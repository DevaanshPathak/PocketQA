package com.indium.pocketqa.controller

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var content: LinearLayout
    private var unsubscribe: (() -> Unit)? = null
    private lateinit var modelRuntime: LiteRtModelRuntime
    private var modelStatus = "Model smoke test not run"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "PocketQA"
        modelRuntime = LiteRtModelRuntime(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        setContentView(ScrollView(this).apply { addView(content) })
        unsubscribe = PocketQaSessionStore.subscribe { snapshot ->
            runOnUiThread { render(snapshot) }
        }
    }

    private fun render(snapshot: SessionSnapshot) {
        content.removeAllViews()
        val heading = TextView(this).apply {
            text = "PocketQA\nOffline device QA"
            textSize = 24f
            setTextColor(Color.rgb(25, 25, 35))
        }
        val enable = Button(this).apply {
            text = "Enable Accessibility Service"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        content.addView(heading)
        content.addView(enable)
        content.addView(Button(this).apply {
            text = "Run offline model smoke test"
            setOnClickListener { runModelSmokeTest() }
        })
        content.addView(Button(this).apply {
            text = "Run GPU thermal benchmark (3 passes)"
            setOnClickListener { runThermalBenchmark() }
        })
        content.addView(TextView(this).apply {
            text = modelStatus
            setPadding(0, 12, 0, 8)
            setTextColor(Color.rgb(50, 50, 65))
        })
        if (snapshot.status == RunStatus.RUNNING) renderRun(snapshot) else renderGoalPicker(snapshot)
    }

    private fun runModelSmokeTest() {
        modelStatus = "Loading Gemma 4 E4B locally..."
        render(PocketQaSessionStore.snapshot())
        modelRuntime.initialize { load ->
            runOnUiThread {
                when (load) {
                    is ModelLoadResult.Ready -> {
                        modelStatus = "Model ready in ${load.initializationMs}ms. Generating offline response..."
                        render(PocketQaSessionStore.snapshot())
                        modelRuntime.runSmokePrompt(
                            "Reply with exactly one sentence: PocketQA is running offline on this phone.",
                        ) { response ->
                            runOnUiThread {
                                modelStatus = when (response) {
                                    is ModelPromptResult.Success ->
                                        "Offline response in ${response.elapsedMs}ms: ${response.text}"
                                    is ModelPromptResult.Failed -> "Model response failed: ${response.message}"
                                }
                                render(PocketQaSessionStore.snapshot())
                            }
                        }
                    }
                    is ModelLoadResult.Missing -> {
                        modelStatus = "Model missing: ${load.expectedPath}"
                        render(PocketQaSessionStore.snapshot())
                    }
                    is ModelLoadResult.Failed -> {
                        modelStatus = "Model initialization failed: ${load.message}"
                        render(PocketQaSessionStore.snapshot())
                    }
                }
            }
        }
    }

    private fun runThermalBenchmark() {
        modelStatus = "Running three LiteRT GPU benchmark passes..."
        render(PocketQaSessionStore.snapshot())
        modelRuntime.runThermalBenchmark { benchmark ->
            runOnUiThread {
                modelStatus = when (benchmark) {
                    is ModelBenchmarkResult.Success -> {
                        val averageTtft = benchmark.passes.map { it.timeToFirstTokenMs }.average().toLong()
                        val averageTokPerSec = benchmark.passes.map { it.decodeTokensPerSecond }.average()
                        "GPU thermal benchmark complete: ${benchmark.passes.size} passes in " +
                            "${benchmark.elapsedMs}ms; avg TTFT ${averageTtft}ms; avg decode " +
                            "${"%.1f".format(averageTokPerSec)} tok/s. GPU backend; NPU is not claimed."
                    }
                    is ModelBenchmarkResult.Failed -> "Benchmark failed: ${benchmark.message}"
                }
                render(PocketQaSessionStore.snapshot())
            }
        }
    }

    private fun renderGoalPicker(snapshot: SessionSnapshot) {
        content.addView(TextView(this).apply {
            text = if (snapshot.status == RunStatus.COMPLETE) "Run complete - choose another goal" else "Choose a test goal"
            textSize = 18f
            setPadding(0, 32, 0, 16)
        })
        val goals = RadioGroup(this)
        TestGoal.entries.forEachIndexed { index, goal ->
            goals.addView(RadioButton(this).apply {
                id = 100 + index
                text = "${goal.title}\n${goal.description}"
                contentDescription = goal.description
                if (goal == (snapshot.goal ?: TestGoal.FULL_SCAN)) isChecked = true
            })
        }
        val run = Button(this).apply {
            text = "Start exploration"
            setOnClickListener {
                val selected = goals.checkedRadioButtonId - 100
                val goal = TestGoal.entries.getOrElse(selected) { TestGoal.FULL_SCAN }
                if (!PocketQaAccessibilityService.startTestRun(goal)) {
                    PocketQaSessionStore.fail("Enable PocketQA Semantics Reader first.")
                }
            }
        }
        content.addView(goals)
        content.addView(run)
        snapshot.error?.let { message ->
            content.addView(TextView(this).apply {
                text = message
                setTextColor(Color.rgb(160, 30, 30))
                setPadding(0, 24, 0, 0)
            })
        }
    }

    private fun renderRun(snapshot: SessionSnapshot) {
        content.addView(TextView(this).apply {
            text = "Exploring: ${snapshot.goal?.title ?: "Buggy App"}"
            textSize = 18f
            setPadding(0, 32, 0, 8)
        })
        content.addView(Button(this).apply {
            text = "Stop exploration"
            setOnClickListener { PocketQaAccessibilityService.stopTestRun() }
        })
        content.addView(TextView(this).apply {
            text = "Live action log"
            textSize = 16f
            setPadding(0, 24, 0, 8)
        })
        content.addView(TextView(this).apply {
            text = snapshot.actions.takeLast(20).joinToString("\n") { "${it.kind.uppercase()}: ${it.detail}" }.ifBlank { "Waiting for Buggy App..." }
            setTextColor(Color.rgb(30, 30, 40))
        })
        if (snapshot.findings.isNotEmpty()) {
            content.addView(TextView(this).apply {
                text = "\nFindings\n" + snapshot.findings.joinToString("\n") { "- ${it.title}" }
                setTextColor(Color.rgb(150, 45, 25))
            })
        }
    }

    override fun onDestroy() {
        unsubscribe?.invoke()
        modelRuntime.close()
        super.onDestroy()
    }
}
