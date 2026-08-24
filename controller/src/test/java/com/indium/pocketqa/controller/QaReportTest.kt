package com.indium.pocketqa.controller

import org.junit.Assert.assertTrue
import org.junit.Test

class QaReportTest {
    @Test
    fun `report distinguishes detected bugs from pending checks`() {
        val report = QaReport.render(
            listOf(BugFinding("Cart quantity goes below zero", "Observed quantity -1")),
            running = true
        )

        assertTrue(report.contains("1/5 bugs found"))
        assertTrue(report.contains("● Cart quantity goes below zero"))
        assertTrue(report.contains("○ Third grocery item fails to render"))
    }
}
