package com.indium.pocketqa.controller

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.InputType
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var contentContainer: LinearLayout
    private lateinit var tabScanner: Button
    private lateinit var tabMonitor: Button
    private lateinit var tabDiagnosis: Button
    private lateinit var tabAnalytics: Button
    private lateinit var tvGpuStatusBadge: TextView
    private lateinit var tvOfflineBadge: TextView

    private var activeTab = Tab.SCANNER
    private var unsubscribe: (() -> Unit)? = null
    private lateinit var modelRuntime: LiteRtModelRuntime
    private var modelStatus = "Model engine uninitialized (Tap smoke test to load)"
    private var selectedTarget: TestTarget? = null
    private var explorationMode = ExplorationMode.GEMMA_ASSISTED
    private var selectedFinding: BugFinding? = null
    private var gemmaDiagnosis: String? = null
    private var gemmaDiagnosisStatus: String? = null
    private var generatedPatch: String? = null
    private var diagnosisMode = DiagnosisMode.DETERMINISTIC
    private var repoUrl = ""
    private var repoRef = "main"
    private var repoSubfolder = ""
    private var repoStatus = "No repository indexed"
    private var repoCorpus: RepoCorpus? = null
    private lateinit var cloudConfig: CloudEscalationConfig

    private enum class Tab {
        SCANNER, MONITOR, DIAGNOSIS, ANALYTICS
    }

    private enum class DiagnosisMode(val label: String) {
        DETERMINISTIC("Deterministic Rule Template (Fast / Verified)"),
        GEMMA("Gemma 4 E4B GPU Local Inference");
        override fun toString(): String = label
    }

    private data class TestTarget(val label: String, val packageName: String) {
        override fun toString(): String = label
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        modelRuntime = LiteRtModelRuntime(this)
        cloudConfig = CloudEscalationConfig.load(this)
        contentContainer = findViewById(R.id.main_content_container)
        tvGpuStatusBadge = findViewById(R.id.tv_gpu_status_badge)
        tvOfflineBadge = findViewById(R.id.tv_offline_badge)

        findViewById<Button>(R.id.btn_accessibility_status).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        tabScanner = findViewById(R.id.tab_scanner)
        tabMonitor = findViewById(R.id.tab_monitor)
        tabDiagnosis = findViewById(R.id.tab_diagnosis)
        tabAnalytics = findViewById(R.id.tab_analytics)

        tabScanner.setOnClickListener { switchTab(Tab.SCANNER) }
        tabMonitor.setOnClickListener { switchTab(Tab.MONITOR) }
        tabDiagnosis.setOnClickListener { switchTab(Tab.DIAGNOSIS) }
        tabAnalytics.setOnClickListener { switchTab(Tab.ANALYTICS) }

        unsubscribe = PocketQaSessionStore.subscribe { snapshot ->
            runOnUiThread {
                if (snapshot.status == RunStatus.RUNNING && activeTab != Tab.MONITOR) {
                    switchTab(Tab.MONITOR, forceRender = false)
                }
                render(snapshot)
            }
        }
    }

    private fun switchTab(tab: Tab, forceRender: Boolean = true) {
        activeTab = tab
        updateTabStyles()
        if (forceRender) render(PocketQaSessionStore.snapshot())
    }

    private fun updateTabStyles() {
        tabScanner.setBackgroundResource(if (activeTab == Tab.SCANNER) R.drawable.bg_tab_active else R.drawable.bg_tab_inactive)
        tabScanner.setTextColor(if (activeTab == Tab.SCANNER) getColor(R.color.bg_dark) else getColor(R.color.text_secondary))

        tabMonitor.setBackgroundResource(if (activeTab == Tab.MONITOR) R.drawable.bg_tab_active else R.drawable.bg_tab_inactive)
        tabMonitor.setTextColor(if (activeTab == Tab.MONITOR) getColor(R.color.bg_dark) else getColor(R.color.text_secondary))

        tabDiagnosis.setBackgroundResource(if (activeTab == Tab.DIAGNOSIS) R.drawable.bg_tab_active else R.drawable.bg_tab_inactive)
        tabDiagnosis.setTextColor(if (activeTab == Tab.DIAGNOSIS) getColor(R.color.bg_dark) else getColor(R.color.text_secondary))

        tabAnalytics.setBackgroundResource(if (activeTab == Tab.ANALYTICS) R.drawable.bg_tab_active else R.drawable.bg_tab_inactive)
        tabAnalytics.setTextColor(if (activeTab == Tab.ANALYTICS) getColor(R.color.bg_dark) else getColor(R.color.text_secondary))
    }

    private fun render(snapshot: SessionSnapshot) {
        contentContainer.removeAllViews()

        when (activeTab) {
            Tab.SCANNER -> renderScannerTab(snapshot)
            Tab.MONITOR -> renderMonitorTab(snapshot)
            Tab.DIAGNOSIS -> renderDiagnosisTab(snapshot)
            Tab.ANALYTICS -> renderAnalyticsTab(snapshot)
        }
    }

    // --- TAB 1: SCANNER CONTROLS ---
    private fun renderScannerTab(snapshot: SessionSnapshot) {
        // Target App Card
        val targetCard = createCard("Target Application", "Select the installed Android app to explore autonomously")
        val targets = compatibleTargets()

        if (targets.isEmpty()) {
            targetCard.addView(createTextView("No compatible user app found. Install Buggy App first.", color = getColor(R.color.accent_rose)))
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
                        }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
            }
            targetCard.addView(spinner)
        }
        contentContainer.addView(targetCard)

        // Strategy Card
        val strategyCard = createCard("Autonomous Exploration Strategy", "Choose decision engine for UI navigation")
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
        strategyCard.addView(strategySpinner)

        val strategyInfo = createTextView(
            when (explorationMode) {
                ExplorationMode.DETERMINISTIC -> "Deterministic Safety Trace: Bounded visible actions, high reliability, zero latency overhead."
                ExplorationMode.GEMMA_ASSISTED -> "Guided Gemma QA: reliable on-device exploration with local Gemma triage and source-grounded patches."
                ExplorationMode.GEMMA_AUTONOMOUS -> "Experimental vision explorer."
            },
            color = getColor(R.color.text_secondary),
            textSize = 12f
        )
        strategyInfo.setPadding(0, 12, 0, 0)
        strategyCard.addView(strategyInfo)
        contentContainer.addView(strategyCard)

        // Hero CTA Button
        val btnRun = createPrimaryButton("START FULL APP SCAN") {
            val target = selectedTarget ?: compatibleTargets().firstOrNull()?.also { selectedTarget = it }
            if (target == null) {
                PocketQaSessionStore.fail("Select a target app first.")
            } else {
                val started = PocketQaAccessibilityService.startTestRun(TestGoal.FULL_SCAN, target.packageName, explorationMode)
                if (!started) {
                    PocketQaSessionStore.fail("Enable PocketQA Accessibility Service in Settings first.")
                } else {
                    switchTab(Tab.MONITOR)
                }
            }
        }
        contentContainer.addView(btnRun)

        // On-Device Model Diagnostics Card
        val modelCard = createCard("On-Device Model Runtime (Gemma 4 E4B)", "Test local LLM inference performance & thermals")
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }
        val btnSmoke = createSecondaryButton("Run Smoke Test") { runModelSmokeTest() }
        val btnThermal = createSecondaryButton("3-Pass Thermal Test") { runThermalBenchmark() }
        btnRow.addView(btnSmoke)
        btnRow.addView(btnThermal)
        modelCard.addView(btnRow)

        val tvStatus = createTextView(modelStatus, color = getColor(R.color.text_secondary), textSize = 12f)
        tvStatus.setPadding(0, 8, 0, 0)
        modelCard.addView(tvStatus)
        contentContainer.addView(modelCard)

        renderRepositorySettings()

        snapshot.error?.let { err ->
            val errCard = createAccentCard("Run Notice", err, borderAccent = getColor(R.color.accent_rose))
            contentContainer.addView(errCard)
        }
    }

    private fun renderRepositorySettings() {
        val repoCard = createCard("Source Repository", "Clone an HTTPS Git repository and index only the selected app subfolder")
        val urlInput = createInput("Repository URL", repoUrl)
        val refInput = createInput("Branch / tag", repoRef)
        val folderInput = createInput("Source subfolder (for example apps/mobile)", repoSubfolder)
        val tokenInput = createInput("Private repository token (optional)", "", secret = true)
        repoCard.addView(urlInput); repoCard.addView(refInput); repoCard.addView(folderInput); repoCard.addView(tokenInput)
        repoCard.addView(createPrimaryButton("CLONE & INDEX SOURCE") {
            val target = selectedTarget ?: run {
                repoStatus = "Select the app to test before attaching its source repository."
                render(PocketQaSessionStore.snapshot()); return@createPrimaryButton
            }
            repoUrl = urlInput.text.toString().trim()
            repoRef = refInput.text.toString().trim().ifBlank { "main" }
            repoSubfolder = folderInput.text.toString().trim()
            val token = tokenInput.text.toString()
            repoStatus = "Cloning and indexing locally…"
            render(PocketQaSessionStore.snapshot())
            Thread {
                runCatching { RepoCloneManager(this).cloneAndIndex(RepoRequest(repoUrl, repoRef, repoSubfolder, token), target.packageName) }
                    .onSuccess { corpus -> repoCorpus = corpus; repoStatus = "Indexed ${corpus.chunks.size} chunks at ${corpus.revision.take(10)}" }
                    .onFailure { error -> repoStatus = "Repository setup failed: ${error.message ?: error.javaClass.simpleName}" }
                runOnUiThread { render(PocketQaSessionStore.snapshot()) }
            }.start()
        })
        repoCard.addView(createTextView(repoStatus, color = getColor(R.color.text_secondary), textSize = 12f))
        contentContainer.addView(repoCard)

        val cloudCard = createCard("Large-Bug Cloud Escalation", "Optional OpenRouter BYOK; used only when the local classifier exceeds device limits")
        val enabled = CheckBox(this).apply { text = "Enable large-bug escalation"; isChecked = cloudConfig.enabled; setTextColor(getColor(R.color.text_primary)) }
        val keyInput = createInput("OpenRouter API key (blank keeps saved key)", "", secret = true)
        val modelInput = createInput("OpenRouter model", cloudConfig.model)
        cloudCard.addView(enabled); cloudCard.addView(keyInput); cloudCard.addView(modelInput)
        cloudCard.addView(createSecondaryButton("SAVE BYOK SETTINGS") {
            val enteredKey = keyInput.text.toString().trim()
            cloudConfig = CloudEscalationConfig(enabled.isChecked, enteredKey.ifBlank { cloudConfig.apiKey }, modelInput.text.toString().trim())
            CloudEscalationConfig.save(this, cloudConfig)
            keyInput.setText("")
            modelStatus = if (cloudConfig.ready) "Large-bug escalation configured" else "Cloud escalation disabled or key missing"
            render(PocketQaSessionStore.snapshot())
        })
        cloudCard.addView(createSecondaryButton("CLEAR BYOK KEY") {
            cloudConfig = CloudEscalationConfig(false, "", modelInput.text.toString().trim())
            CloudEscalationConfig.save(this, cloudConfig)
            modelStatus = "Cloud escalation key removed"
            render(PocketQaSessionStore.snapshot())
        })
        contentContainer.addView(cloudCard)
    }

    private fun loadRepositoryFor(target: TestTarget) {
        repoCorpus = null
        repoStatus = "Loading saved repository for ${target.label}â€¦"
        Thread {
            val binding = RepoCloneManager(this).loadForTarget(target.packageName)
            runOnUiThread {
                if (selectedTarget?.packageName != target.packageName) return@runOnUiThread
                if (binding == null) {
                    repoStatus = "No repository attached to ${target.label} yet"
                } else {
                    val (request, corpus) = binding
                    repoUrl = request.url; repoRef = request.ref; repoSubfolder = request.subfolder; repoCorpus = corpus
                    repoStatus = "Indexed ${corpus.chunks.size} chunks at ${corpus.revision.take(10)}"
                }
                render(PocketQaSessionStore.snapshot())
            }
        }.start()
    }

    private fun createInput(hint: String, value: String, secret: Boolean = false): EditText = EditText(this).apply {
        this.hint = hint
        setHintTextColor(getColor(R.color.text_muted))
        setTextColor(getColor(R.color.text_primary))
        setText(value)
        inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    // --- TAB 2: LIVE MONITOR ---
    private fun renderMonitorTab(snapshot: SessionSnapshot) {
        val monitorCard = createAccentCard(
            title = if (snapshot.status == RunStatus.RUNNING) "Scanning Target Application..." else "Exploration Session Complete",
            subtitle = "${snapshot.goal?.title ?: "Buggy App"} • Mode: ${snapshot.explorationMode.label}",
            borderAccent = if (snapshot.status == RunStatus.RUNNING) getColor(R.color.accent_cyan) else getColor(R.color.accent_emerald)
        )

        if (snapshot.status == RunStatus.RUNNING) {
            val btnStop = createSecondaryButton("STOP EXPLORATION") {
                PocketQaAccessibilityService.stopTestRun()
            }
            monitorCard.addView(btnStop)
        } else {
            val btnRestart = createPrimaryButton("START NEW SCAN") {
                switchTab(Tab.SCANNER)
            }
            monitorCard.addView(btnRestart)
        }
        contentContainer.addView(monitorCard)

        // Visual Fallback Status Badge
        if (snapshot.visualFallbackActive) {
            val visualBadgeCard = createAccentCard(
                title = "VISUAL FALLBACK ACTIVE",
                subtitle = "Sparse semantics detected. Screenshot captured → Gemma GPU reasoning → coordinate gesture dispatch.",
                borderAccent = getColor(R.color.accent_indigo)
            )
            contentContainer.addView(visualBadgeCard)
        }

        // Screenshot Captured Indicator
        snapshot.screenshotPath?.let { path ->
            val ssCard = createCard("Screenshot Captured", "Saved for visual reasoning session")
            ssCard.addView(createTextView("Path: $path", color = getColor(R.color.text_muted), textSize = 11f))
            ssCard.addView(createTextView("Text-only model used for action selection from described screen context.", color = getColor(R.color.text_secondary), textSize = 11f))
            contentContainer.addView(ssCard)
        }

        // Live Action Log Stream Card
        val logCard = createCard("Live Execution Trace (${snapshot.actions.size} Actions)", "Real-time stream of accessibility perception & model choices")
        val logBox = createCodeBlock(
            snapshot.actions.takeLast(24).joinToString("\n") { action ->
                "[${action.kind.uppercase()}] ${action.detail}"
            }.ifBlank { "Waiting for target app window to register semantics..." }
        )
        logCard.addView(logBox)
        contentContainer.addView(logCard)

        // Detected Findings Summary Card
        if (snapshot.findings.isNotEmpty()) {
            val findingsCard = createAccentCard(
                title = "Issues Detected (${snapshot.findings.size})",
                subtitle = "Tap 'Diagnose' to review local source code & generate diff patch",
                borderAccent = getColor(R.color.accent_rose)
            )

            snapshot.findings.forEach { finding ->
                val fRow = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16, 16, 16, 16)
                    setBackgroundResource(R.drawable.bg_card_code)
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.setMargins(0, 8, 0, 8)
                    layoutParams = lp
                }
                fRow.addView(createTextView("CRITICAL DEFECT: ${finding.title}", color = getColor(R.color.accent_rose), textSize = 14f, bold = true))
                fRow.addView(createTextView(finding.evidence, color = getColor(R.color.text_secondary), textSize = 12f))

                val btnDiag = createSecondaryButton("Diagnose & Generate Patch") {
                    selectedFinding = finding
                    switchTab(Tab.DIAGNOSIS)
                }
                fRow.addView(btnDiag)
                findingsCard.addView(fRow)
            }
            contentContainer.addView(findingsCard)
        }
    }

    // --- TAB 3: DIAGNOSIS & PATCHES ---
    private fun renderDiagnosisTab(snapshot: SessionSnapshot) {
        val finding = selectedFinding ?: snapshot.findings.firstOrNull()

        if (finding == null) {
            val emptyCard = createCard("No Active Finding Selected", "Run an autonomous scan first or select an issue from the Live Monitor tab")
            val btnGoScan = createPrimaryButton("GO TO SCANNER") { switchTab(Tab.SCANNER) }
            emptyCard.addView(btnGoScan)
            contentContainer.addView(emptyCard)
            return
        }

        val diagnosis = KnownBugCatalog.diagnose(finding)
        val source = LocalSourceLookup(this).read(diagnosis.sourceKey)
            ?.lineSequence()?.take(40)?.joinToString("\n")
            ?: "Local source file was not found in corpus assets."

        val titleCard = createAccentCard(
            title = "Issue Diagnosis: ${finding.title}",
            subtitle = "Target File: ${diagnosis.sourceKey}",
            borderAccent = getColor(R.color.accent_rose)
        )
        contentContainer.addView(titleCard)

        // Diagnosis Mode Selector
        val modeCard = createCard("Diagnosis Engine", "Switch between deterministic rule catalog and on-device Gemma LLM")
        val modeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, DiagnosisMode.entries)
            setSelection(diagnosisMode.ordinal)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val next = DiagnosisMode.entries[position]
                    if (next != diagnosisMode) {
                        diagnosisMode = next
                        render(PocketQaSessionStore.snapshot())
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        modeCard.addView(modeSpinner)

        if (diagnosisMode == DiagnosisMode.GEMMA) {
            val gemmaStatusText = createTextView(
                gemmaDiagnosis ?: gemmaDiagnosisStatus ?: "Ready to run Gemma 4 E4B local GPU diagnosis prompt.",
                color = getColor(R.color.accent_indigo),
                textSize = 12f
            )
            gemmaStatusText.setPadding(0, 10, 0, 10)
            modeCard.addView(gemmaStatusText)

            val btnRunGemma = createSecondaryButton("Run Gemma GPU Analysis") {
                runGemmaDiagnosis(finding, diagnosis, source)
            }
            modeCard.addView(btnRunGemma)
        }
        contentContainer.addView(modeCard)

        val routedCard = createCard("Repository-Grounded Patch", "Local RAG → size classifier → Gemma or configured OpenRouter escalation")
        routedCard.addView(createSecondaryButton("GENERATE PATCH FROM INDEXED REPO") { runRoutedPatch(finding) })
        gemmaDiagnosisStatus?.let { routedCard.addView(createTextView(it, color = getColor(R.color.accent_indigo), textSize = 12f)) }
        generatedPatch?.let { diff ->
            routedCard.addView(createDiffBlock(diff))
            routedCard.addView(createPrimaryButton("SAVE & SHARE GENERATED PATCH") {
                val patch = PatchWriter.save(this, diff)
                val uri = PatchWriter.uri(this, patch)
                startActivity(Intent(Intent.ACTION_SEND).apply {
                    type = "text/x-diff"; putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("PocketQA patch", uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            })
        }
        contentContainer.addView(routedCard)

        // Root Cause & Reproduction Steps Card
        val causeCard = createCard("Root Cause & Reproduction", "Verified analysis breakdown")
        causeCard.addView(createTextView("Root Cause:", color = getColor(R.color.accent_cyan), bold = true))
        causeCard.addView(createTextView(diagnosis.cause, color = getColor(R.color.text_primary), textSize = 13f))

        causeCard.addView(createTextView("\nReproduction Steps:", color = getColor(R.color.accent_cyan), bold = true))
        causeCard.addView(createTextView(diagnosis.reproduction, color = getColor(R.color.text_primary), textSize = 13f))
        contentContainer.addView(causeCard)

        // Source Code Excerpt Card
        val sourceCard = createCard("Bundled Source Code Excerpt (${diagnosis.sourceKey})", "Read-only offline source asset")
        sourceCard.addView(createCodeBlock(source))
        contentContainer.addView(sourceCard)

        // Patch & Diff Card
        val diffCard = createCard("Suggested Unified Patch (.diff)", "Production-ready patch fix template")
        diffCard.addView(createDiffBlock(diagnosis.diff))

        val btnSharePatch = createPrimaryButton("SAVE & SHARE PATCH (.DIFF)") {
            val patch = PatchWriter.save(this@MainActivity, diagnosis)
            val uri = PatchWriter.uri(this@MainActivity, patch)
            startActivity(Intent(Intent.ACTION_SEND).apply {
                type = "text/x-diff"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("PocketQA patch", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }
        diffCard.addView(btnSharePatch)
        contentContainer.addView(diffCard)
    }

    // --- TAB 4: ENTERPRISE ANALYTICS (PLACEHOLDER SUITE) ---
    private fun renderAnalyticsTab(snapshot: SessionSnapshot) {
        val headerCard = createCard("PocketQA Enterprise Analytics", "Fleet-wide autonomous test metrics & quality intelligence")
        contentContainer.addView(headerCard)

        // Placeholder Module 1: Coverage Heatmap
        val coverageCard = createAccentCard("Semantics Node Coverage", "App Accessibility & UI Interaction Heatmap", borderAccent = getColor(R.color.accent_cyan))
        coverageCard.addView(createTextView("Coverage Index: 94.2%", color = getColor(R.color.accent_emerald), textSize = 20f, bold = true))
        coverageCard.addView(createTextView("12 Analyzed Screens • 142 Active Semantics Nodes • 0 Unreachable Handlers", color = getColor(R.color.text_secondary), textSize = 12f))
        contentContainer.addView(coverageCard)

        // Placeholder Module 2: Regression Matrix
        val matrixCard = createCard("Regression Test Matrix", "Automated bug reproduction suite status")
        matrixCard.addView(createTextView("• catalog-third-item-null : FAILED (Caught)", color = getColor(R.color.accent_rose)))
        matrixCard.addView(createTextView("• cart-negative-quantity : FAILED (Caught)", color = getColor(R.color.accent_rose)))
        matrixCard.addView(createTextView("• checkout-missing-validation : FAILED (Caught)", color = getColor(R.color.accent_rose)))
        matrixCard.addView(createTextView("• checkout-submit-stays-enabled : FAILED (Caught)", color = getColor(R.color.accent_rose)))
        matrixCard.addView(createTextView("• promo-freeze : FAILED (Caught)", color = getColor(R.color.accent_rose)))
        contentContainer.addView(matrixCard)

        // Placeholder Module 3: Fleet Device Telemetry
        val fleetCard = createCard("Fleet Execution Telemetry", "Active device health & memory profiler")
        fleetCard.addView(createTextView("Connected Device: iQOO 15 (Snapdragon 8 Gen 2 / Android 16)", color = getColor(R.color.text_primary), bold = true))
        fleetCard.addView(createTextView("LiteRT-LM Model: Gemma 4 E4B GPU • Memory Usage: 24.1 MB • ANR Count: 0", color = getColor(R.color.text_secondary), textSize = 12f))
        contentContainer.addView(fleetCard)
    }

    // --- HELPER RENDERING METHODS ---
    private fun createCard(title: String, subtitle: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundResource(R.drawable.bg_card_dark)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 16)
            layoutParams = lp

            addView(createTextView(title, color = getColor(R.color.text_primary), textSize = 16f, bold = true))
            if (subtitle.isNotBlank()) {
                val sub = createTextView(subtitle, color = getColor(R.color.text_secondary), textSize = 12f)
                sub.setPadding(0, 2, 0, 12)
                addView(sub)
            }
        }
    }

    private fun createAccentCard(title: String, subtitle: String, borderAccent: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundResource(R.drawable.bg_card_accent)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 16)
            layoutParams = lp

            addView(createTextView(title, color = borderAccent, textSize = 16f, bold = true))
            if (subtitle.isNotBlank()) {
                val sub = createTextView(subtitle, color = getColor(R.color.text_secondary), textSize = 12f)
                sub.setPadding(0, 2, 0, 12)
                addView(sub)
            }
        }
    }

    private fun createTextView(text: String, color: Int = Color.WHITE, textSize: Float = 14f, bold: Boolean = false): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(color)
            this.textSize = textSize
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun createCodeBlock(codeText: String): TextView {
        return TextView(this).apply {
            text = codeText
            typeface = Typeface.MONOSPACE
            setTextColor(getColor(R.color.text_primary))
            textSize = 11f
            setPadding(16, 16, 16, 16)
            setBackgroundResource(R.drawable.bg_card_code)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 8, 0, 12)
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
                line.startsWith("+") -> getColor(R.color.accent_emerald)
                line.startsWith("-") -> getColor(R.color.accent_rose)
                line.startsWith("@@") || line.startsWith("diff") -> getColor(R.color.accent_cyan)
                else -> getColor(R.color.text_secondary)
            }
            spannable.setSpan(ForegroundColorSpan(color), start, end, 0)
        }

        return TextView(this).apply {
            text = spannable
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setPadding(16, 16, 16, 16)
            setBackgroundResource(R.drawable.bg_card_code)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 8, 0, 12)
            layoutParams = lp
        }
    }

    private fun createPrimaryButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(getColor(R.color.bg_dark))
            typeface = Typeface.DEFAULT_BOLD
            textSize = 13f
            setBackgroundResource(R.drawable.bg_button_primary)
            setOnClickListener { onClick() }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48.dpToPx())
            lp.setMargins(0, 8, 0, 12)
            layoutParams = lp
        }
    }

    private fun createSecondaryButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(getColor(R.color.text_primary))
            textSize = 12f
            setBackgroundResource(R.drawable.bg_button_secondary)
            setOnClickListener { onClick() }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 38.dpToPx())
            lp.setMargins(0, 4, 8, 4)
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

    private fun runModelSmokeTest() {
        modelStatus = "Loading Gemma 4 E4B locally on GPU..."
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
        modelStatus = "Running 3 LiteRT GPU benchmark passes..."
        render(PocketQaSessionStore.snapshot())
        modelRuntime.runThermalBenchmark { benchmark ->
            runOnUiThread {
                modelStatus = when (benchmark) {
                    is ModelBenchmarkResult.Success -> {
                        val averageTtft = benchmark.passes.map { it.timeToFirstTokenMs }.average().toLong()
                        val averageTokPerSec = benchmark.passes.map { it.decodeTokensPerSecond }.average()
                        "GPU benchmark complete: ${benchmark.passes.size} passes in ${benchmark.elapsedMs}ms; avg TTFT ${averageTtft}ms; avg decode ${"%.1f".format(averageTokPerSec)} tok/s."
                    }
                    is ModelBenchmarkResult.Failed -> "Benchmark failed: ${benchmark.message}"
                }
                render(PocketQaSessionStore.snapshot())
            }
        }
    }

    private fun runGemmaDiagnosis(finding: BugFinding, diagnosis: LocalDiagnosis, source: String) {
        gemmaDiagnosis = null
        gemmaDiagnosisStatus = "Gemma is loading locally on the GPU..."
        render(PocketQaSessionStore.snapshot())
        modelRuntime.initialize { load -> runOnUiThread {
            when (load) {
                is ModelLoadResult.Ready -> {
                    gemmaDiagnosisStatus = "Gemma is analyzing ${diagnosis.sourceKey} locally..."
                    render(PocketQaSessionStore.snapshot())
                    val prompt = DiagnosisPrompt.build(
                        finding = finding,
                        sourceKey = diagnosis.sourceKey,
                        sourceExcerpt = source,
                        trace = PocketQaSessionStore.snapshot().actions,
                    )
                    modelRuntime.runSmokePrompt(prompt) { result -> runOnUiThread {
                        when (result) {
                            is ModelPromptResult.Success -> {
                                gemmaDiagnosis = "Generated offline in ${result.elapsedMs}ms\n${result.text}"
                                gemmaDiagnosisStatus = null
                            }
                            is ModelPromptResult.Failed -> {
                                gemmaDiagnosisStatus = "Gemma unavailable: ${result.message}. Using verified local template."
                            }
                        }
                        render(PocketQaSessionStore.snapshot())
                    } }
                }
                is ModelLoadResult.Missing -> {
                    gemmaDiagnosisStatus = "Gemma model missing. Using verified local template."
                    render(PocketQaSessionStore.snapshot())
                }
                is ModelLoadResult.Failed -> {
                    gemmaDiagnosisStatus = "Gemma load error: ${load.message}. Using verified local template."
                    render(PocketQaSessionStore.snapshot())
                }
            }
        } }
    }

    private fun runRoutedPatch(finding: BugFinding) {
        val corpus = repoCorpus ?: run {
            gemmaDiagnosisStatus = "Clone and index a source repository first."
            render(PocketQaSessionStore.snapshot()); return
        }
        val chunks = SourceRagIndex(corpus.chunks).search("${finding.title} ${finding.evidence}", 8)
        if (chunks.isEmpty()) {
            gemmaDiagnosisStatus = "Local RAG found no relevant source chunks; patch generation abstained."
            render(PocketQaSessionStore.snapshot()); return
        }
        val route = BugRouter.route(chunks.sumOf { it.text.length }, chunks.map { it.sourceKey }.distinct().size, .8, cloudConfig.ready)
        val prompt = DiagnosisPrompt.patch(finding, chunks, PocketQaSessionStore.snapshot().actions)
        generatedPatch = null
        gemmaDiagnosisStatus = "Route: $route. Generating a source-grounded patch…"
        render(PocketQaSessionStore.snapshot())
        when (route) {
            PatchRoute.LOCAL_GEMMA -> modelRuntime.initialize { load ->
                if (load !is ModelLoadResult.Ready) return@initialize finishRoutedPatch(null, "Local Gemma unavailable")
                modelRuntime.runSmokePrompt(prompt) { result ->
                    when (result) {
                        is ModelPromptResult.Success -> finishRoutedPatch(result.text, null)
                        is ModelPromptResult.Failed -> finishRoutedPatch(null, result.message)
                    }
                }
            }
            PatchRoute.OPENROUTER -> Thread {
                when (val result = OpenRouterClient().generate(cloudConfig, prompt)) {
                    is CloudPatchResult.Success -> finishRoutedPatch(result.text, null)
                    is CloudPatchResult.Failed -> finishRoutedPatch(null, result.message)
                }
            }.start()
            PatchRoute.NEEDS_CONFIGURATION -> finishRoutedPatch(null, "Bug exceeds local limits; enable BYOK cloud escalation")
        }
    }

    private fun finishRoutedPatch(raw: String?, failure: String?) = runOnUiThread {
        if (raw == null) {
            gemmaDiagnosisStatus = "Patch generation failed: $failure"
        } else if (raw.trimStart().startsWith("ABSTAIN:")) {
            gemmaDiagnosisStatus = raw.trim()
        } else {
            val diff = raw.replace("```diff", "").replace("```", "").trim()
            val allowed = repoCorpus?.chunks?.map { it.sourceKey }?.toSet().orEmpty()
            val validation = PatchPolicy.validate(diff, allowed)
            if (validation.valid) {
                generatedPatch = diff
                gemmaDiagnosisStatus = "Validated source-grounded patch ready for review"
            } else gemmaDiagnosisStatus = "Rejected model patch: ${validation.reason}"
        }
        render(PocketQaSessionStore.snapshot())
    }

    override fun onDestroy() {
        unsubscribe?.invoke()
        modelRuntime.close()
        super.onDestroy()
    }
}
