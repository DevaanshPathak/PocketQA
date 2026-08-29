package com.indium.pocketqa.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportContractTest {
    @Test
    fun `accepts a version one report with a Dart source key`() {
        val validation = CrashReportContract.validate(
            schemaVersion = 1,
            id = "run-42",
            appPackage = "com.pocketqa.pocketqa",
            sourceKey = "lib/ui/screens/catalog_screen.dart",
            line = 84,
        )

        assertTrue(validation.isValid)
    }

    @Test
    fun `rejects reports from another app or unsafe source path`() {
        val wrongPackage = CrashReportContract.validate(1, "run-42", "com.example.other", "lib/a.dart", 1)
        val traversal = CrashReportContract.validate(1, "run-42", "com.pocketqa.pocketqa", "../secrets.dart", 1)

        assertFalse(wrongPackage.isValid)
        assertFalse(traversal.isValid)
        assertEquals("appPackage", wrongPackage.reason)
        assertEquals("sourceKey", traversal.reason)
    }
}
