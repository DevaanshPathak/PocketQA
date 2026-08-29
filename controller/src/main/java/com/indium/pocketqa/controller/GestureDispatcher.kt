package com.indium.pocketqa.controller

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log

/**
 * Dispatches coordinate-based gestures through the AccessibilityService gesture API.
 * Used by the visual fallback when semantics-based node clicking is not possible.
 */
object GestureDispatcher {

    private const val TAG = "PocketQAGesture"
    private const val TAP_DURATION_MS = 50L
    private const val SWIPE_DURATION_MS = 400L

    /**
     * Dispatches a tap gesture at the given pixel coordinates.
     * Returns immediately; the gesture executes asynchronously.
     */
    fun tapAt(service: AccessibilityService, x: Int, y: Int, onComplete: (Boolean) -> Unit = {}) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val dispatched = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.i(TAG, "Tap gesture completed at ($x, $y)")
                onComplete(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Tap gesture cancelled at ($x, $y)")
                onComplete(false)
            }
        }, null)

        if (!dispatched) {
            Log.w(TAG, "dispatchGesture returned false for tap at ($x, $y)")
            onComplete(false)
        }
    }

    /**
     * Dispatches a vertical scroll-down swipe gesture from the center of the screen
     * downward by ~40% of the screen height.
     */
    fun scrollDown(service: AccessibilityService, screenWidth: Int, screenHeight: Int, onComplete: (Boolean) -> Unit = {}) {
        val centerX = screenWidth / 2f
        val startY = screenHeight * 0.65f
        val endY = screenHeight * 0.25f

        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, SWIPE_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val dispatched = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.i(TAG, "Scroll-down gesture completed")
                onComplete(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Scroll-down gesture cancelled")
                onComplete(false)
            }
        }, null)

        if (!dispatched) {
            Log.w(TAG, "dispatchGesture returned false for scroll-down")
            onComplete(false)
        }
    }
}
