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
        Inspect the screen and every listed visible action for a reproducible UI,
        state, validation, or availability problem. Then choose one safe action
        that advances functional testing.
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
