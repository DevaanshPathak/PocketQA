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
    private val timeoutRunnable = Runnable {
        if (running) failRun("Safety timeout: exploration stopped after 30 seconds")
    }

    override fun onServiceConnected() {
        instance = this
        testingOverlay = TestingOverlay(this)
        Log.i(TAG, "PocketQA test service connected")
    }

    override fun onDestroy() {
        if (::testingOverlay.isInitialized) testingOverlay.hide()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun beginRun(goal: TestGoal, packageName: String) {
        findings.clear()
        running = true
        actionCount = 0
        catalogProbeScheduled = false
        targetPackage = packageName
        step = if (packageName == DEFAULT_TARGET_PACKAGE) RunStep.WAIT_CATALOG else RunStep.WAIT_GENERIC
        PocketQaSessionStore.start(goal)
        PocketQaSessionStore.record("run", "Starting ${goal.title}")
        testingOverlay.show("PocketQA AI\nPlanning test trace…")
        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, RUN_TIMEOUT_MS)
        launchTarget()
        Log.i(TAG, "RUN STARTED: five deterministic bug checks")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!running || event?.packageName?.toString() != targetPackage) return
        lastEventAt = System.currentTimeMillis()
        val root = rootInActiveWindow ?: return
        val snapshot = root.toSnapshot()
        PocketQaSessionStore.record("observe", "${step.name}: ${snapshot.label ?: snapshot.className}")
        testingOverlay.show("PocketQA AI\nObserving ${snapshot.label ?: "screen"}\nStep ${actionCount + 1}/$MAX_ACTIONS")
        Log.d(TAG, "STATE ${step.name}:\n${TreeFormatter.format(snapshot)}")

        when (step) {
            RunStep.WAIT_CATALOG -> handleCatalog(root, snapshot)
            RunStep.WAIT_CART -> handleCart(root, snapshot)
            RunStep.WAIT_CHECKOUT -> handleCheckout(root, snapshot)
            RunStep.WAIT_RETURN_TO_CART -> handleFreeze(root, snapshot)
            RunStep.WAIT_GENERIC -> handleGeneric(root)
            else -> Unit
        }
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
        val add = findByPrefix(root, "Add ") ?: return
        step = RunStep.WAIT_CART
        if (consumeAction("tap", add.label() ?: "Add item")) add.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        add.recycle()
        handler.postDelayed({ clickFresh("Shopping cart") }, 700)
    }

    /** Safe generic path for a user-selected non-testbed app: at most 3 labeled taps. */
    private fun handleGeneric(root: AccessibilityNodeInfo) {
        if (actionCount >= 3) {
            finishRun()
            return
        }
        val node = findNode(root) { candidate ->
            candidate.isClickable && !candidate.label().isNullOrBlank()
        } ?: run {
            PocketQaSessionStore.record("wait", "No labeled clickable control available")
            finishRun()
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

    private fun failRun(message: String) {
        running = false
        step = RunStep.IDLE
        handler.removeCallbacks(timeoutRunnable)
        PocketQaSessionStore.fail(message)
        testingOverlay.show("PocketQA AI\n$message")
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        handler.postDelayed({ testingOverlay.hide() }, 1800)
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
        if (findings.none { it.title == title }) findings += BugFinding(title, evidence)
        PocketQaSessionStore.recordFinding(BugFinding(title, evidence))
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

    private fun setText(node: AccessibilityNodeInfo, text: String) {
        if (!consumeAction("input", text)) return
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun consumeAction(kind: String, detail: String): Boolean {
        actionCount += 1
        if (actionCount > MAX_ACTIONS) {
            failRun("Action budget reached ($MAX_ACTIONS)")
            return false
        }
        PocketQaSessionStore.record(kind, detail)
        testingOverlay.show("PocketQA AI\nReasoning: $kind $detail\nAction $actionCount/$MAX_ACTIONS")
        return true
    }

    private fun findByLabel(node: AccessibilityNodeInfo, wanted: String): AccessibilityNodeInfo? =
        findNode(node) { it.label() == wanted && it.isClickable }

    private fun findByPrefix(node: AccessibilityNodeInfo, prefix: String): AccessibilityNodeInfo? =
        findNode(node) { it.label()?.startsWith(prefix) == true && it.isClickable }

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
        ): Boolean {
            val service = instance ?: return false
            service.beginRun(goal, targetPackage)
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

        private const val MAX_ACTIONS = 20
        private const val RUN_TIMEOUT_MS = 30_000L
        private const val CATALOG_LOAD_WINDOW_MS = 3_000L
    }
}

private enum class RunStep {
    IDLE, WAIT_GENERIC, WAIT_CATALOG, WAIT_CART, DECREMENTING, WAIT_CHECKOUT,
    CHECKING_CHECKOUT, WAIT_RETURN_TO_CART, ENTERING_FREEZE, WAIT_FREEZE, COMPLETE
}

private fun SemanticNode.labels(): List<String> = buildList {
    label?.let(::add)
    children.forEach { addAll(it.labels()) }
}

private fun SemanticNode.hasLabel(wanted: String): Boolean = labels().any { it == wanted }
