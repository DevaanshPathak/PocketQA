package com.indium.pocketqa.controller

object AccessibilityGate {
    fun message(targetLabel: String): String =
        "PocketQA needs its Accessibility Service enabled before it can inspect and test $targetLabel. " +
            "Enable PocketQA Semantics Reader in Android Accessibility settings, then return here and start the scan."
}
