package com.indium.pocketqa.controller

data class BugFinding(
    val title: String,
    val evidence: String,
    val sourceKey: String = "",
    val recommendation: String = "",
    /** Screenshot taken at the instant this finding was observed. */
    val screenshotPath: String? = null,
)

object QaReport {
    val expectedTitles = listOf(
        "Rapid cart quantity update race",
        "Rapid search request race",
        "Checkout total stale after cart modification",
        "Async product load after back navigation",
        "Quantity zero boundary failure",
        "Final list item off-by-one",
        "Filter changes dataset but not list state",
        "Profile edit controller reuse",
        "Rapid double save race",
        "Cancelled form mutates shared state",
        "Double place-order submission",
        "Checkout summary stale after cart edit",
        "Low-semantics visual hitbox mismatch",
        "Login authentication validation gap",
    )

    fun render(findings: List<BugFinding>, running: Boolean): String = buildString {
        append(if (running) "TESTING…" else "TEST REPORT")
        append("\n${findings.size}/${expectedTitles.size} bugs found\n\n")
        expectedTitles.forEach { title ->
            val finding = findings.find { it.title == title }
            append(if (finding == null) "○ " else "● ").append(title)
            finding?.let { append("\n   ").append(it.evidence) }
            appendLine()
        }
    }.trimEnd()
}
