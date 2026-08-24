package com.indium.pocketqa.controller

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class PocketQaAccessibilityService : AccessibilityService() {
    private var step = DemoStep.OPEN_CART
    private var lastTree: String? = null

    override fun onServiceConnected() {
        step = DemoStep.OPEN_CART
        lastTree = null
        Log.i(TAG, "Service connected; waiting for PocketQA Testbed (Buggy)")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != TARGET_PACKAGE) return
        val root = rootInActiveWindow ?: return
        val snapshot = root.toSnapshot()
        if (DemoPlanner.isCatalog(snapshot) && step != DemoStep.OPEN_CART) {
            Log.i(TAG, "Catalog relaunched; resetting demo state")
            step = DemoStep.OPEN_CART
        }
        if (step == DemoStep.COMPLETE) return
        val formatted = TreeFormatter.format(snapshot)
        if (formatted != lastTree) {
            Log.i(TAG, "Flutter Semantics tree (${step.name}):\n$formatted")
            lastTree = formatted
        }

        when (val action = DemoPlanner.next(snapshot, step)) {
            is DemoAction.Click -> {
                val node = findByLabel(root, action.label) ?: return
                Log.i(TAG, "ACTION ${step.name}: click label=\"${action.label}\"")
                step = step.next()
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                node.recycle()
            }
            DemoAction.ClickFirstEditable -> {
                val node = findFirstEditable(root) ?: return
                Log.i(TAG, "ACTION ${step.name}: click first semantic EditText")
                step = step.next()
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                node.recycle()
            }
            DemoAction.Scroll -> {
                step = DemoStep.COMPLETE
                val node = findScrollable(root)
                if (node != null) {
                    Log.i(TAG, "ACTION SCROLL_CHECKOUT: node ACTION_SCROLL_FORWARD class=${node.className}")
                    node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    node.recycle()
                } else {
                    Log.i(TAG, "ACTION SCROLL_CHECKOUT: dispatchGesture swipe fallback")
                    dispatchScrollGesture()
                }
                Log.i(TAG, "DEMO COMPLETE: real Flutter Semantics drove all actions")
            }
            null -> Unit
        }
    }

    override fun onInterrupt() = Unit

    private fun findByLabel(node: AccessibilityNodeInfo, wanted: String): AccessibilityNodeInfo? {
        val label = node.text?.toString() ?: node.contentDescription?.toString()
        if (label == wanted && node.isClickable) return AccessibilityNodeInfo.obtain(node)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val match = findByLabel(child, wanted)
            child.recycle()
            if (match != null) return match
        }
        return null
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return AccessibilityNodeInfo.obtain(node)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val match = findScrollable(child)
            child.recycle()
            if (match != null) return match
        }
        return null
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable && node.className?.toString()?.endsWith("EditText") == true) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val match = findFirstEditable(child)
            child.recycle()
            if (match != null) return match
        }
        return null
    }

    private fun dispatchScrollGesture() {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val path = Path().apply {
            moveTo(x, metrics.heightPixels * 0.75f)
            lineTo(x, metrics.heightPixels * 0.35f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 450))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun AccessibilityNodeInfo.toSnapshot(): SemanticNode {
        val childSnapshots = buildList {
            for (index in 0 until childCount) {
                val child = getChild(index) ?: continue
                add(child.toSnapshot())
                child.recycle()
            }
        }
        return SemanticNode(
            className = className?.toString() ?: "unknown",
            label = text?.toString() ?: contentDescription?.toString(),
            clickable = isClickable,
            scrollable = isScrollable,
            children = childSnapshots
        )
    }

    companion object {
        private const val TAG = "PocketQA"
        private const val TARGET_PACKAGE = "com.pocketqa.pocketqa"
    }
}

private fun DemoStep.next(): DemoStep = when (this) {
    DemoStep.OPEN_CART -> DemoStep.OPEN_CHECKOUT
    DemoStep.OPEN_CHECKOUT -> DemoStep.FOCUS_NAME
    DemoStep.FOCUS_NAME -> DemoStep.SCROLL_CHECKOUT
    DemoStep.SCROLL_CHECKOUT, DemoStep.COMPLETE -> DemoStep.COMPLETE
}
