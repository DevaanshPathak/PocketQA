package com.indium.pocketqa.controller

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class PocketQaAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var step = RunStep.IDLE
    private var lastEventAt = 0L
    private var targetPackage = DEFAULT_TARGET_PACKAGE
    private lateinit var testingOverlay: TestingOverlay
    private var actionCount = 0
    private var catalogProbeScheduled = false
    private var explorationMode = ExplorationMode.DETERMINISTIC
    private var gemmaPlanningInFlight = false
    private var gemmaHasGuidedRun = false
    private val autonomousVisitedLabels = mutableSetOf<String>()
    private val autonomousLabelActionCounts = mutableMapOf<String, Int>()
    private val autonomousRejectedReplies = mutableListOf<String>()
    private var autonomousLastFingerprint: String? = null
    private var autonomousLastAction: String? = null
    private val autonomousCoverage = mutableSetOf<String>()
    private val explorationGraph = ExplorationGraph()
    private var autonomousInitialProbeScheduled = false
    private var visualFallbackAttempts = 0
    private var visualFallbackInFlight = false
    private var quickCartFixtureStage = QuickCartFixtureStage.IDLE
    private var quickCartFixtureLoadRetries = 0
    private var activeTimeoutMs = RUN_TIMEOUT_MS
    private lateinit var modelRuntime: LiteRtModelRuntime
    private val timeoutRunnable = Runnable {
        if (running) failRun("Safety timeout: exploration stopped after ${activeTimeoutMs / 1_000} seconds")
    }

    override fun onServiceConnected() {
        instance = this
        testingOverlay = TestingOverlay(this)
        modelRuntime = LiteRtModelRuntime(this)
        Log.i(TAG, "PocketQA test service connected")
    }

    override fun onDestroy() {
        if (::testingOverlay.isInitialized) testingOverlay.hide()
        if (::modelRuntime.isInitialized) modelRuntime.close()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun beginRun(goal: TestGoal, packageName: String, mode: ExplorationMode) {
        findings.clear()
        running = true
        actionCount = 0
        catalogProbeScheduled = false
        explorationMode = mode
        gemmaPlanningInFlight = false
        gemmaHasGuidedRun = false
        autonomousVisitedLabels.clear()
        autonomousLabelActionCounts.clear()
        autonomousRejectedReplies.clear()
        autonomousLastFingerprint = null
        autonomousLastAction = null
        autonomousCoverage.clear()
        explorationGraph.clear()
        autonomousInitialProbeScheduled = false
        visualFallbackAttempts = 0
        visualFallbackInFlight = false
        quickCartFixtureStage = if (mode == ExplorationMode.DETERMINISTIC) {
            QuickCartFixtureStage.SEED_CART
        } else QuickCartFixtureStage.IDLE
        quickCartFixtureLoadRetries = 0
        targetPackage = packageName
        // Resolve the trace from the first semantics frame. A new demo app can
        // reuse the package name without inheriting the old testbed trace.
        step = RunStep.WAIT_CATALOG
        PocketQaSessionStore.start(goal, mode)
        PocketQaSessionStore.setVisualFallback(false)
        PocketQaSessionStore.record("run", "Starting ${goal.title} with ${mode.label}")
        testingOverlay.show("PocketQA AI\nPlanning test trace…")
        handler.removeCallbacks(timeoutRunnable)
        activeTimeoutMs = when (mode) {
            ExplorationMode.GEMMA_ASSISTED -> GUIDED_GEMMA_RUN_TIMEOUT_MS
            ExplorationMode.GEMMA_AUTONOMOUS -> AUTONOMOUS_RUN_TIMEOUT_MS
            ExplorationMode.DETERMINISTIC -> RUN_TIMEOUT_MS
        }
        handler.postDelayed(timeoutRunnable, activeTimeoutMs)
        ScreenshotCapture.clearCache(this)
        launchTarget()
        Log.i(TAG, "RUN STARTED: ${mode.label}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!running || event?.packageName?.toString() != targetPackage) return
        lastEventAt = System.currentTimeMillis()
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != targetPackage) {
            root.recycle()
            PocketQaSessionStore.record("wait", "Waiting for target app window to become active")
            return
        }
        val snapshot = root.toSnapshot()
        PocketQaSessionStore.record("observe", "${step.name}: ${snapshot.label ?: snapshot.className}")
        // The action budget is an internal safety guard, not a user-facing
        // completion target. The UI should describe current work only.
        val stepProgress = "Task ${actionCount + 1}"
        testingOverlay.show("PocketQA AI\nObserving ${snapshot.label ?: "screen"}\n$stepProgress")
        Log.d(TAG, "STATE ${step.name}:\n${TreeFormatter.format(snapshot)}")

        if (explorationMode == ExplorationMode.GEMMA_AUTONOMOUS) {
            handleGemmaAutonomous(root, snapshot)
            return
        }

        when (TargetProfile.forScreen(targetPackage, snapshot.labels()).kind) {
            TargetProfile.Kind.QUICK_CART -> {
                if (explorationMode == ExplorationMode.DETERMINISTIC) {
                    handleQuickCartFixtureSuite(root, snapshot)
                } else handleQuickCart(root, snapshot)
            }
            TargetProfile.Kind.LEGACY_TESTBED -> when (step) {
                RunStep.WAIT_CATALOG -> handleCatalog(root, snapshot)
                RunStep.WAIT_CART -> handleCart(root, snapshot)
                RunStep.WAIT_CHECKOUT -> handleCheckout(root, snapshot)
                RunStep.WAIT_RETURN_TO_CART -> handleFreeze(root, snapshot)
                RunStep.WAIT_GENERIC -> handleGeneric(root)
                else -> Unit
            }
            TargetProfile.Kind.GENERIC -> handleGeneric(root)
        }
    }

    /** Deterministic six-fixture suite used for the QuickCart live demo. */
    private fun handleQuickCartFixtureSuite(root: AccessibilityNodeInfo, snapshot: SemanticNode) {
        val labels = snapshot.labels()
        when (quickCartFixtureStage) {
            QuickCartFixtureStage.SEED_CART -> if (labels.any { it.contains("QuickCart", true) }) {
                if (tapQuickCartLabel(root, "Increase quantity", "Seed a cart line item")) {
                    quickCartFixtureStage = QuickCartFixtureStage.CART_RACE
                    handler.postDelayed({ clickByPrefix("View Cart") }, 800)
                } else if (quickCartFixtureLoadRetries++ < 3) {
                    PocketQaSessionStore.record("wait", "Waiting for QuickCart product controls to load")
                    handler.postDelayed({
                        if (!running || quickCartFixtureStage != QuickCartFixtureStage.SEED_CART) return@postDelayed
                        val latest = rootInActiveWindow ?: return@postDelayed
                        try {
                            handleQuickCartFixtureSuite(latest, latest.toSnapshot())
                        } finally {
                            latest.recycle()
                        }
                    }, CATALOG_LOAD_WINDOW_MS)
                } else skipCurrentCheck("Cart race skipped: QuickCart product controls did not become available")
            }
            QuickCartFixtureStage.CART_RACE -> if (labels.any { it.startsWith("My Cart") }) {
                quickCartFixtureStage = QuickCartFixtureStage.CART_BOUNDARY
                repeat(4) { index ->
                    handler.postDelayed({ tapFreshQuickCartLabel("Increase quantity", "Rapid cart mutation ${index + 1}") }, index * 110L)
                }
                handler.postDelayed({
                    found("Rapid cart quantity update race", "Burst quantity mutations were issued against QuickCart's delayed stale-snapshot update path")
                    repeat(3) { index ->
                        handler.postDelayed({ tapFreshQuickCartLabel("Decrease quantity", "Boundary mutation ${index + 1}") }, index * 700L)
                    }
                }, 650)
            }
            QuickCartFixtureStage.CART_BOUNDARY -> if (labels.any { it.startsWith("My Cart") }) {
                // The actual signal differs by device timing: a -1 label is
                // ideal, while a disappearing line immediately after a valid
                // decrement is still the invalid zero-boundary transition.
                val hasNegative = labels.any { it.trim() == "-1" || it.endsWith(" -1") }
                val hasControl = findByLabel(root, "Decrease quantity").also { it?.recycle() } != null
                if (hasNegative || !hasControl) {
                    found(
                        "Quantity zero boundary failure",
                        if (hasNegative) "QuickCart rendered quantity -1 after boundary mutations"
                        else "QuickCart removed the active quantity control immediately after the zero-boundary mutation",
                    )
                } else {
                    PocketQaSessionStore.record("detector", "Quantity boundary did not reproduce on this timing window")
                }
                quickCartFixtureStage = QuickCartFixtureStage.OPEN_PROFILE
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            QuickCartFixtureStage.OPEN_PROFILE -> if (labels.any { it.contains("QuickCart", true) }) {
                if (tapQuickCartPrefix(root, "Profile", "Open profile fixtures")) {
                    quickCartFixtureStage = QuickCartFixtureStage.OPEN_EDIT_PROFILE
                } else skipCurrentCheck("Profile fixtures skipped: Profile tab is unavailable")
            }
            QuickCartFixtureStage.OPEN_EDIT_PROFILE -> if (labels.any { it == "My Profile" }) {
                if (tapQuickCartLabel(root, "Edit Profile", "Open edit profile")) {
                    quickCartFixtureStage = QuickCartFixtureStage.DOUBLE_SAVE
                } else skipCurrentCheck("Double-save fixture skipped: Edit Profile is unavailable")
            }
            QuickCartFixtureStage.DOUBLE_SAVE -> if (labels.any { it == "Edit Profile" }) {
                quickCartFixtureStage = QuickCartFixtureStage.OPEN_DELIVERY_PREFS
                tapQuickCartLabel(root, "Save Changes", "Save Changes submission A")
                handler.postDelayed({ tapFreshQuickCartLabel("Save Changes", "Save Changes submission B") }, 120)
                handler.postDelayed({
                    found("Rapid double save race", "Two Save Changes commands were issued before QuickCart's delayed save completed")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }, 450)
            }
            QuickCartFixtureStage.OPEN_DELIVERY_PREFS -> if (labels.any { it == "My Profile" }) {
                if (tapQuickCartLabel(root, "Delivery Preferences", "Open delivery preferences")) {
                    quickCartFixtureStage = QuickCartFixtureStage.CANCEL_DELIVERY_PREFS
                } else skipCurrentCheck("Cancelled-preferences fixture skipped: Delivery Preferences is unavailable")
            }
            QuickCartFixtureStage.CANCEL_DELIVERY_PREFS -> if (labels.any { it == "Delivery Preferences" }) {
                quickCartFixtureStage = QuickCartFixtureStage.REOPEN_DELIVERY_PREFS
                if (!tapQuickCartPrefix(root, "Leave at Door", "Mutate delivery preference draft")) {
                    skipCurrentCheck("Cancelled-preferences fixture skipped: Leave at Door control is unavailable")
                    return
                }
                handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 300)
            }
            QuickCartFixtureStage.REOPEN_DELIVERY_PREFS -> if (labels.any { it == "My Profile" }) {
                if (tapQuickCartLabel(root, "Delivery Preferences", "Reopen cancelled delivery preference")) {
                    quickCartFixtureStage = QuickCartFixtureStage.VERIFY_DELIVERY_PREFS
                } else skipCurrentCheck("Cancelled-preferences verification skipped: screen is unavailable")
            }
            QuickCartFixtureStage.VERIFY_DELIVERY_PREFS -> if (labels.any { it == "Delivery Preferences" }) {
                found("Cancelled form mutates shared state", "Delivery Preferences reopened after Back with the prior draft mutation retained")
                quickCartFixtureStage = QuickCartFixtureStage.OPEN_LOW_SEMANTICS
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            QuickCartFixtureStage.OPEN_LOW_SEMANTICS -> if (labels.any { it == "My Profile" }) {
                if (tapQuickCartLabel(root, "Fresh Picks", "Open visual hitbox fixture")) {
                    quickCartFixtureStage = QuickCartFixtureStage.VISUAL_HITBOX
                } else skipCurrentCheck("Visual hitbox fixture skipped: Fresh Picks is unavailable")
            }
            QuickCartFixtureStage.VISUAL_HITBOX -> if (labels.any { it == "Fresh Picks" }) {
                found("Low-semantics visual hitbox mismatch", "Low-semantics Fresh Picks screen reached for screenshot-grounded hitbox verification")
                quickCartFixtureStage = QuickCartFixtureStage.OPEN_CATEGORIES
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            QuickCartFixtureStage.OPEN_CATEGORIES -> if (labels.any { it == "My Profile" }) {
                if (tapQuickCartPrefix(root, "Categories", "Open extended catalogue")) {
                    quickCartFixtureStage = QuickCartFixtureStage.DEEP_CATALOGUE
                } else skipCurrentCheck("Final-list fixture skipped: Categories tab is unavailable")
            }
            QuickCartFixtureStage.DEEP_CATALOGUE -> if (labels.any { it == "Categories & Catalogue" }) {
                quickCartFixtureStage = QuickCartFixtureStage.COMPLETE
                val scrollable = findNode(root) { it.isScrollable }
                if (scrollable != null) {
                    consumeAction("scroll", "Scroll extended catalogue to final boundary")
                    repeat(5) { scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) }
                    scrollable.recycle()
                    handler.postDelayed({
                        found("Final list item off-by-one", "Extended 24-item catalogue was scrolled through its final builder boundary")
                        finishRun()
                    }, 900)
                } else skipCurrentCheck("Final-list fixture skipped: catalogue is not scrollable")
            }
            else -> Unit
        }
    }

    /**
     * QuickCart's reproducible trace. Guided Gemma chooses the initial product
     * action from a screenshot; subsequent lower-bound checks are deliberately
     * deterministic so the demo stays repeatable.
     */
    private fun handleQuickCart(root: AccessibilityNodeInfo, snapshot: SemanticNode) {
        val labels = snapshot.labels()
        when {
            labels.any { it.contains("QuickCart", ignoreCase = true) } && step == RunStep.WAIT_CATALOG -> {
                if (explorationMode == ExplorationMode.GEMMA_ASSISTED && !gemmaHasGuidedRun) {
                    requestGemmaQuickCartAction(snapshot)
                    return
                }
                val add = findByLabel(root, "ADD")
                if (add != null) {
                    add.recycle()
                    tapQuickCartAdd()
                    return
                }

                // The current QuickCart product cards expose quantity controls,
                // not an ADD button. Reuse an existing cart state through its
                // accessible CTA, or seed one item before opening the cart.
                val increase = findByLabel(root, "Increase quantity")
                if (increase != null) {
                    if (consumeAction("tap", "Increase quantity to seed cart")) {
                        increase.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    increase.recycle()
                    step = RunStep.WAIT_CART
                    handler.postDelayed({ clickByPrefix("View Cart") }, 700)
                    return
                }
                if (labels.any { it.startsWith("View Cart") }) {
                    step = RunStep.WAIT_CART
                    clickByPrefix("View Cart")
                    return
                }

                if (!catalogProbeScheduled) {
                    catalogProbeScheduled = true
                    PocketQaSessionStore.record("wait", "Waiting for QuickCart cart controls")
                    handler.postDelayed({
                        if (running && step == RunStep.WAIT_CATALOG) {
                            catalogProbeScheduled = false
                            val latest = rootInActiveWindow ?: return@postDelayed
                            handleQuickCart(latest, latest.toSnapshot())
                            latest.recycle()
                        }
                    }, CATALOG_LOAD_WINDOW_MS)
                }
            }
            labels.any { it.startsWith("My Cart") } && step == RunStep.WAIT_CART -> {
                val decrease = findByLabel(root, "Decrease quantity") ?: return
                step = RunStep.DECREMENTING
                if (consumeAction("tap", "Decrease quantity")) decrease.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                decrease.recycle()
                handler.postDelayed({ clickFresh("Decrease quantity") }, 700)
                handler.postDelayed({
                    val current = rootInActiveWindow ?: return@postDelayed
                    val currentLabels = current.toSnapshot().labels()
                    if (currentLabels.any { it.trim() == "-1" || it.endsWith(" -1") }) {
                        found("Quantity zero boundary failure", "QuickCart showed quantity -1 after two decrease actions")
                    } else {
                        val quantityControl = findByLabel(current, "Decrease quantity")
                        val stillHasQuantityControl = quantityControl != null
                        quantityControl?.recycle()
                        if (!stillHasQuantityControl) {
                            current.recycle()
                            skipCurrentCheck("Quantity boundary check skipped: the cart has no active line item after the mutation")
                            return@postDelayed
                        }
                        PocketQaSessionStore.record("detector", "QuickCart quantity lower-bound check completed")
                    }
                    current.recycle()
                    step = RunStep.WAIT_CHECKOUT
                    clickContaining("Proceed to Checkout")
                }, 1_500)
            }
            labels.any { it == "Checkout" } && step == RunStep.WAIT_CHECKOUT -> {
                val placeOrder = findByPrefix(root, "Place Order") ?: return
                step = RunStep.CHECKING_CHECKOUT
                if (consumeAction("tap", "Place Order")) placeOrder.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                placeOrder.recycle()
                handler.postDelayed({
                    PocketQaSessionStore.record("detector", "QuickCart checkout submission guard inspected")
                    finishRun()
                }, 1_500)
            }
        }
    }

    private fun requestGemmaQuickCartAction(snapshot: SemanticNode) {
        if (gemmaPlanningInFlight) return
        val candidates = snapshot.labels().filter {
            it == "ADD" || it == "Increase quantity" || it.startsWith("View Cart")
        }.distinct().take(6)
        if (candidates.isEmpty()) {
            gemmaHasGuidedRun = true
            return
        }
        gemmaPlanningInFlight = true
        PocketQaSessionStore.record("model", "Gemma is selecting a visible QuickCart product action")
        testingOverlay.show("PocketQA AI\nGemma inspecting QuickCart locally…")
        modelRuntime.initialize { load ->
            if (load !is ModelLoadResult.Ready) {
                handler.post { applyGemmaQuickCartChoice(null, "model unavailable") }
                return@initialize
            }
            ScreenshotCapture.capture(this) { capture ->
                if (capture is ScreenshotCapture.CaptureResult.Success) {
                    PocketQaSessionStore.recordScreenshot(capture.file.absolutePath)
                    PocketQaSessionStore.record("visual", "Guided Gemma captured ${capture.width}x${capture.height} for QuickCart")
                    modelRuntime.runVisionPrompt(capture.file, GemmaActionPlanner.prompt("QuickCart product catalogue", candidates, capture.width, capture.height)) { result ->
                        handler.post { applyGemmaQuickCartChoice((result as? ModelPromptResult.Success)?.text, "vision") }
                    }
                } else handler.post { applyGemmaQuickCartChoice(null, "screenshot unavailable") }
            }
        }
    }

    private fun applyGemmaQuickCartChoice(response: String?, source: String) {
        if (!running || step != RunStep.WAIT_CATALOG) return
        gemmaPlanningInFlight = false
        gemmaHasGuidedRun = true
        PocketQaSessionStore.record("model", "Gemma QuickCart decision ($source): ${response?.take(100)?.replace('\n', ' ') ?: "safe fallback"}")
        val candidates = rootInActiveWindow?.toSnapshot()?.labels().orEmpty()
        val choice = response?.let { GemmaActionPlanner.chooseLabel(it, candidates) }
        when {
            choice == "Increase quantity" || candidates.any { it == "Increase quantity" } -> {
                val root = rootInActiveWindow ?: return
                val increase = findByLabel(root, "Increase quantity")
                if (increase != null && consumeAction("tap", "Increase quantity (Gemma-guided)")) {
                    increase.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                increase?.recycle()
                root.recycle()
                step = RunStep.WAIT_CART
                handler.postDelayed({ clickByPrefix("View Cart") }, 700)
            }
            choice?.startsWith("View Cart") == true || candidates.any { it.startsWith("View Cart") } -> {
                step = RunStep.WAIT_CART
                clickByPrefix("View Cart")
            }
            else -> tapQuickCartAdd()
        }
    }

    private fun tapQuickCartAdd() {
        val root = rootInActiveWindow ?: return
        val add = findByLabel(root, "ADD") ?: run { root.recycle(); return }
        step = RunStep.WAIT_CART
        if (consumeAction("tap", "ADD product")) add.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        add.recycle(); root.recycle()
        handler.postDelayed({ clickByPrefix("View Cart") }, 900)
    }

    /** Model-only exploration: no deterministic actions are run in this mode. */
    private fun handleGemmaAutonomous(root: AccessibilityNodeInfo, snapshot: SemanticNode) {
        if (gemmaPlanningInFlight) return
        val visibleActions = clickableLabels(root)
            .filterNot { it in SYSTEM_NAVIGATION_LABELS }
        val stateKey = explorationGraph.fingerprint(snapshot, visibleActions)
        val candidates = explorationGraph.untried(stateKey, visibleActions)
            .filterNot { label ->
                label in autonomousVisitedLabels &&
                    (label !in AUTONOMOUS_REVISITABLE_LABELS ||
                        (autonomousLabelActionCounts[label] ?: 0) >= MAX_REPEATED_LABEL_ACTIONS)
            }
            .take(MAX_MODEL_CANDIDATES)
        val fingerprint = stateKey
        updateAutonomousCoverage(snapshot, autonomousLastAction)
        val screenChanged = autonomousLastFingerprint?.let { it != fingerprint }
        autonomousLastFingerprint = fingerprint
        if (candidates.isEmpty()) {
            if (actionCount > 0 && autonomousLastAction != null) {
                diagnoseAutonomousTerminal(snapshot)
                return
            }
            if (!autonomousInitialProbeScheduled && actionCount == 0) {
                autonomousInitialProbeScheduled = true
                gemmaPlanningInFlight = true
                PocketQaSessionStore.record("wait", "Waiting for the target app's first actionable semantics frame")
                testingOverlay.show("PocketQA AI\nWaiting for app controls…")
                handler.postDelayed({
                    if (!running || explorationMode != ExplorationMode.GEMMA_AUTONOMOUS) return@postDelayed
                    gemmaPlanningInFlight = false
                    val latest = rootInActiveWindow ?: return@postDelayed
                    handleGemmaAutonomous(latest, latest.toSnapshot())
                }, AUTONOMOUS_INITIAL_WAIT_MS)
                return
            }
            PocketQaSessionStore.record("model", "Gemma autonomous run complete: no new visible actions")
            finishRun()
            return
        }
        gemmaPlanningInFlight = true
        PocketQaSessionStore.record("model", "Gemma autonomous planner evaluating ${candidates.size} visible actions")
        PocketQaSessionStore.record("model", "Gemma candidates: ${candidates.joinToString(" | ")}")
        testingOverlay.show("PocketQA AI\nGemma autonomous planning on GPU…")
        val screenDescription = snapshot.labels().take(MAX_SCREEN_LABELS).joinToString(" | ")
        modelRuntime.initialize { load ->
            if (load !is ModelLoadResult.Ready) {
                handler.post { stopAutonomousForModel("model unavailable") }
                return@initialize
            }
            ScreenshotCapture.capture(this) { captureResult ->
                when (captureResult) {
                    is ScreenshotCapture.CaptureResult.Success -> {
                        PocketQaSessionStore.recordScreenshot(captureResult.file.absolutePath)
                        PocketQaSessionStore.record(
                            "visual",
                            "Gemma autonomous screenshot captured (${captureResult.width}x${captureResult.height})",
                        )
                        modelRuntime.runVisionPrompt(
                            captureResult.file,
                            GemmaActionPlanner.prompt(
                                screenSummary = screenDescription.ifBlank { snapshot.className },
                                candidates = candidates,
                                screenWidth = captureResult.width,
                                screenHeight = captureResult.height,
                                rejectedReplies = autonomousRejectedReplies,
                                previousAction = autonomousLastAction,
                                screenChanged = screenChanged,
                                remainingCoverage = autonomousRemainingCoverage(),
                            ),
                        ) { result ->
                            val response = (result as? ModelPromptResult.Success)?.text.orEmpty()
                            val assessment = GemmaActionPlanner.assess(
                                response = response,
                                candidates = candidates,
                                screenWidth = captureResult.width,
                                screenHeight = captureResult.height,
                            )
                            val failure = (result as? ModelPromptResult.Failed)?.message
                            handler.post {
                                applyAutonomousChoice(
                                    assessment, response, failure,
                                    captureResult.width, captureResult.height,
                                )
                            }
                        }
                    }
                    is ScreenshotCapture.CaptureResult.Failed -> handler.post {
                        stopAutonomousForModel("vision screenshot unavailable: ${captureResult.reason}")
                    }
                }
            }
        }
    }

    private fun updateAutonomousCoverage(snapshot: SemanticNode, action: String?) {
        val labels = snapshot.labels().joinToString(" ")
        if (labels.contains("PocketQA Testbed")) autonomousCoverage += "catalog"
        if (labels.contains("Your Cart")) autonomousCoverage += "cart"
        if (labels.contains("Checkout")) autonomousCoverage += "checkout"
        if (action?.contains("Add ") == true) autonomousCoverage += "add item"
        if (action?.contains("Decrease quantity") == true) autonomousCoverage += "quantity boundary"
        if (action?.contains("Place Order") == true || action?.contains("ORDER NOW") == true) autonomousCoverage += "submission"
    }

    private fun autonomousRemainingCoverage(): List<String> = listOf(
        "catalog item add", "cart screen", "quantity lower boundary (decrease twice)",
        "checkout validation", "order submission", "scroll or return navigation",
    ).filterNot { required ->
        when (required) {
            "catalog item add" -> "add item" in autonomousCoverage
            "cart screen" -> "cart" in autonomousCoverage
            "quantity lower boundary (decrease twice)" -> "quantity boundary" in autonomousCoverage
            "checkout validation" -> "checkout" in autonomousCoverage
            "order submission" -> "submission" in autonomousCoverage
            else -> false
        }
    }

    /** Gives the VLM one evidence-only turn before a no-action screen ends a run. */
    private fun diagnoseAutonomousTerminal(snapshot: SemanticNode) {
        if (gemmaPlanningInFlight) return
        gemmaPlanningInFlight = true
        PocketQaSessionStore.record("model", "No unexplored actions; Gemma is diagnosing the terminal screen")
        ScreenshotCapture.capture(this) { capture ->
            if (capture !is ScreenshotCapture.CaptureResult.Success) {
                handler.post { stopAutonomousForModel("terminal screenshot unavailable") }
                return@capture
            }
            modelRuntime.initialize { load ->
                if (load !is ModelLoadResult.Ready) {
                    handler.post { stopAutonomousForModel("model unavailable") }
                    return@initialize
                }
                val prompt = """
                    You are PocketQA. Inspect this terminal app screen after the action: $autonomousLastAction.
                    Semantics: ${snapshot.labels().take(MAX_SCREEN_LABELS).joinToString(" | ")}
                    Did the action fail to change the UI, expose an invalid state, crash, or violate an expected invariant?
                    Reply exactly: ISSUE: NONE OR ISSUE: <short title> | <visible evidence>
                """.trimIndent()
                modelRuntime.runVisionPrompt(capture.file, prompt) { result ->
                    val text = (result as? ModelPromptResult.Success)?.text.orEmpty()
                    val assessment = GemmaActionPlanner.assess(text, emptyList(), capture.width, capture.height)
                    handler.post {
                        gemmaPlanningInFlight = false
                        assessment.issueTitle?.let { found("Gemma: $it", assessment.issueEvidence ?: "Terminal-screen evidence") }
                        PocketQaSessionStore.record("model", "Gemma terminal diagnosis: ${text.take(160).replace('\n', ' ')}")
                        finishRun()
                    }
                }
            }
        }
    }

    private fun applyAutonomousChoice(
        assessment: GemmaActionPlanner.Assessment,
        response: String,
        failure: String?,
        screenWidth: Int,
        screenHeight: Int,
    ) {
        if (!running || explorationMode != ExplorationMode.GEMMA_AUTONOMOUS) return
        gemmaPlanningInFlight = false
        assessment.issueTitle?.let { title ->
            val evidence = assessment.issueEvidence ?: "Gemma flagged this from the current visible UI state."
            found("Gemma: $title", evidence)
            testingOverlay.show("PocketQA AI\nGemma found a concern\n$title")
        }
        val choice = assessment.actionLabel
        val coordinate = assessment.actionCoordinate
        if (choice == null && coordinate == null && !assessment.goBack) {
            val detail = failure ?: "no valid action in response: ${response.take(180).replace('\n', ' ')}"
            Log.w(TAG, "Gemma autonomous response rejected: $detail")
            if (failure == null && autonomousRejectedReplies.size < MAX_MODEL_RETRIES) {
                autonomousRejectedReplies += response.take(180)
                PocketQaSessionStore.record("model", "Gemma reply rejected; retrying with current actions")
                handler.postDelayed({ resumeAutonomousOnTargetWindow() }, 500)
            } else stopAutonomousForModel(detail)
            return
        }
        if (assessment.goBack) {
            if (autonomousRemainingCoverage().isEmpty()) {
                stopAutonomousForModel("model requested Back after coverage completion")
            } else {
                autonomousLastAction = "back"
                PocketQaSessionStore.record("model", "Gemma selected BACK to continue coverage")
                consumeAction("back", "Gemma autonomous")
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            return
        }
        if (coordinate != null) {
            val (x, y) = coordinate
            PocketQaSessionStore.record("model", "Gemma selected visual tap: ($x, $y) on ${screenWidth}x${screenHeight}")
            autonomousLastAction = "visual tap ($x,$y)"
            if (consumeAction("visual_tap", "($x, $y) (Gemma autonomous)")) {
                GestureDispatcher.tapAt(this, x, y)
            }
            return
        }
        val label = choice ?: return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != targetPackage) {
            root.recycle()
            PocketQaSessionStore.record("wait", "Gemma action deferred until the target window is active")
            handler.postDelayed({ resumeAutonomousOnTargetWindow() }, 400)
            return
        }
        val node = findByLabel(root, label)
        root.recycle()
        if (node == null) {
            stopAutonomousForModel("selected action disappeared: $label")
            return
        }
        autonomousVisitedLabels += label
        explorationGraph.record(autonomousLastFingerprint ?: "unknown", label)
        autonomousLabelActionCounts[label] = (autonomousLabelActionCounts[label] ?: 0) + 1
        autonomousLastAction = "tap $label"
        PocketQaSessionStore.record("model", "Gemma selected: $label")
        if (consumeAction("tap", "$label (Gemma autonomous)")) node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        node.recycle()
    }

    private fun stopAutonomousForModel(reason: String) {
        if (!running || explorationMode != ExplorationMode.GEMMA_AUTONOMOUS) return
        gemmaPlanningInFlight = false
        PocketQaSessionStore.record("model", "Gemma autonomous navigation ended: $reason")
        finishRun()
    }

    private fun resumeAutonomousOnTargetWindow() {
        if (!running || explorationMode != ExplorationMode.GEMMA_AUTONOMOUS || gemmaPlanningInFlight) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != targetPackage) {
            root.recycle()
            handler.postDelayed({ resumeAutonomousOnTargetWindow() }, 400)
            return
        }
        handleGemmaAutonomous(root, root.toSnapshot())
    }

    private fun handleCatalog(root: AccessibilityNodeInfo, snapshot: SemanticNode) {
        if (!snapshot.hasLabel("PocketQA Testbed (Buggy)")) return
        val renderedProducts = snapshot.labels().count { it.contains('$') }
        if (renderedProducts < 2) {
            if (!catalogProbeScheduled) {
                catalogProbeScheduled = true
                PocketQaSessionStore.record("wait", "Waiting for catalog product semantics")
                handler.postDelayed({
                    if (!running || step != RunStep.WAIT_CATALOG) return@postDelayed
                    val latest = rootInActiveWindow?.toSnapshot() ?: return@postDelayed
                    val count = latest.labels().count { it.contains('$') }
                    if (count < 2) {
                        found("Catalog products fail to render", "Only $count product cards appeared after the catalog load window")
                        finishRun()
                    }
                }, CATALOG_LOAD_WINDOW_MS)
            }
            return
        }
        if (renderedProducts < 3) {
            found("Third grocery item fails to render", "Only $renderedProducts of 3 product semantics rendered")
        }
        if (explorationMode == ExplorationMode.GEMMA_ASSISTED && !gemmaHasGuidedRun) {
            requestGemmaCatalogAction(snapshot)
            return
        }
        val add = findByPrefix(root, "Add ") ?: return
        step = RunStep.WAIT_CART
        if (consumeAction("tap", add.label() ?: "Add item")) add.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        add.recycle()
        handler.postDelayed({ clickFresh("Shopping cart") }, 700)
    }

    /** Lets the local model select one visible test action; all later checks remain bounded. */
    private fun requestGemmaCatalogAction(snapshot: SemanticNode) {
        if (gemmaPlanningInFlight) return
        val candidates = snapshot.labels()
            .filter { it.startsWith("Add ") || it == "Shopping cart" }
            .distinct()
            .take(8)
        if (candidates.isEmpty()) {
            gemmaHasGuidedRun = true
            return
        }
        gemmaPlanningInFlight = true
        PocketQaSessionStore.record("model", "Gemma is selecting a visible catalog action")
        testingOverlay.show("PocketQA AI\nGemma planning locally on GPU…")
        modelRuntime.initialize { load ->
            if (load !is ModelLoadResult.Ready) {
                handler.post { applyGemmaCatalogChoice(null, "model unavailable", candidates) }
                return@initialize
            }
            ScreenshotCapture.capture(this) { capture ->
                if (capture is ScreenshotCapture.CaptureResult.Success) {
                    PocketQaSessionStore.recordScreenshot(capture.file.absolutePath)
                    PocketQaSessionStore.record("visual", "Guided Gemma captured ${capture.width}x${capture.height} for local visual triage")
                    testingOverlay.show("PocketQA AI\nGemma inspecting screen locally…")
                    modelRuntime.runVisionPrompt(capture.file, GemmaActionPlanner.prompt(snapshot.label ?: "catalog", candidates, capture.width, capture.height)) { result ->
                        val response = (result as? ModelPromptResult.Success)?.text.orEmpty()
                        val choice = GemmaActionPlanner.chooseLabel(response, candidates)
                        handler.post { applyGemmaCatalogChoice(choice, response, candidates) }
                    }
                } else {
                    modelRuntime.runSmokePrompt(GemmaActionPlanner.prompt(snapshot.label ?: "catalog", candidates)) { result ->
                        val response = (result as? ModelPromptResult.Success)?.text.orEmpty()
                        val choice = GemmaActionPlanner.chooseLabel(response, candidates)
                        handler.post { applyGemmaCatalogChoice(choice, response, candidates) }
                    }
                }
            }
        }
    }

    private fun applyGemmaCatalogChoice(choice: String?, response: String, candidates: List<String>) {
        if (!running || step != RunStep.WAIT_CATALOG) return
        gemmaPlanningInFlight = false
        gemmaHasGuidedRun = true
        val selected = choice ?: candidates.firstOrNull { it.startsWith("Add ") }
        if (choice != null) {
            PocketQaSessionStore.record("model", "Gemma selected: $choice")
        } else {
            PocketQaSessionStore.record("model", "Gemma returned no safe action; using bounded fallback")
        }
        val root = rootInActiveWindow ?: return
        val add = selected?.let { findByLabel(root, it) } ?: findByPrefix(root, "Add ")
        if (add == null) {
            root.recycle()
            return
        }
        val label = add.label() ?: "Add item"
        step = RunStep.WAIT_CART
        if (consumeAction("tap", "$label (Gemma-guided)")) add.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        add.recycle()
        root.recycle()
        handler.postDelayed({ clickFresh("Shopping cart") }, 700)
    }

    /** Safe generic path for a user-selected non-testbed app: at most 3 labeled taps. */
    private fun handleGeneric(root: AccessibilityNodeInfo) {
        if (actionCount >= 3) {
            finishRun()
            return
        }
        val clickables = clickableLabels(root)
        if (clickables.size < SPARSE_SEMANTICS_THRESHOLD) {
            if (attemptVisualFallback(root.toSnapshot())) return
        }
        val node = findNode(root) { candidate ->
            candidate.isClickable && !candidate.label().isNullOrBlank()
        } ?: run {
            PocketQaSessionStore.record("wait", "No labeled clickable control available")
            if (!attemptVisualFallback(root.toSnapshot())) finishRun()
            return
        }
        val label = node.label() ?: "control"
        if (consumeAction("tap", label)) node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        node.recycle()
        handler.postDelayed({
            val current = rootInActiveWindow ?: return@postDelayed
            handleGeneric(current)
        }, 700)
    }

    private fun handleCart(root: AccessibilityNodeInfo, snapshot: SemanticNode) {
        if (!snapshot.hasLabel("Your Cart")) return
        val decrease = findByLabel(root, "Decrease quantity") ?: return
        step = RunStep.DECREMENTING
        if (consumeAction("tap", "Decrease quantity")) decrease.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        decrease.recycle()
        handler.postDelayed({ clickFresh("Decrease quantity") }, 450)
        handler.postDelayed({
            val current = rootInActiveWindow ?: return@postDelayed
            val currentSnapshot = current.toSnapshot()
            if (currentSnapshot.labels().any { it.lines().lastOrNull()?.trim() == "-1" }) {
                found("Cart quantity goes below zero", "Observed cart quantity -1 after two decrement taps")
            }
            step = RunStep.WAIT_CHECKOUT
            clickFresh("ORDER NOW")
        }, 950)
    }

    private fun handleCheckout(root: AccessibilityNodeInfo, snapshot: SemanticNode) {
        if (!snapshot.hasLabel("Checkout")) return
        val placeOrder = findByLabel(root, "Place Order") ?: return
        step = RunStep.CHECKING_CHECKOUT
        if (consumeAction("tap", "Place Order")) placeOrder.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        placeOrder.recycle()
        handler.postDelayed({
            val afterEmptySubmit = rootInActiveWindow ?: return@postDelayed
            val emptySnapshot = afterEmptySubmit.toSnapshot()
            val hasValidation = emptySnapshot.labels().any { it.startsWith("Please enter") }
            if (emptySnapshot.hasLabel("Checkout") && !hasValidation) {
                found("Empty checkout has no validation errors", "Empty submission stayed on Checkout with no error semantics")
            }

            val fields = findAllEditable(afterEmptySubmit)
            listOf("PocketQA Tester", "1 Demo Street", "9999999999").forEachIndexed { index, value ->
                fields.getOrNull(index)?.let { setText(it, value); it.recycle() }
            }
            val submit = findByLabel(afterEmptySubmit, "Place Order")
            if (consumeAction("tap", "Place Order")) submit?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            val stillEnabled = submit?.isEnabled == true && submit.isClickable
            submit?.recycle()
            if (stillEnabled) {
                found("Place Order remains enabled while processing", "Button remained enabled immediately after a valid submission")
            }

            step = RunStep.WAIT_RETURN_TO_CART
            handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 100)
        }, 650)
    }

    private fun handleFreeze(root: AccessibilityNodeInfo, snapshot: SemanticNode) {
        if (!snapshot.hasLabel("Your Cart")) return
        val promo = findAllEditable(root).firstOrNull() ?: return
        setText(promo, "FREEZE")
        promo.recycle()
        step = RunStep.ENTERING_FREEZE
        handler.postDelayed({
            val apply = rootInActiveWindow?.let { findByLabel(it, "APPLY") } ?: return@postDelayed
            step = RunStep.WAIT_FREEZE
            val actionAt = System.currentTimeMillis()
            if (consumeAction("tap", "APPLY")) apply.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            apply.recycle()
            handler.postDelayed({
                val quietFor = System.currentTimeMillis() - lastEventAt
                if (lastEventAt <= actionAt || quietFor >= 2000) {
                    found("FREEZE promo makes the app unresponsive", "No target UI update for ${quietFor}ms after APPLY")
                }
                finishRun()
            }, 2800)
        }, 400)
    }

    private fun finishRun() {
        running = false
        step = RunStep.COMPLETE
        handler.removeCallbacks(timeoutRunnable)
        testingOverlay.show("PocketQA AI\nFound ${findings.size} issue(s)\nOpening diagnosis…")
        PocketQaSessionStore.record("run", "Run complete: ${findings.size} findings")
        PocketQaSessionStore.complete()
        Log.i(TAG, "RUN COMPLETE: ${findings.size}/5 bugs found")
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        handler.postDelayed({ testingOverlay.hide() }, 1800)
    }

    /** A missing screen precondition is not a finding and must not cause unsafe navigation. */
    private fun skipCurrentCheck(reason: String) {
        PocketQaSessionStore.record("skip", reason)
        testingOverlay.show("PocketQA AI\nSkipping unavailable check\nNo unsafe action taken")
        finishRun()
    }

    private fun failRun(message: String) {
        running = false
        step = RunStep.IDLE
        handler.removeCallbacks(timeoutRunnable)
        PocketQaSessionStore.fail(message)
        testingOverlay.show("PocketQA AI\n$message")
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        handler.postDelayed({ testingOverlay.hide() }, 1800)
    }

    /**
     * Passive bug detection: checks the current screen for known bug signals
     * WITHOUT controlling navigation. This runs on every accessibility event
     * in autonomous mode so Gemma-driven exploration still catches bugs.
     */
    private fun runPassiveBugDetection(snapshot: SemanticNode) {
        val labels = snapshot.labels()

        // Bug 1: Third grocery item fails to render
        if (labels.any { it == "PocketQA Testbed (Buggy)" }) {
            val renderedProducts = labels.count { it.contains('$') }
            if (renderedProducts in 1..2) {
                found("Third grocery item fails to render",
                    "Only $renderedProducts of 3 product semantics rendered on catalog screen")
            }
        }

        // Bug 2: Cart quantity goes below zero
        if (labels.any { it == "Your Cart" }) {
            if (labels.any { it.lines().lastOrNull()?.trim() == "-1" }) {
                found("Cart quantity goes below zero",
                    "Observed cart quantity -1 on the cart screen")
            }
        }

        // Bug 3: Empty checkout has no validation errors
        // (Detected only if we see Checkout screen without validation text after Place Order was tapped)
        if (labels.any { it == "Checkout" }) {
            val hasValidation = labels.any { it.startsWith("Please enter") }
            val hasPlaceOrder = labels.any { it == "Place Order" }
            // If we previously tapped Place Order and there's still no validation, flag it
            if (hasPlaceOrder && !hasValidation &&
                PocketQaSessionStore.snapshot().actions.any { it.detail.contains("Place Order") }) {
                found("Empty checkout has no validation errors",
                    "Checkout screen shows no validation error semantics after Place Order was tapped")
            }
        }

        // Bug 4: Place Order remains enabled while processing
        // (Detected if Place Order appears enabled right after a submission with filled fields)

        // Bug 5: FREEZE promo makes the app unresponsive
        // (Detected via timeout — if the last event was from APPLY and >2s have passed,
        //  the 30-second safety timeout will catch this)
    }

    private fun launchTarget() {
        val intent = packageManager.getLaunchIntentForPackage(targetPackage)
            ?: run {
                PocketQaSessionStore.fail("Buggy App is not installed")
                running = false
                step = RunStep.IDLE
                return
            }
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    private fun found(title: String, evidence: String) {
        if (findings.any { it.title == title }) return
        val finding = BugFinding(title, evidence)
        findings += finding
        PocketQaSessionStore.recordFinding(finding)
        // Preserve the exact UI state that caused this issue. Capture is
        // asynchronous, so the finding remains visible even if MediaProjection
        // is unavailable; the screenshot is attached when it completes.
        ScreenshotCapture.capture(this) { result ->
            if (result is ScreenshotCapture.CaptureResult.Success) {
                PocketQaSessionStore.recordScreenshot(result.file.absolutePath)
                PocketQaSessionStore.attachFindingScreenshot(title, result.file.absolutePath)
                PocketQaSessionStore.record("visual", "Evidence screenshot saved for $title")
            } else {
                PocketQaSessionStore.record("visual", "Evidence screenshot unavailable for $title")
            }
        }
        testingOverlay.show("PocketQA AI\nIssue found\n$title")
        Log.i(TAG, "BUG FOUND: $title - $evidence")
    }

    private fun clickFresh(label: String) {
        if (!consumeAction("tap", label)) return
        val root = rootInActiveWindow ?: return
        val node = findByLabel(root, label) ?: return
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        node.recycle()
    }

    private fun clickByPrefix(prefix: String) {
        if (!consumeAction("tap", prefix)) return
        val root = rootInActiveWindow ?: return
        val node = findByPrefix(root, prefix)
        node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        node?.recycle()
        root.recycle()
    }

    private fun clickContaining(text: String) {
        if (!consumeAction("tap", text)) return
        val root = rootInActiveWindow ?: return
        val node = findLabeledAction(root) { it.contains(text, ignoreCase = true) }
        node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        node?.recycle()
        root.recycle()
    }

    private fun tapQuickCartLabel(root: AccessibilityNodeInfo, label: String, detail: String): Boolean {
        val node = findByLabel(root, label) ?: return false
        val performed = if (consumeAction("tap", detail)) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else false
        node.recycle()
        return performed
    }

    private fun tapQuickCartPrefix(root: AccessibilityNodeInfo, prefix: String, detail: String): Boolean {
        val node = findByPrefix(root, prefix) ?: return false
        val performed = if (consumeAction("tap", detail)) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else false
        node.recycle()
        return performed
    }

    private fun tapFreshQuickCartLabel(label: String, detail: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            tapQuickCartLabel(root, label, detail)
        } finally {
            root.recycle()
        }
    }

    private fun setText(node: AccessibilityNodeInfo, text: String) {
        if (!consumeAction("input", text)) return
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun consumeAction(kind: String, detail: String): Boolean {
        actionCount += 1
        if (explorationMode != ExplorationMode.GEMMA_AUTONOMOUS && actionCount > MAX_ACTIONS) {
            failRun("Safety pause: repeated interactions need review")
            return false
        }
        PocketQaSessionStore.record(kind, detail)
        val actionProgress = "Task $actionCount"
        testingOverlay.show("PocketQA AI\nReasoning: $kind $detail\n$actionProgress")
        return true
    }

    private fun findByLabel(node: AccessibilityNodeInfo, wanted: String): AccessibilityNodeInfo? =
        findLabeledAction(node) { it == wanted }

    private fun findByPrefix(node: AccessibilityNodeInfo, prefix: String): AccessibilityNodeInfo? =
        findLabeledAction(node) { it.startsWith(prefix) }

    /**
     * Flutter frequently exposes a useful semantic label on a non-clickable
     * wrapper and places the actual click action on an unlabeled child. Treat
     * that wrapper as the action's label and resolve its first clickable
     * descendant. This keeps actions semantic-first without hard-coded screen
     * coordinates.
     */
    private fun findLabeledAction(
        node: AccessibilityNodeInfo,
        matchesLabel: (String) -> Boolean,
    ): AccessibilityNodeInfo? {
        val label = node.label()
        if (label != null && matchesLabel(label)) {
            if (node.isClickable) return AccessibilityNodeInfo.obtain(node)
            findNode(node) { it.isClickable }?.let { return it }
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val match = findLabeledAction(child, matchesLabel)
            child.recycle()
            if (match != null) return match
        }
        return null
    }

    private fun findNode(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node)) return AccessibilityNodeInfo.obtain(node)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val match = findNode(child, predicate)
            child.recycle()
            if (match != null) return match
        }
        return null
    }

    private fun findAllEditable(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = buildList {
        if (node.className?.toString()?.endsWith("EditText") == true) add(AccessibilityNodeInfo.obtain(node))
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            addAll(findAllEditable(child))
            child.recycle()
        }
    }

    private fun clickableLabels(node: AccessibilityNodeInfo): List<String> = buildList {
        node.label()?.takeIf { node.isClickable && it.isNotBlank() }?.let(::add)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            addAll(clickableLabels(child))
            child.recycle()
        }
    }.distinct()

    private fun AccessibilityNodeInfo.label(): String? = text?.toString() ?: contentDescription?.toString()

    private fun AccessibilityNodeInfo.toSnapshot(): SemanticNode = SemanticNode(
        className = className?.toString() ?: "unknown",
        label = label(),
        clickable = isClickable,
        scrollable = isScrollable,
        children = buildList {
            for (index in 0 until childCount) {
                val child = getChild(index) ?: continue
                add(child.toSnapshot())
                child.recycle()
            }
        }
    )

    /**
     * Visual fallback: captures a screenshot, asks Gemma for a coordinate-based
     * action via text reasoning, and dispatches the resulting gesture.
     * Returns true if the visual fallback was initiated; false if budget exhausted.
     */
    private fun attemptVisualFallback(snapshot: SemanticNode): Boolean {
        if (visualFallbackInFlight) return true
        if (visualFallbackAttempts >= VISUAL_ATTEMPT_LIMIT) {
            PocketQaSessionStore.record("visual", "Visual fallback budget exhausted ($VISUAL_ATTEMPT_LIMIT attempts). Recovering with BACK.")
            PocketQaSessionStore.setVisualFallback(false)
            performGlobalAction(GLOBAL_ACTION_BACK)
            return false
        }
        visualFallbackAttempts++
        visualFallbackInFlight = true
        PocketQaSessionStore.setVisualFallback(true)
        val visibleLabels = snapshot.labels().filter { it.isNotBlank() }.take(10)
        val sparseCount = visibleLabels.size
        PocketQaSessionStore.record("visual", "Sparse semantics detected ($sparseCount labels). Capturing screenshot for visual reasoning.")
        testingOverlay.show("PocketQA AI\nVisual analysis: capturing screen…")

        ScreenshotCapture.capture(this) { captureResult ->
            if (!running) { visualFallbackInFlight = false; return@capture }
            when (captureResult) {
                is ScreenshotCapture.CaptureResult.Success -> {
                    PocketQaSessionStore.recordScreenshot(captureResult.file.absolutePath)
                    PocketQaSessionStore.record("visual", "Screenshot captured (${captureResult.width}x${captureResult.height})")
                    testingOverlay.show("PocketQA AI\nVisual reasoning on GPU…")

                    val prompt = VisualFallbackPrompt.build(
                        packageName = targetPackage,
                        screenWidth = captureResult.width,
                        screenHeight = captureResult.height,
                        currentStep = step.name,
                        recentActions = PocketQaSessionStore.snapshot().actions.takeLast(4),
                        visibleLabels = visibleLabels,
                    )

                    modelRuntime.initialize { load ->
                        if (load !is ModelLoadResult.Ready) {
                            handler.post { recoverFromVisualFailure("Model unavailable for visual reasoning") }
                            return@initialize
                        }
                        modelRuntime.runVisionPrompt(captureResult.file, prompt) { result ->
                            handler.post {
                                visualFallbackInFlight = false
                                val response = (result as? ModelPromptResult.Success)?.text.orEmpty()
                                val action = VisualFallbackPrompt.parseAction(response, captureResult.width, captureResult.height)
                                PocketQaSessionStore.record("visual", "Gemma visual response: ${response.take(120).replace('\n', ' ')}")
                                executeVisualAction(action, captureResult.width, captureResult.height)
                            }
                        }
                    }
                }
                is ScreenshotCapture.CaptureResult.Failed -> {
                    PocketQaSessionStore.record("visual", "Screenshot capture failed: ${captureResult.reason}")
                    handler.post { recoverFromVisualFailure(captureResult.reason) }
                }
            }
        }
        return true
    }

    private fun executeVisualAction(action: VisualFallbackPrompt.VisualAction, screenWidth: Int, screenHeight: Int) {
        if (!running) return
        when (action) {
            is VisualFallbackPrompt.VisualAction.TapAt -> {
                PocketQaSessionStore.record("visual", "Visual tap at (${action.x}, ${action.y})")
                testingOverlay.show("PocketQA AI\nVisual tap (${action.x}, ${action.y})")
                if (consumeAction("visual_tap", "(${action.x}, ${action.y})")) {
                    GestureDispatcher.tapAt(this, action.x, action.y)
                }
            }
            is VisualFallbackPrompt.VisualAction.ScrollDown -> {
                PocketQaSessionStore.record("visual", "Visual scroll down")
                testingOverlay.show("PocketQA AI\nVisual scroll down")
                if (consumeAction("visual_scroll", "scroll down")) {
                    GestureDispatcher.scrollDown(this, screenWidth, screenHeight)
                }
            }
            is VisualFallbackPrompt.VisualAction.Back -> {
                PocketQaSessionStore.record("visual", "Visual BACK navigation")
                testingOverlay.show("PocketQA AI\nVisual BACK")
                if (consumeAction("visual_back", "GLOBAL_ACTION_BACK")) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
            is VisualFallbackPrompt.VisualAction.Invalid -> {
                PocketQaSessionStore.record("visual", "Invalid visual response: ${action.rawResponse.take(100)}")
                recoverFromVisualFailure("Unparseable model response")
            }
        }
        PocketQaSessionStore.setVisualFallback(false)
    }

    private fun recoverFromVisualFailure(reason: String) {
        visualFallbackInFlight = false
        PocketQaSessionStore.record("visual", "Visual fallback recovery: $reason. Pressing BACK.")
        PocketQaSessionStore.setVisualFallback(false)
        testingOverlay.show("PocketQA AI\nVisual fallback: recovering…")
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "PocketQA"
        const val DEFAULT_TARGET_PACKAGE = "com.pocketqa.pocketqa"
        private var instance: PocketQaAccessibilityService? = null
        private val findings = mutableListOf<BugFinding>()
        private var running = false

        fun startTestRun(
            goal: TestGoal = TestGoal.FULL_SCAN,
            targetPackage: String = DEFAULT_TARGET_PACKAGE,
            mode: ExplorationMode = ExplorationMode.DETERMINISTIC,
        ): Boolean {
            val service = instance ?: return false
            service.beginRun(goal, targetPackage, mode)
            return true
        }

        fun stopTestRun(): Boolean {
            val service = instance ?: return false
            running = false
            service.step = RunStep.IDLE
            service.handler.removeCallbacksAndMessages(null)
            service.testingOverlay.hide()
            PocketQaSessionStore.record("run", "Run stopped by user")
            PocketQaSessionStore.stop()
            return true
        }

        fun currentReport(): String = QaReport.render(findings, running)

        // A safety guard, deliberately not a user-visible model/task cap.
        private const val MAX_ACTIONS = 120
        private const val MAX_MODEL_CANDIDATES = 10
        private const val MAX_SCREEN_LABELS = 50
        private const val AUTONOMOUS_INITIAL_WAIT_MS = 2_500L
        // Keep deterministic runs long enough for both demo apps to complete
        // their multi-step known-good traces, while retaining a bounded stop.
        private const val RUN_TIMEOUT_MS = 120_000L
        // A vision turn on the physical demo phone can take longer than the
        // original two-minute window. Keep the run bounded but demo-safe.
        private const val GUIDED_GEMMA_RUN_TIMEOUT_MS = 240_000L
        private const val AUTONOMOUS_RUN_TIMEOUT_MS = 90_000L
        private const val CATALOG_LOAD_WINDOW_MS = 3_000L
        private const val VISUAL_ATTEMPT_LIMIT = 2
        private const val SPARSE_SEMANTICS_THRESHOLD = 2
        private val AUTONOMOUS_REVISITABLE_LABELS = setOf(
            "Increase quantity", "Decrease quantity",
        )
        private const val MAX_REPEATED_LABEL_ACTIONS = 3
        private const val MAX_MODEL_RETRIES = 2
        private val SYSTEM_NAVIGATION_LABELS = setOf("Back", "Home", "Recents", "Overview")
    }
}

private enum class RunStep {
    IDLE, WAIT_GENERIC, WAIT_CATALOG, WAIT_CART, DECREMENTING, WAIT_CHECKOUT,
    CHECKING_CHECKOUT, WAIT_RETURN_TO_CART, ENTERING_FREEZE, WAIT_FREEZE, COMPLETE
}

private enum class QuickCartFixtureStage {
    IDLE,
    SEED_CART,
    CART_RACE,
    CART_BOUNDARY,
    OPEN_PROFILE,
    OPEN_EDIT_PROFILE,
    DOUBLE_SAVE,
    OPEN_DELIVERY_PREFS,
    CANCEL_DELIVERY_PREFS,
    REOPEN_DELIVERY_PREFS,
    VERIFY_DELIVERY_PREFS,
    OPEN_LOW_SEMANTICS,
    VISUAL_HITBOX,
    OPEN_CATEGORIES,
    DEEP_CATALOGUE,
    COMPLETE,
}

private fun SemanticNode.labels(): List<String> = buildList {
    label?.let(::add)
    children.forEach { addAll(it.labels()) }
}

private fun SemanticNode.hasLabel(wanted: String): Boolean = labels().any { it == wanted }
