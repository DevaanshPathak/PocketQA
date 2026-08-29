package com.indium.pocketqa.controller

/** Restricts model suggestions to labels PocketQA can currently observe. */
object GemmaActionPlanner {
    fun prompt(screenSummary: String, candidates: List<String>): String = """
        You are PocketQA, an offline Android UI test planner.
        Choose one safe, visible action that advances functional testing.
        Reply with exactly: TAP: <one label copied exactly from Available actions>.
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
}
