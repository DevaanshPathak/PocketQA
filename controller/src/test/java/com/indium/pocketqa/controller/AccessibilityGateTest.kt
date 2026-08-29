package com.indium.pocketqa.controller

import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityGateTest {
    @Test fun `explains that accessibility must be enabled before a scan`() {
        val message = AccessibilityGate.message("Buggy App")
        assertTrue(message.contains("Buggy App"))
        assertTrue(message.contains("Accessibility"))
        assertTrue(message.lowercase().contains("enable"))
    }
}
