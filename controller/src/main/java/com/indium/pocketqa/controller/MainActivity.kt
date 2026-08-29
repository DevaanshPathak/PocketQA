package com.indium.pocketqa.controller

import android.app.Activity
import android.content.pm.PackageManager
import android.content.Intent
import android.content.ClipData
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.ArrayAdapter

class MainActivity : Activity() {
    private lateinit var content: LinearLayout
    private var unsubscribe: (() -> Unit)? = null
    private lateinit var modelRuntime: LiteRtModelRuntime
    private var modelStatus = "Model smoke test not run"
    private var selectedTarget: TestTarget? = null
    private var selectedFinding: BugFinding? = null

    private data class TestTarget(val label: String, val packageName: String) {
        override fun toString(): String = label
    }

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
        when {
            snapshot.status == RunStatus.RUNNING -> renderRun(snapshot)
            selectedFinding != null -> renderDiagnosis(selectedFinding!!)
            snapshot.status == RunStatus.COMPLETE && snapshot.findings.isNotEmpty() -> renderIssueReport(snapshot)
            else -> renderGoalPicker(snapshot)
        }
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
        val targets = compatibleTargets()
        if (targets.isEmpty()) {
            content.addView(TextView(this).apply {
                text = "Buggy App not installed. Install the companion app, then reopen PocketQA."
                setTextColor(Color.rgb(160, 30, 30))
                setPadding(0, 12, 0, 8)
            })
            return
        }
        content.addView(TextView(this).apply {
            text = "Target app"
            textSize = 16f
            setPadding(0, 20, 0, 6)
        })
        val targetPicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, targets)
            setSelection(targets.indexOfFirst { it.packageName == selectedTarget?.packageName }.coerceAtLeast(0))
            setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    selectedTarget = targets[position]
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            })
        }
        content.addView(targetPicker)
        val run = Button(this).apply {
            text = "Run full app test"
            setOnClickListener {
                val target = selectedTarget
                if (target == null) {
                    PocketQaSessionStore.fail("Select the Buggy App to test first.")
                } else if (!PocketQaAccessibilityService.startTestRun(TestGoal.FULL_SCAN, target.packageName)) {
                    PocketQaSessionStore.fail("Enable PocketQA Semantics Reader first.")
                }
            }
        }
        content.addView(TextView(this).apply {
            text = "PocketQA will observe the selected app, perform bounded visible actions, and report any detected issue."
            setPadding(0, 16, 0, 8)
        })
        content.addView(run)
        snapshot.error?.let { message ->
            content.addView(TextView(this).apply {
                text = message
                setTextColor(Color.rgb(160, 30, 30))
                setPadding(0, 24, 0, 0)
            })
        }
    }

    private fun compatibleTargets(): List<TestTarget> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != packageName }
            .filter { it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 }
            .distinctBy { it.packageName }
            .map { appInfo ->
                val label = packageManager.getApplicationLabel(appInfo).toString().ifBlank { appInfo.packageName }
                val suffix = if (appInfo.packageName == PocketQaAccessibilityService.DEFAULT_TARGET_PACKAGE) " (Buggy App)" else ""
                TestTarget(label + suffix, appInfo.packageName)
            }
            .sortedBy { it.label.lowercase() }
            .toList()
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

    private fun renderIssueReport(snapshot: SessionSnapshot) {
        content.addView(TextView(this).apply {
            text = "Run complete: ${snapshot.findings.size} issue(s) found"
            textSize = 18f
            setPadding(0, 28, 0, 12)
        })
        snapshot.findings.forEach { finding ->
            content.addView(TextView(this).apply {
                text = "${finding.title}\n${finding.evidence}"
                setPadding(0, 14, 0, 4)
            })
            content.addView(Button(this).apply {
                text = "Diagnose locally"
                setOnClickListener {
                    selectedFinding = finding
                    render(PocketQaSessionStore.snapshot())
                }
            })
        }
        content.addView(Button(this).apply {
            text = "Start another run"
            setOnClickListener {
                PocketQaSessionStore.stop()
                render(PocketQaSessionStore.snapshot())
            }
        })
    }

    private fun renderDiagnosis(finding: BugFinding) {
        val diagnosis = KnownBugCatalog.diagnose(finding)
        val source = LocalSourceLookup(this).read(diagnosis.sourceKey)
            ?.lineSequence()?.take(36)?.joinToString("\n")
            ?: "Local source was not found."
        content.addView(TextView(this).apply {
            text = "Local diagnosis\n${finding.title}"
            textSize = 18f
            setPadding(0, 28, 0, 8)
        })
        content.addView(TextView(this).apply {
            text = "Cause\n${diagnosis.cause}\n\nReproduce\n${diagnosis.reproduction}\n\nSource: ${diagnosis.sourceKey}\n$source\n\nSuggested patch\n${diagnosis.diff}"
            setTextColor(Color.rgb(30, 30, 40))
        })
        content.addView(Button(this).apply {
            text = "Save and share patch"
            setOnClickListener {
                val patch = PatchWriter.save(this@MainActivity, diagnosis)
                val uri = PatchWriter.uri(this@MainActivity, patch)
                startActivity(Intent(Intent.ACTION_SEND).apply {
                    type = "text/x-diff"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("PocketQA patch", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            }
        })
        content.addView(Button(this).apply {
            text = "Back to issues"
            setOnClickListener {
                selectedFinding = null
                render(PocketQaSessionStore.snapshot())
            }
        })
    }

    override fun onDestroy() {
        unsubscribe?.invoke()
        modelRuntime.close()
        super.onDestroy()
    }
}
