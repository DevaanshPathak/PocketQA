package com.indium.pocketqa.controller

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = TextView(this).apply {
            text = "PocketQA Semantics Proof\n\n1. Enable the service\n2. Open PocketQA Testbed (Buggy)\n3. Watch it navigate to Checkout and scroll\n\nLogs: adb logcat -s PocketQA"
            textSize = 20f
            setTextColor(Color.rgb(25, 25, 35))
        }
        val enable = Button(this).apply {
            text = "Enable Accessibility Service"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            addView(title)
            addView(enable)
        })
    }
}
