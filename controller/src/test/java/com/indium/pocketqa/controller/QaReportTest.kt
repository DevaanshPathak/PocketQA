package com.indium.pocketqa.controller

import org.junit.Assert.assertTrue
import org.junit.Test

class QaReportTest {
    @Test
    fun `report distinguishes detected bugs from pending checks`() {
        val report = QaReport.render(
            listOf(BugFinding("Quantity zero boundary failure", "Observed quantity -1")),
            running = true
        )

        assertTrue(report.contains("1/6 bugs found"))
        assertTrue(report.contains("Quantity zero boundary failure"))
        assertTrue(report.contains("Rapid cart quantity update race"))
    }
}
