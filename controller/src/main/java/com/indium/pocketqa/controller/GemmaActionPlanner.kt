package com.indium.pocketqa.controller

/** Restricts model suggestions to labels PocketQA can currently observe. */
object GemmaActionPlanner {
    data class Assessment(
        val actionLabel: String?,
        val actionCoordinate: Pair<Int, Int>?,
        val issueTitle: String?,
        val issueEvidence: String?,
    )

    fun prompt(
        screenSummary: String,
        candidates: List<String>,
        screenWidth: Int? = null,
        screenHeight: Int? = null,
        rejectedReplies: List<String> = emptyList(),
        previousAction: String? = null,
        screenChanged: Boolean? = null,
        remainingCoverage: List<String> = emptyList(),
    ): String = """
        You are PocketQA, an offline Android UI test planner.
        Inspect the screen image, its Semantics summary, and every listed action
        for a reproducible UI, state, validation, or availability problem. Act
        like an exploratory tester: form a hypothesis, change one thing, then
        inspect the observed result on the next turn. Do not finish merely
        because the happy path works.

        Your coverage charter is to explore the whole reachable app, not just
        the current screen. Before considering the run complete, cover catalog
        item actions, cart add/remove/quantity boundaries, checkout validation
        and submission, scrollable content, retry/error states, and navigation
        back to earlier screens when available. Prefer an unvisited screen or
        untried control over advancing the happy path. A terminal screen is an
        observation point, not proof that the app has been fully tested.
        Remaining required coverage: ${remainingCoverage.joinToString("; ").ifBlank { "all charter areas observed" }}.
        Choose an action that advances one remaining coverage item whenever possible.

        Deliberately exercise numeric boundaries and state transitions. For a
        cart or quantity control, add an item, decrease it to its minimum, then
        try one further decrease; check that each displayed value and total never
        go below its minimum. Also test increase/decrease reversals, empty
        states, validation, retry, and navigation when their controls exist.
        Report an issue only when the image or observed UI text is evidence.
        Previous action: ${previousAction ?: "none"}. Screen changed after it: ${screenChanged?.toString() ?: "unknown"}.
        If a previous tap should submit, navigate, or change a value but the screen did not change, report that as evidence.
        If an Available action starts with Add, choose it before opening a cart or checkout.
        The Available actions list is authoritative; never choose a label outside it.
        Then choose one safe action that advances functional testing.
        Reply with exactly two lines:
        ISSUE: NONE  OR  ISSUE: <short title> | <visible evidence>
        TAP: <one label copied exactly from Available actions>
        OR TAP_AT: <x>,<y> for a visible app control in the screenshot.
        ${if (screenWidth != null && screenHeight != null) "Coordinates must be within 0,0 to $screenWidth,$screenHeight." else ""}
        Do not tap Android system navigation (Back/Home/Recents), do not explain,
        and do not use system settings.

        Screen: $screenSummary
        Available actions:
        ${candidates.joinToString("\n") { "- $it" }}
        Rejected prior replies: ${rejectedReplies.takeLast(2).joinToString(" | ").ifBlank { "none" }}
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

    fun assess(
        response: String,
        candidates: List<String>,
        screenWidth: Int? = null,
        screenHeight: Int? = null,
    ): Assessment {
        val issue = Regex("(?im)^ISSUE\\s*:\\s*(.+)$")
            .find(response)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeUnless { it.equals("NONE", ignoreCase = true) }
        val parts = issue?.split('|', limit = 2)?.map(String::trim)
        val coordinate = Regex("(?im)^TAP_AT\\s*:\\s*(\\d+)\\s*,\\s*(\\d+)\\s*$")
            .find(response)
            ?.let { it.groupValues[1].toIntOrNull() to it.groupValues[2].toIntOrNull() }
            ?.takeIf { (x, y) ->
                screenWidth != null && screenHeight != null && x != null && y != null &&
                    x in 0..screenWidth && y in 0..screenHeight
            }
            ?.let { (x, y) -> x!! to y!! }
        return Assessment(
            actionLabel = chooseLabel(response, candidates),
            actionCoordinate = coordinate,
            issueTitle = parts?.firstOrNull()?.takeUnless { it.isNullOrBlank() },
            issueEvidence = parts?.getOrNull(1)?.takeUnless { it.isBlank() },
        )
    }
}
