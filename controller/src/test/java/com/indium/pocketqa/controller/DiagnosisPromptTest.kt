package com.indium.pocketqa.controller

import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosisPromptTest {
    @Test
    fun includesFindingTraceAndSource() {
        val prompt = DiagnosisPrompt.build(
            BugFinding("Cart quantity goes below zero", "Observed -1"),
            "lib/state/cart_provider.dart",
            "quantity -= 1;",
            listOf(ActionEvent(1, "tap", "Decrease quantity")),
        )

        assertTrue(prompt.contains("Observed -1"))
        assertTrue(prompt.contains("Decrease quantity"))
        assertTrue(prompt.contains("cart_provider.dart"))
    }
}
