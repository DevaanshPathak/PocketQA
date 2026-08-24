package com.indium.pocketqa.controller

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var report: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "PocketQA"
        val heading = TextView(this).apply {
            text = "PocketQA\nDeterministic real-device bug scan"
            textSize = 24f
            setTextColor(Color.rgb(25, 25, 35))
        }
        val enable = Button(this).apply {
            text = "Enable Accessibility Service"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val run = Button(this).apply {
            text = "Open buggy app and run 5 tests"
            setOnClickListener {
                if (!PocketQaAccessibilityService.startTestRun()) {
                    report.text = "Enable PocketQA Semantics Reader first."
                }
            }
        }
        report = TextView(this).apply {
            text = PocketQaAccessibilityService.currentReport()
            textSize = 16f
            setTextColor(Color.rgb(30, 30, 40))
            setPadding(0, 32, 0, 32)
        }
        setContentView(ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(48, 48, 48, 48)
                addView(heading)
                addView(enable)
                addView(run)
                addView(report)
            })
        })
    }

    override fun onResume() {
        super.onResume()
        if (::report.isInitialized) report.text = PocketQaAccessibilityService.currentReport()
    }
}
