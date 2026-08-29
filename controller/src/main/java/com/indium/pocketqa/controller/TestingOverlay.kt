package com.indium.pocketqa.controller

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** A non-interactive status card rendered above the app under test. */
class TestingOverlay(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var card: LinearLayout? = null
    private var status: TextView? = null

    fun show(message: String) {
        val current = card
        if (current != null) {
            status?.text = message
            return
        }
        val text = TextView(service).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(28, 20, 28, 20)
            this.text = message
        }
        val view = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            addView(text)
            background = GradientDrawable().apply {
                setColor(Color.rgb(28, 36, 54))
                cornerRadius = 28f
                setStroke(2, Color.rgb(107, 205, 255))
            }
            elevation = 18f
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 28
            y = 120
        }
        runCatching { windowManager.addView(view, params) }
        card = view
        status = text
    }

    fun hide() {
        card?.let { view -> runCatching { windowManager.removeView(view) } }
        card = null
        status = null
    }
}
