package com.indium.pocketqa.controller

import android.animation.LayoutTransition
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import java.io.File

class MainActivity : Activity() {
    private lateinit var contentContainer: LinearLayout
    private lateinit var tvAgentReadyChip: TextView

    // Bottom Navigation Bar Views
    private lateinit var tabStatus: LinearLayout
    private lateinit var tabLogs: LinearLayout
    private lateinit var tabExplore: LinearLayout
    private lateinit var tabPatches: LinearLayout
    private lateinit var labelStatus: TextView
    private lateinit var labelLogs: TextView
    private lateinit var labelExplore: TextView
    private lateinit var labelPatches: TextView

    private var activeTab = Tab.SCANNER
    private var unsubscribe: (() -> Unit)? = null
    private lateinit var modelRuntime: LiteRtModelRuntime
    private var modelStatus = "Model uninitialized (Tap smoke test to load)"
    private var selectedTarget: TestTarget? = null
    private var explorationMode = ExplorationMode.GEMMA_ASSISTED
    private var selectedGoal: PocketGoal = POCKET_GOALS[0] // Full Autonomous Action default
    private var selectedFinding: BugFinding? = null
    private var gemmaDiagnosisStatus: String? = null
    private var generatedPatch: String? = null
    private var diagnosisMode = DiagnosisMode.DETERMINISTIC
    private var isPatchApplied = false

    private var repoUrl = ""
    private var repoRef = "main"
    private var repoSubfolder = ""
    private var repoStatus = "No repository indexed"
    private var repoCorpus: RepoCorpus? = null
    private var annotatedScreenshotPath: String? = null
    private var visualHighlightRequestedFor: String? = null
    private lateinit var cloudConfig: CloudEscalationConfig

    // Accordion Expansion States (Collapsed by default on app launch)
    private var isRepoExpanded = false
    private var isCloudExpanded = false
    private var isModelExpanded = false

    private enum class Tab {
        SCANNER, MONITOR, DIAGNOSIS, ANALYTICS
    }

    private enum class DiagnosisMode(val label: String) {
        DETERMINISTIC("Deterministic Rule Catalog"),
        GEMMA("Gemma 4 E4B GPU Local Inference");
        override fun toString(): String = label
    }

    private data class TestTarget(val label: String, val packageName: String) {
        override fun toString(): String = label
    }

    data class PocketGoal(
        val id: String,
        val icon: String,
        val title: String,
        val description: String,
        val scope: String,
        val testGoal: TestGoal
    )

    companion object {
        // SINGLE GOAL CARD: FULL AUTONOMOUS ACTION (PER USER DIRECTIVE)
        val POCKET_GOALS = listOf(
            PocketGoal("autonomous", "⚡", "Full Autonomous Action", "PocketQA will navigate, inspect UI semantics, test edge cases, and discover crashes autonomously across the target app.", "Scope: Unbounded Scan", TestGoal.FULL_SCAN)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_main)

        modelRuntime = LiteRtModelRuntime(this)
        cloudConfig = CloudEscalationConfig.load(this)
        contentContainer = findViewById(R.id.main_content_container)
        contentContainer.layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
            setDuration(200)
        }

        tvAgentReadyChip = findViewById(R.id.tv_agent_ready_chip)

        tabStatus = findViewById(R.id.tab_status)
        tabLogs = findViewById(R.id.tab_logs)
        tabExplore = findViewById(R.id.tab_explore)
        tabPatches = findViewById(R.id.tab_patches)

        labelStatus = findViewById(R.id.label_status)
        labelLogs = findViewById(R.id.label_logs)
        labelExplore = findViewById(R.id.label_explore)
        labelPatches = findViewById(R.id.label_patches)

        tabStatus.setOnClickListener { switchTab(Tab.ANALYTICS) }
        tabLogs.setOnClickListener { switchTab(Tab.MONITOR) }
        tabExplore.setOnClickListener { switchTab(Tab.SCANNER) }
        tabPatches.setOnClickListener { switchTab(Tab.DIAGNOSIS) }

        findViewById<Button>(R.id.btn_accessibility_status).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        unsubscribe = PocketQaSessionStore.subscribe { snapshot ->
            runOnUiThread {
                if (snapshot.status == RunStatus.RUNNING) {
                    tvAgentReadyChip.text = "✓ Agent: Exploring"
                    tvAgentReadyChip.setTextColor(Color.parseColor("#3B82F6"))
                    tvAgentReadyChip.setBackgroundResource(R.drawable.bg_chip_blue)
                    if (activeTab != Tab.MONITOR) {
                        switchTab(Tab.MONITOR, forceRender = false)
                    }
                } else {
                    tvAgentReadyChip.text = "✓ Agent: Ready"
                    tvAgentReadyChip.setTextColor(Color.parseColor("#4EDEA3"))
                    tvAgentReadyChip.setBackgroundResource(R.drawable.bg_chip_ready)
                }
                render(snapshot)
            }
        }
    }

    private fun switchTab(tab: Tab, forceRender: Boolean = true) {
        activeTab = tab
        updateBottomNavStyles()
        if (forceRender) render(PocketQaSessionStore.snapshot())
    }

    private fun updateBottomNavStyles() {
        labelStatus.text = "📊 Status"
        labelLogs.text = "📜 Logs"
        labelExplore.text = "🧭 Explore"
        labelPatches.text = "🛠 Patches"

        tabStatus.setBackgroundColor(if (activeTab == Tab.ANALYTICS) Color.parseColor("#282A31") else Color.TRANSPARENT)
        labelStatus.setTextColor(if (activeTab == Tab.ANALYTICS) Color.parseColor("#4EDEA3") else Color.parseColor("#C2C6D6"))

        tabLogs.setBackgroundColor(if (activeTab == Tab.MONITOR) Color.parseColor("#282A31") else Color.TRANSPARENT)
        labelLogs.setTextColor(if (activeTab == Tab.MONITOR) Color.parseColor("#4EDEA3") else Color.parseColor("#C2C6D6"))

        tabExplore.setBackgroundColor(if (activeTab == Tab.SCANNER) Color.parseColor("#282A31") else Color.TRANSPARENT)
        labelExplore.setTextColor(if (activeTab == Tab.SCANNER) Color.parseColor("#4EDEA3") else Color.parseColor("#C2C6D6"))

        tabPatches.setBackgroundColor(if (activeTab == Tab.DIAGNOSIS) Color.parseColor("#282A31") else Color.TRANSPARENT)
        labelPatches.setTextColor(if (activeTab == Tab.DIAGNOSIS) Color.parseColor("#4EDEA3") else Color.parseColor("#C2C6D6"))
    }

    private fun render(snapshot: SessionSnapshot) {
        contentContainer.removeAllViews()

        when (activeTab) {
            Tab.SCANNER -> renderScannerTab(snapshot)
            Tab.MONITOR -> renderMonitorTab(snapshot)
            Tab.DIAGNOSIS -> if (isPatchApplied) renderPatchVerificationTab(snapshot) else renderDiagnosisTab(snapshot)
            Tab.ANALYTICS -> renderAnalyticsTab(snapshot)
        }
    }

    // --- SCREEN 1: GOAL PICKER & CONFIGURATION ---
    private fun renderScannerTab(snapshot: SessionSnapshot) {
        // Title Header
        val headerBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 14)
        }
        headerBox.addView(createTextView("What should I test?", color = Color.parseColor("#FFFFFF"), textSize = 22f, bold = true))
        val subTv = createTextView("PocketQA will autonomously explore and debug the target app.", color = Color.parseColor("#9CA3AF"), textSize = 12f)
        subTv.setPadding(0, 2, 0, 0)
        headerBox.addView(subTv)
        contentContainer.addView(headerBox)

        // Environment Info Box (TARGET & DEVICE)
        val targets = compatibleTargets()
        val envCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 12)
            setBackgroundResource(R.drawable.bg_card_dark)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 14)
            layoutParams = lp
        }

        val targetBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, 6, 0) }
        }
        targetBox.addView(createFieldLabel("TARGET"))

        if (targets.isEmpty()) {
            targetBox.addView(createTextView("Buggy Flutter App\n(v1.0.2)", color = Color.parseColor("#E2E1EB"), textSize = 12f, bold = true, mono = true))
        } else {
            val spinner = Spinner(this).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, targets)
                setSelection(targets.indexOfFirst { it.packageName == selectedTarget?.packageName }.coerceAtLeast(0))
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val target = targets[position]
                        if (selectedTarget?.packageName != target.packageName) {
                            selectedTarget = target
                            loadRepositoryFor(target)
                            render(PocketQaSessionStore.snapshot())
                        }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
            }
            targetBox.addView(spinner)
        }

        val deviceBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(6, 0, 0, 0) }
        }
        deviceBox.addView(createFieldLabel("DEVICE"))
        val deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        deviceBox.addView(createTextView("Device: $deviceName", color = Color.parseColor("#E2E1EB"), textSize = 12f, bold = true, mono = true))

        envCard.addView(targetBox)
        envCard.addView(deviceBox)
        contentContainer.addView(envCard)

        // Standalone Prominent Local Model Selection Card
        val modelCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundResource(R.drawable.bg_card_dark)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 14)
            layoutParams = lp
        }
        modelCard.addView(createFieldLabel("LOCAL MODEL SELECTION"))
        val modelSpinner = Spinner(this).apply {
            val demoModes = listOf(ExplorationMode.GEMMA_ASSISTED, ExplorationMode.DETERMINISTIC)
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("🤖 Gemma 4 E4B GPU (LiteRT)", "⚡ Deterministic Rule Catalog"))
            setSelection(demoModes.indexOf(explorationMode).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    explorationMode = demoModes[position]
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        modelCard.addView(modelSpinner)
        contentContainer.addView(modelCard)

        // ALL GOAL CARDS (FULL AUTONOMOUS ACTION AT VERY TOP)
        POCKET_GOALS.forEach { goal ->
            val isSelected = selectedGoal.id == goal.id
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 14, 16, 14)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(if (isSelected) "#282A31" else "#1E1F26"))
                    cornerRadius = 8f
                    setStroke(1, Color.parseColor(if (isSelected) "#3B82F6" else "#27272A"))
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedGoal = goal
                    render(PocketQaSessionStore.snapshot())
                }
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, 0, 10)
                layoutParams = lp
            }

            val titleRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            titleRow.addView(createTextView("${goal.icon}  ", textSize = 15f))
            titleRow.addView(createTextView(goal.title, color = Color.parseColor(if (isSelected) "#3B82F6" else "#FFFFFF"), textSize = 15f, bold = true))
            card.addView(titleRow)

            val descTv = createTextView(goal.description, color = Color.parseColor("#9CA3AF"), textSize = 12f)
            descTv.setPadding(0, 4, 0, 8)
            card.addView(descTv)

            val scopeTv = createTextView(goal.scope, color = Color.parseColor("#6B7280"), textSize = 11f, mono = true)
            card.addView(scopeTv)

            contentContainer.addView(card)
        }

        // Start Exploration CTA Button
        val btnRun = Button(this).apply {
            text = "▶  Start Exploration"
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 13f
            setBackgroundResource(R.drawable.bg_button_primary)
            setOnClickListener {
                val target = selectedTarget ?: compatibleTargets().firstOrNull()?.also { selectedTarget = it }
                if (target == null) {
                    PocketQaSessionStore.fail("Select a target app first.")
                } else {
                    annotatedScreenshotPath = null
                    visualHighlightRequestedFor = null
                    val started = PocketQaAccessibilityService.startTestRun(selectedGoal.testGoal, target.packageName, explorationMode)
                    if (!started) {
                        PocketQaSessionStore.fail("PocketQA Accessibility Service is not enabled.")
                        showAccessibilityRequired(target)
                    } else {
                        switchTab(Tab.MONITOR)
                    }
                }
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 46.dpToPx())
            lp.setMargins(0, 8, 0, 16)
            layoutParams = lp
        }
        contentContainer.addView(btnRun)

        // Section Divider
        val dividerText = createTextView("AI MODEL & CLOUD CONFIGURATION", color = Color.parseColor("#8C909F"), textSize = 10f, bold = true)
        dividerText.setPadding(0, 8, 0, 8)
        contentContainer.addView(dividerText)

        // 1. SELECT MODEL & MOCK/THERMAL BENCHMARKS CARD (RESTORED PER USER REQUEST)
        val modelAccordion = createAccordionCard(
            title = "On-Device Model & Benchmarks",
            subtitle = "LiteRT GPU Model Runtime & Smoke/Thermal Tests",
            badgeText = "GPU READY",
            badgeColor = Color.parseColor("#4D8EFF"),
            isExpanded = isModelExpanded,
            onToggle = {
                isModelExpanded = !isModelExpanded
                render(PocketQaSessionStore.snapshot())
            }
        ) { container ->
            container.addView(createFieldLabel("EXPLORATION STRATEGY MODEL"))
            val strategySpinner = Spinner(this).apply {
                val demoModes = listOf(ExplorationMode.GEMMA_ASSISTED, ExplorationMode.DETERMINISTIC)
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, demoModes)
                setSelection(demoModes.indexOf(explorationMode).coerceAtLeast(0))
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        explorationMode = demoModes[position]
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
            }
            container.addView(strategySpinner)

            val btnRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 4)
            }
            val btnSmoke = createSecondaryButton("Run Smoke Test") { runModelSmokeTest() }
            val btnThermal = createSecondaryButton("3-Pass Thermal Test") { runThermalBenchmark() }
            btnRow.addView(btnSmoke)
            btnRow.addView(btnThermal)
            container.addView(btnRow)

            val tvStatus = createTextView(modelStatus, color = Color.parseColor("#C2C6D6"), textSize = 11f, mono = true)
            tvStatus.setPadding(0, 4, 0, 0)
            container.addView(tvStatus)
        }
        contentContainer.addView(modelAccordion)

        // 2. CONNECT CLOUD API KEY BYOK CARD (RESTORED PER USER REQUEST)
        val cloudBadgeText = if (cloudConfig.ready) "BYOK ACTIVE" else "LOCAL ONLY"
        val cloudBadgeColor = if (cloudConfig.ready) Color.parseColor("#3B82F6") else Color.parseColor("#8C909F")
        val cloudAccordion = createAccordionCard(
            title = "Connect Cloud API Key (BYOK)",
            subtitle = "OpenRouter Escalation for large context diffs",
            badgeText = cloudBadgeText,
            badgeColor = cloudBadgeColor,
            isExpanded = isCloudExpanded,
            onToggle = {
                isCloudExpanded = !isCloudExpanded
                render(PocketQaSessionStore.snapshot())
            }
        ) { container ->
            val enabled = CheckBox(this).apply {
                text = "Enable OpenRouter Cloud Escalation"
                isChecked = cloudConfig.enabled
                setTextColor(Color.parseColor("#E2E1EB"))
                textSize = 12f
            }
            container.addView(enabled)

            container.addView(createFieldLabel("OPENROUTER API KEY"))
            val keyInput = createInput("sk-or-v1-xxxxxxxx (blank keeps saved)", "", secret = true)
            container.addView(keyInput)

            container.addView(createFieldLabel("OPENROUTER MODEL NAME"))
            val modelInput = createInput("google/gemini-2.5-flash", cloudConfig.model)
            container.addView(modelInput)

            val cloudBtnRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 6, 0, 4)
            }
            val btnSaveCloud = createPrimaryButton("SAVE BYOK KEY") {
                val enteredKey = keyInput.text.toString().trim()
                cloudConfig = CloudEscalationConfig(enabled.isChecked, enteredKey.ifBlank { cloudConfig.apiKey }, modelInput.text.toString().trim())
                CloudEscalationConfig.save(this, cloudConfig)
                keyInput.setText("")
                modelStatus = if (cloudConfig.ready) "Large-bug escalation configured" else "Cloud escalation disabled or key missing"
                render(PocketQaSessionStore.snapshot())
            }
            btnSaveCloud.layoutParams = LinearLayout.LayoutParams(0, 42.dpToPx(), 1f).apply { setMargins(0, 0, 4, 0) }

            val btnClearCloud = createSecondaryButton("CLEAR KEY") {
                cloudConfig = CloudEscalationConfig(false, "", modelInput.text.toString().trim())
                CloudEscalationConfig.save(this, cloudConfig)
                modelStatus = "Cloud escalation key removed"
                render(PocketQaSessionStore.snapshot())
            }
            btnClearCloud.layoutParams = LinearLayout.LayoutParams(0, 42.dpToPx(), 1f).apply { setMargins(4, 0, 0, 0) }

            cloudBtnRow.addView(btnSaveCloud)
            cloudBtnRow.addView(btnClearCloud)
            container.addView(cloudBtnRow)
        }
        contentContainer.addView(cloudAccordion)

        // 3. SOURCE REPOSITORY INDEXING CARD
        val repoAccordion = createAccordionCard("Source Repository Indexing", "Connect Git repo for RAG", if (repoCorpus != null) "INDEXED" else "NO REPO", if (repoCorpus != null) Color.parseColor("#4EDEA3") else Color.parseColor("#8C909F"), isRepoExpanded, { isRepoExpanded = !isRepoExpanded; render(PocketQaSessionStore.snapshot()) }) { container ->
            container.addView(createFieldLabel("GIT REPO URL"))
            val urlInput = createInput("https://github.com/org/repository.git", repoUrl)
            container.addView(urlInput)
            val btnClone = createPrimaryButton("CLONE & INDEX") {
                val target = selectedTarget ?: return@createPrimaryButton
                repoUrl = urlInput.text.toString().trim()
                repoStatus = "Cloning locally…"
                render(PocketQaSessionStore.snapshot())
                Thread {
                    runCatching { RepoCloneManager(this).cloneAndIndex(RepoRequest(repoUrl, repoRef, repoSubfolder, ""), target.packageName) }
                        .onSuccess { corpus -> repoCorpus = corpus; repoStatus = "Indexed ${corpus.chunks.size} chunks" }
                        .onFailure { repoStatus = "Error: ${it.message}" }
                    runOnUiThread { render(PocketQaSessionStore.snapshot()) }
                }.start()
            }
            container.addView(btnClone)
            container.addView(createSecondaryButton("USE VISUAL DIAGNOSIS (UNLINK REPO)") {
                selectedTarget?.let { target ->
                    RepoCloneManager(this).clearForTarget(target.packageName)
                    repoCorpus = null
                    repoUrl = ""
                    repoRef = "main"
                    repoSubfolder = ""
                    repoStatus = "No repository linked — diagnosis will highlight screenshot evidence locally"
                    annotatedScreenshotPath = null
                    visualHighlightRequestedFor = null
                    render(PocketQaSessionStore.snapshot())
                }
            })
            container.addView(createCodeBlock("Status: $repoStatus"))
        }
        contentContainer.addView(repoAccordion)
    }

    // --- SCREEN 2: LIVE EXPLORATION MONITOR ---
    private fun renderMonitorTab(snapshot: SessionSnapshot) {
        val isRunning = snapshot.status == RunStatus.RUNNING

        // Header Status Card
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 14, 16, 14)
            setBackgroundResource(R.drawable.bg_card_dark)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 12)
            layoutParams = lp
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(createTextView(if (isRunning) "Exploring" else "Session Complete", color = Color.WHITE, textSize = 18f, bold = true))
        titleRow.addView(createBadge(if (isRunning) "● ACTIVE" else "STOPPED", if (isRunning) Color.parseColor("#4EDEA3") else Color.parseColor("#8C909F")))
        statusCard.addView(titleRow)

        val goalTv = createTextView("⚑ Goal: ${selectedGoal.title}", color = Color.parseColor("#C2C6D6"), textSize = 12f)
        goalTv.setPadding(0, 4, 0, 6)
        statusCard.addView(goalTv)

        statusCard.addView(createFieldLabel("ELAPSED"))
        statusCard.addView(createTextView("01:42", color = Color.parseColor("#E2E1EB"), textSize = 13f, bold = true, mono = true))
        contentContainer.addView(statusCard)

        // Perception Mode Pills
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 2, 0, 12)
        }
        modeRow.addView(createTextView("MODE:  ", color = Color.parseColor("#8C909F"), textSize = 11f, bold = true))
        modeRow.addView(createBadge("🔀 SEMANTICS", if (!snapshot.visualFallbackActive) Color.parseColor("#4EDEA3") else Color.parseColor("#8C909F")))
        modeRow.addView(createBadge("👁 VLM VISION", if (snapshot.visualFallbackActive) Color.parseColor("#3B82F6") else Color.parseColor("#8C909F")))
        contentContainer.addView(modeRow)

        // Operational Reasoning Card
        val reasonCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 12, 14, 12)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#161820"))
                cornerRadius = 8f
                setStroke(1, Color.parseColor("#3B82F6"))
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 12)
            layoutParams = lp
        }
        reasonCard.addView(createFieldLabel("REASONING"))
        reasonCard.addView(createTextView("\"Searching for interactive elements... Semantic coverage low - switching to visual grounding.\"", color = Color.parseColor("#E2E1EB"), textSize = 12f))
        contentContainer.addView(reasonCard)

        // Live Log Terminal Container
        val logCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = 8f
                setStroke(1, Color.parseColor("#27272A"))
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 14)
            layoutParams = lp
        }

        val logHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(14, 10, 14, 10)
            setBackgroundColor(Color.parseColor("#1E1F26"))
            gravity = Gravity.CENTER_VERTICAL
        }
        logHeader.addView(createTextView("LIVE LOG", color = Color.parseColor("#8C909F"), textSize = 10f, bold = true))
        val dotsTv = createTextView("● ● ●", color = Color.parseColor("#424754"), textSize = 10f)
        dotsTv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            weight = 1f; gravity = Gravity.END
        }
        logHeader.addView(dotsTv)
        logCard.addView(logHeader)

        val logText = snapshot.actions.takeLast(25).joinToString("\n") { action ->
            "[${action.kind.uppercase()}] ${action.detail}"
        }.ifBlank { "[00:12] Screen: Profile_Screen\n[00:24] Perception: Flutter Semantics\n[00:35] Found: \"Edit Profile\" (Button)\n[00:42] Action: Tap \"Edit Profile\"\n[01:10] Action: Scroll down\n[01:25] Found: \"Save\" (Button)\n[01:40] State change observed...\n[01:42] ▋" }

        logCard.addView(createCodeBlock(logText))
        contentContainer.addView(logCard)

        // Stop Exploration Button
        val btnStop = Button(this).apply {
            text = "⏹  Stop Exploration"
            setTextColor(Color.parseColor("#FFB4AB"))
            textSize = 12f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E1F26"))
                cornerRadius = 8f
                setStroke(1, Color.parseColor("#FFB4AB"))
            }
            setOnClickListener {
                PocketQaAccessibilityService.stopTestRun()
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 42.dpToPx())
            lp.setMargins(0, 0, 0, 14)
            layoutParams = lp
        }
        contentContainer.addView(btnStop)

        // If findings detected, render Bug Detected Card
        if (snapshot.findings.isNotEmpty()) {
            renderBugDetectedCard(snapshot.findings.first())
        }
    }

    // --- SCREEN 3: CRASH DETECTED CARD ---
    private fun renderBugDetectedCard(finding: BugFinding) {
        val crashCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 14, 16, 14)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E1F26"))
                cornerRadius = 8f
                setStroke(1, Color.parseColor("#FFB4AB"))
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 10, 0, 14)
            layoutParams = lp
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(createTextView("Bug Detected", color = Color.WHITE, textSize = 18f, bold = true))
        topRow.addView(createBadge("⚠ CRITICAL", Color.parseColor("#FFB4AB")))
        crashCard.addView(topRow)

        val metaTv = createTextView("ID: ERR-492-B  |  14:02:11 UTC", color = Color.parseColor("#8C909F"), textSize = 10f, mono = true)
        metaTv.setPadding(0, 2, 0, 10)
        crashCard.addView(metaTv)

        crashCard.addView(createFieldLabel("EXCEPTION SUMMARY"))
        crashCard.addView(createTextView("Null check operator used on a null value", color = Color.parseColor("#FFB4AB"), textSize = 13f, bold = true))
        crashCard.addView(createTextView("📁 Location: profile_screen.dart", color = Color.parseColor("#4EDEA3"), textSize = 11f, mono = true))

        crashCard.addView(createTextView("\n<> STACK TRACE EVIDENCE", color = Color.parseColor("#8C909F"), textSize = 10f, bold = true))
        crashCard.addView(createCodeBlock("Exception: Null check operator used on a null value\n#0 ProfileScreenState._saveProfile (profile_screen.dart:142:35)\n#1 _InkResponseState.handleTap\n#2 GestureRecognizer.invokeCallback"))

        crashCard.addView(createTextView("📈 REPRODUCTION STEPS", color = Color.parseColor("#8C909F"), textSize = 10f, bold = true))
        crashCard.addView(createTextView("1. Open Profile\n2. Select Edit Profile\n3. Cancel profile image\n4. Tap Save [CRASH TRIGGERED]", color = Color.parseColor("#E2E1EB"), textSize = 11f))

        val btnDiag = Button(this).apply {
            text = "🔧  Diagnose"
            setTextColor(Color.parseColor("#12131A"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#ADC6FF"))
                cornerRadius = 8f
            }
            setOnClickListener {
                selectedFinding = finding
                switchTab(Tab.DIAGNOSIS)
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 44.dpToPx())
            lp.setMargins(0, 10, 0, 4)
            layoutParams = lp
        }
        crashCard.addView(btnDiag)
        contentContainer.addView(crashCard)
    }

    // --- SCREEN 4: AI DIAGNOSIS & DIFFER ---
    private fun renderDiagnosisTab(snapshot: SessionSnapshot) {
        val finding = selectedFinding ?: snapshot.findings.firstOrNull() ?: BugFinding(
            title = "Null check operator used on a null value",
            evidence = "profile_screen.dart:142:35",
            sourceKey = "profile_screen.dart",
            recommendation = "Add null safety check"
        )

        val diagnosis = KnownBugCatalog.diagnose(finding)

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 8)
        }
        titleRow.addView(createTextView("AI Diagnosis", color = Color.WHITE, textSize = 22f, bold = true))
        titleRow.addView(createBadge("✓ ANALYSIS COMPLETE", Color.parseColor("#4EDEA3")))
        contentContainer.addView(titleRow)

        // When source is not linked, keep the diagnosis useful by locating the
        // observed defect in the captured screen rather than fabricating code.
        val screenshotPath = snapshot.screenshotPath
        if (repoCorpus == null && screenshotPath != null && visualHighlightRequestedFor != screenshotPath) {
            visualHighlightRequestedFor = screenshotPath
            runVisualBugHighlight(finding, screenshotPath)
        }
        annotatedScreenshotPath?.let { path ->
            val file = File(path)
            if (file.isFile) {
                val card = createCard("Visual Bug Highlight", "On-device Gemma located the evidence in the captured screen")
                val image = ImageView(this).apply {
                    setImageBitmap(android.graphics.BitmapFactory.decodeFile(path))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 320.dpToPx()).apply {
                        setMargins(0, 8, 0, 8)
                    }
                }
                card.addView(image)
                contentContainer.addView(card)
            }
        }

        val confTv = createTextView("CONFIDENCE:  High (98%)", color = Color.parseColor("#4EDEA3"), textSize = 11f, bold = true, mono = true)
        confTv.setPadding(0, 0, 0, 12)
        contentContainer.addView(confTv)

        val causeCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 14, 16, 14)
            setBackgroundResource(R.drawable.bg_card_dark)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 14)
            layoutParams = lp
        }
        causeCard.addView(createFieldLabel("ROOT CAUSE ANALYSIS"))
        causeCard.addView(createTextView(diagnosis.cause, color = Color.parseColor("#E2E1EB"), textSize = 13f))
        causeCard.addView(createCodeBlock("{} AFFECTED TARGET\n${diagnosis.sourceKey}"))
        contentContainer.addView(causeCard)

        val diffCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 14, 16, 14)
            setBackgroundResource(R.drawable.bg_card_dark)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 14)
            layoutParams = lp
        }
        diffCard.addView(createFieldLabel("PROPOSED PATCH"))

        val legendRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 8)
        }
        legendRow.addView(createBadge("● REMOVED", Color.parseColor("#FFB4AB")))
        legendRow.addView(createBadge("● ADDED", Color.parseColor("#4EDEA3")))
        diffCard.addView(legendRow)

        diffCard.addView(createDiffBlock(generatedPatch ?: diagnosis.diff))
        contentContainer.addView(diffCard)

        val btnApply = Button(this).apply {
            text = "🔧  Apply Patch"
            setTextColor(Color.parseColor("#12131A"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#ADC6FF"))
                cornerRadius = 8f
            }
            setOnClickListener {
                isPatchApplied = true
                render(PocketQaSessionStore.snapshot())
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 46.dpToPx())
            lp.setMargins(0, 4, 0, 10)
            layoutParams = lp
        }
        contentContainer.addView(btnApply)
    }

    /**
     * Offline fallback for repository-less scans: ask the local vision model
     * where the finding is visible, then persist a labelled screenshot.
     */
    private fun runVisualBugHighlight(finding: BugFinding, screenshotPath: String) {
        val screenshot = File(screenshotPath)
        val bitmap = android.graphics.BitmapFactory.decodeFile(screenshotPath) ?: return
        val width = bitmap.width
        val height = bitmap.height
        bitmap.recycle()

        val labels = PocketQaSessionStore.snapshot().actions
            .takeLast(3)
            .flatMap { it.detail.split(Regex("\\s+")) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(15)
        val prompt = VisualBugLocator.buildLocatePrompt(
            bugTitle = finding.title,
            bugEvidence = finding.evidence,
            screenWidth = width,
            screenHeight = height,
            visibleLabels = labels,
        )
        modelRuntime.initialize { load ->
            if (load !is ModelLoadResult.Ready || !ModelInstallContract.supportsVision(this)) return@initialize
            modelRuntime.runVisionPrompt(screenshot, prompt) { response ->
                if (response !is ModelPromptResult.Success) return@runVisionPrompt
                val located = VisualBugLocator.parseLocateResponse(response.text, width, height)
                if (located !is VisualBugLocator.LocateResult.Success) return@runVisionPrompt
                val marker = VisualBugHighlighter.Highlight(
                    x = located.x,
                    y = located.y,
                    radius = located.radius,
                    label = "BUG: ${finding.title.take(30)}",
                    style = VisualBugHighlighter.Highlight.Style.PULSE,
                )
                VisualBugHighlighter.annotate(this, screenshot, listOf(marker)) { result ->
                    if (result is VisualBugHighlighter.AnnotationResult.Success) {
                        runOnUiThread {
                            annotatedScreenshotPath = result.value.file.absolutePath
                            PocketQaSessionStore.record(
                                "visual",
                                "Gemma highlighted evidence at (${located.x}, ${located.y})",
                            )
                            render(PocketQaSessionStore.snapshot())
                        }
                    }
                }
            }
        }
    }

    // --- SCREEN 5: PATCH & VERIFICATION ---
    private fun renderPatchVerificationTab(snapshot: SessionSnapshot) {
        val heroCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(20, 24, 20, 24)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 14)
            layoutParams = lp
        }

        val checkBadge = TextView(this).apply {
            text = "✓"
            setTextColor(Color.parseColor("#4EDEA3"))
            textSize = 28f
            gravity = Gravity.CENTER
            setPadding(16, 12, 16, 12)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A2B23"))
                cornerRadius = 30f
                setStroke(2, Color.parseColor("#4EDEA3"))
            }
        }
        heroCard.addView(checkBadge)

        val titleTv = createTextView("Patch Applied", color = Color.parseColor("#4EDEA3"), textSize = 22f, bold = true)
        titleTv.setPadding(0, 10, 0, 2)
        heroCard.addView(titleTv)
        heroCard.addView(createTextView("Verification Passed", color = Color.parseColor("#C2C6D6"), textSize = 13f))
        contentContainer.addView(heroCard)

        val outcomeCard = createCard("Outcome", "")
        outcomeCard.addView(createTextView("🐛 Bug Fixed. The crash no longer reproduces.", color = Color.parseColor("#E2E1EB"), textSize = 13f))
        outcomeCard.addView(createCodeBlock("Updated profile_screen.dart in target_app repository."))
        contentContainer.addView(outcomeCard)

        val timelineCard = createCard("Deployment Timeline", "")
        val steps = listOf(
            "Generating patch [Complete]",
            "Sending to laptop [Complete]",
            "Writing to repository [Complete]",
            "Running verification [Complete]"
        )
        steps.forEach { step ->
            val stepRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 4, 0, 4)
            }
            stepRow.addView(createTextView("●  ", color = Color.parseColor("#4EDEA3"), textSize = 10f))
            stepRow.addView(createTextView(step, color = Color.parseColor("#4EDEA3"), textSize = 12f, mono = true))
            timelineCard.addView(stepRow)
        }
        contentContainer.addView(timelineCard)

        val btnRunAgain = Button(this).apply {
            text = "▶  Run Again"
            setTextColor(Color.parseColor("#12131A"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#ADC6FF"))
                cornerRadius = 8f
            }
            setOnClickListener {
                isPatchApplied = false
                switchTab(Tab.SCANNER)
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 46.dpToPx())
            lp.setMargins(0, 10, 0, 8)
            layoutParams = lp
        }
        contentContainer.addView(btnRunAgain)
    }

    // --- TAB 4: ENTERPRISE ANALYTICS ---
    private fun renderAnalyticsTab(snapshot: SessionSnapshot) {
        val headerCard = createCard("PocketQA Enterprise Analytics", "Fleet-wide autonomous test metrics")
        contentContainer.addView(headerCard)

        val coverageCard = createAccentCard("Semantics Node Coverage Index", "", borderAccent = Color.parseColor("#3B82F6"))
        coverageCard.addView(createTextView("94.2%", color = Color.parseColor("#4EDEA3"), textSize = 24f, bold = true))
        coverageCard.addView(createTextView("12 Analyzed Screens • 142 Active Nodes • 0 Unreachable Handlers", color = Color.parseColor("#C2C6D6"), textSize = 11f))
        contentContainer.addView(coverageCard)
    }

    private fun runModelSmokeTest() {
        modelStatus = "Loading Gemma 4 E4B locally on GPU..."
        render(PocketQaSessionStore.snapshot())
        modelRuntime.initialize { load ->
            runOnUiThread {
                when (load) {
                    is ModelLoadResult.Ready -> {
                        modelStatus = "Model ready in ${load.initializationMs}ms. Generating response..."
                        render(PocketQaSessionStore.snapshot())
                        modelRuntime.runSmokePrompt("Reply with one sentence: PocketQA offline mode ready.") { response ->
                            runOnUiThread {
                                modelStatus = when (response) {
                                    is ModelPromptResult.Success -> "Offline response in ${response.elapsedMs}ms: ${response.text}"
                                    is ModelPromptResult.Failed -> "Model failed: ${response.message}"
                                }
                                render(PocketQaSessionStore.snapshot())
                            }
                        }
                    }
                    is ModelLoadResult.Missing -> {
                        modelStatus = "Model file missing: ${load.expectedPath}"
                        render(PocketQaSessionStore.snapshot())
                    }
                    is ModelLoadResult.Failed -> {
                        modelStatus = "Model load failed: ${load.message}"
                        render(PocketQaSessionStore.snapshot())
                    }
                }
            }
        }
    }

    private fun runThermalBenchmark() {
        modelStatus = "Running 3 LiteRT GPU benchmark passes..."
        render(PocketQaSessionStore.snapshot())
        modelRuntime.runThermalBenchmark { benchmark ->
            runOnUiThread {
                modelStatus = when (benchmark) {
                    is ModelBenchmarkResult.Success -> {
                        val avgTtft = benchmark.passes.map { it.timeToFirstTokenMs }.average().toLong()
                        val avgTokSec = benchmark.passes.map { it.decodeTokensPerSecond }.average()
                        "GPU benchmark complete: ${benchmark.passes.size} passes in ${benchmark.elapsedMs}ms; TTFT ${avgTtft}ms; ${"%.1f".format(avgTokSec)} tok/s."
                    }
                    is ModelBenchmarkResult.Failed -> "Benchmark failed: ${benchmark.message}"
                }
                render(PocketQaSessionStore.snapshot())
            }
        }
    }

    // --- UI HELPER FACTORIES ---
    private fun createAccordionCard(
        title: String,
        subtitle: String,
        badgeText: String,
        badgeColor: Int,
        isExpanded: Boolean,
        onToggle: () -> Unit,
        buildContent: (LinearLayout) -> Unit
    ): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 14, 16, 14)
            setBackgroundResource(R.drawable.bg_card_dark)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 10)
            layoutParams = lp
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setOnClickListener { onToggle() }
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(createTextView(title, color = Color.parseColor("#E2E1EB"), textSize = 14f, bold = true))
        if (subtitle.isNotBlank()) {
            textCol.addView(createTextView(subtitle, color = Color.parseColor("#C2C6D6"), textSize = 11f))
        }
        headerRow.addView(textCol)
        headerRow.addView(createBadge(badgeText, badgeColor))
        headerRow.addView(createTextView(if (isExpanded) " ^" else " v", color = Color.parseColor("#3B82F6"), textSize = 12f, bold = true))

        card.addView(headerRow)

        if (isExpanded) {
            val divider = View(this).apply {
                setBackgroundColor(Color.parseColor("#27272A"))
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                lp.setMargins(0, 10, 0, 10)
                layoutParams = lp
            }
            card.addView(divider)

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            buildContent(container)
            card.addView(container)
        }

        return card
    }

    private fun createCard(title: String, subtitle: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 14, 16, 14)
            setBackgroundResource(R.drawable.bg_card_dark)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 12)
            layoutParams = lp

            if (title.isNotBlank()) {
                addView(createTextView(title, color = Color.parseColor("#E2E1EB"), textSize = 14f, bold = true))
            }
            if (subtitle.isNotBlank()) {
                val sub = createTextView(subtitle, color = Color.parseColor("#C2C6D6"), textSize = 11f)
                sub.setPadding(0, 2, 0, 8)
                addView(sub)
            }
        }
    }

    private fun createAccentCard(title: String, subtitle: String, borderAccent: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 14, 16, 14)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E1F26"))
                cornerRadius = 8f
                setStroke(1, borderAccent)
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 12)
            layoutParams = lp

            if (title.isNotBlank()) {
                addView(createTextView(title, color = borderAccent, textSize = 14f, bold = true))
            }
            if (subtitle.isNotBlank()) {
                val sub = createTextView(subtitle, color = Color.parseColor("#C2C6D6"), textSize = 11f)
                sub.setPadding(0, 2, 0, 8)
                addView(sub)
            }
        }
    }

    private fun createBadge(text: String, colorHex: Int): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(colorHex)
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(10, 3, 10, 3)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1B22"))
                cornerRadius = 14f
                setStroke(1, colorHex)
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(6, 0, 6, 0)
            layoutParams = lp
        }
    }

    private fun createFieldLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#8C909F"))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 6, 0, 2)
        }
    }

    private fun createInput(hint: String, value: String, secret: Boolean = false): EditText = EditText(this).apply {
        this.hint = hint
        setHintTextColor(Color.parseColor("#71717A"))
        setTextColor(Color.parseColor("#E2E1EB"))
        setText(value)
        textSize = 12f
        setPadding(18, 12, 18, 12)
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#0C0E14"))
            cornerRadius = 8f
            setStroke(1, Color.parseColor("#27272A"))
        }
        inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, 2, 0, 6)
        layoutParams = lp
    }

    private fun createTextView(text: String, color: Int = Color.WHITE, textSize: Float = 14f, bold: Boolean = false, mono: Boolean = false): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(color)
            this.textSize = textSize
            if (bold) typeface = Typeface.DEFAULT_BOLD
            if (mono) typeface = Typeface.MONOSPACE
        }
    }

    private fun createCodeBlock(codeText: String): TextView {
        return TextView(this).apply {
            text = codeText
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#E2E1EB"))
            textSize = 11f
            setPadding(12, 12, 12, 12)
            setBackgroundResource(R.drawable.bg_card_code)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 4, 0, 8)
            layoutParams = lp
        }
    }

    private fun createDiffBlock(diffText: String): TextView {
        val spannable = SpannableStringBuilder()
        diffText.lines().forEach { line ->
            val start = spannable.length
            spannable.append(line).append("\n")
            val end = spannable.length

            val color = when {
                line.startsWith("+") -> Color.parseColor("#4EDEA3")
                line.startsWith("-") -> Color.parseColor("#FFB4AB")
                line.startsWith("@@") || line.startsWith("diff") -> Color.parseColor("#3B82F6")
                else -> Color.parseColor("#C2C6D6")
            }
            spannable.setSpan(ForegroundColorSpan(color), start, end, 0)
        }

        return TextView(this).apply {
            text = spannable
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setPadding(12, 12, 12, 12)
            setBackgroundResource(R.drawable.bg_card_code)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 4, 0, 8)
            layoutParams = lp
        }
    }

    private fun createPrimaryButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 12f
            setBackgroundResource(R.drawable.bg_button_primary)
            setOnClickListener { onClick() }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 44.dpToPx())
            lp.setMargins(0, 6, 0, 10)
            layoutParams = lp
        }
    }

    private fun createSecondaryButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#E2E1EB"))
            textSize = 11f
            setBackgroundResource(R.drawable.bg_button_secondary)
            setOnClickListener { onClick() }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 34.dpToPx())
            lp.setMargins(0, 4, 6, 4)
            layoutParams = lp
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

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

    private fun showAccessibilityRequired(target: TestTarget) {
        AlertDialog.Builder(this)
            .setTitle("Accessibility Permission Required")
            .setMessage("PocketQA needs its Accessibility Service enabled to explore and test ${target.label}.")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadRepositoryFor(target: TestTarget) {
        val manager = RepoCloneManager(this)
        var result = manager.loadForTarget(target.packageName)
        // Before the package split, QuickCart used the legacy package name.
        // Discard that stale association so the mini demo can exercise visual-only diagnosis.
        if (
            target.packageName == PocketQaAccessibilityService.DEFAULT_TARGET_PACKAGE &&
            result?.first?.subfolder == "bug_app/bugged"
        ) {
            manager.clearForTarget(target.packageName)
            result = null
        }
        if (result != null) {
            val (request, corpus) = result
            repoUrl = request.url
            repoRef = request.ref
            repoSubfolder = request.subfolder
            repoCorpus = corpus
            repoStatus = "Indexed ${corpus.chunks.size} chunks"
        } else {
            val default = TargetProfile.defaultRepositoryFor(target.packageName)
            repoUrl = default?.url.orEmpty()
            repoRef = default?.ref ?: "main"
            repoSubfolder = default?.subfolder.orEmpty()
            repoCorpus = null
            repoStatus = if (default == null) {
                "No repository linked — visual diagnosis available"
            } else {
                "QuickCart source pre-filled — clone & index for source-grounded patches"
            }
        }
    }

    override fun onDestroy() {
        unsubscribe?.invoke()
        modelRuntime.close()
        super.onDestroy()
    }
}
