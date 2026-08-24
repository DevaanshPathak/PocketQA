package com.indium.pocketqa.controller

data class BugFinding(val title: String, val evidence: String)

object QaReport {
    val expectedTitles = listOf(
        "Third grocery item fails to render",
        "Cart quantity goes below zero",
        "FREEZE promo makes the app unresponsive",
        "Empty checkout has no validation errors",
        "Place Order remains enabled while processing"
    )

    fun render(findings: List<BugFinding>, running: Boolean): String = buildString {
        append(if (running) "TESTING…" else "TEST REPORT")
        append("\n${findings.size}/5 bugs found\n\n")
        expectedTitles.forEach { title ->
            val finding = findings.find { it.title == title }
            append(if (finding == null) "○ " else "● ").append(title)
            finding?.let { append("\n   ").append(it.evidence) }
            appendLine()
        }
    }.trimEnd()
}
