package com.indium.pocketqa.controller

/** Restricts model suggestions to labels PocketQA can currently observe. */
object GemmaActionPlanner {
    data class Assessment(
        val actionLabel: String?,
        val issueTitle: String?,
        val issueEvidence: String?,
    )

    fun prompt(screenSummary: String, candidates: List<String>): String = """
        You are PocketQA, an offline Android UI test planner.
        Inspect the screen image, its Semantics summary, and every listed action
        for a reproducible UI, state, validation, or availability problem. Act
        like an exploratory tester: form a hypothesis, change one thing, then
        inspect the observed result on the next turn. Do not finish merely
        because the happy path works.

        Deliberately exercise numeric boundaries and state transitions. For a
        cart or quantity control, add an item, decrease it to its minimum, then
        try one further decrease; check that each displayed value and total never
        go below its minimum. Also test increase/decrease reversals, empty
        states, validation, retry, and navigation when their controls exist.
        Report an issue only when the image or observed UI text is evidence.
        Then choose one safe action that advances functional testing.
        Reply with exactly two lines:
        ISSUE: NONE  OR  ISSUE: <short title> | <visible evidence>
        TAP: <one label copied exactly from Available actions>
        Do not invent actions, do not explain, and do not use system settings.

        Screen: $screenSummary
        Available actions:
        ${candidates.joinToString("\n") { "- $it" }}
    """.trimIndent()

    fun chooseLabel(response: String, candidates: List<String>): String? {
        val proposed = Regex("(?i)TAP\\s*:\\s*[`\\\"]?([^`\\\"\\r\\n]+)")
            .find(response)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?: return null
        return candidates.firstOrNull { it.equals(proposed, ignoreCase = true) }
    }

    fun assess(response: String, candidates: List<String>): Assessment {
        val issue = Regex("(?im)^ISSUE\\s*:\\s*(.+)$")
            .find(response)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeUnless { it.equals("NONE", ignoreCase = true) }
        val parts = issue?.split('|', limit = 2)?.map(String::trim)
        return Assessment(
            actionLabel = chooseLabel(response, candidates),
            issueTitle = parts?.firstOrNull()?.takeUnless { it.isNullOrBlank() },
            issueEvidence = parts?.getOrNull(1)?.takeUnless { it.isBlank() },
        )
    }
}
