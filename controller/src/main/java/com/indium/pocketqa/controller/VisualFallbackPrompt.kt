package com.indium.pocketqa.controller

/**
 * Builds a structured text prompt for the text-only Gemma model when the screen
 * has sparse or absent accessibility semantics. Since Gemma 4 E4B is not a VLM,
 * we describe the screen context textually and ask for a coordinate-based action.
 */
object VisualFallbackPrompt {

    /** The action the model chose. */
    sealed interface VisualAction {
        data class TapAt(val x: Int, val y: Int) : VisualAction
        data object ScrollDown : VisualAction
        data object Back : VisualAction
        data class Invalid(val rawResponse: String) : VisualAction
    }

    fun build(
        packageName: String,
        screenWidth: Int,
        screenHeight: Int,
        currentStep: String,
        recentActions: List<ActionEvent>,
        visibleLabels: List<String>,
    ): String = """
        You are PocketQA, an offline Android QA agent.
        The current screen has very few accessible UI controls (sparse semantics).
        You must choose one safe action to advance testing.

        App: $packageName
        Screen size: ${screenWidth}x${screenHeight} pixels
        Current test step: $currentStep
        Recent actions:
        ${recentActions.takeLast(4).joinToString("\n") { "- ${it.kind}: ${it.detail}" }.ifBlank { "- none" }}
        Visible labels (if any): ${visibleLabels.take(10).joinToString(", ").ifBlank { "none detected" }}

        Choose exactly one action from these options:
        TAP: <x>,<y>   (pixel coordinates, must be within 0,0 to $screenWidth,$screenHeight)
        SCROLL_DOWN
        BACK

        Reply with only one line. Do not explain.
    """.trimIndent()

    /**
     * Parses the model response into a [VisualAction].
     * Accepts flexible formatting: "TAP: 540, 960", "TAP: 540,960", "SCROLL_DOWN", "BACK".
     */
    fun parseAction(response: String, screenWidth: Int, screenHeight: Int): VisualAction {
        val trimmed = response.lines()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: return VisualAction.Invalid(response)

        if (trimmed.equals("SCROLL_DOWN", ignoreCase = true)) return VisualAction.ScrollDown
        if (trimmed.equals("BACK", ignoreCase = true)) return VisualAction.Back

        val tapMatch = Regex("""(?i)TAP\s*:\s*(\d+)\s*,\s*(\d+)""").find(trimmed)
        if (tapMatch != null) {
            val x = tapMatch.groupValues[1].toIntOrNull() ?: return VisualAction.Invalid(trimmed)
            val y = tapMatch.groupValues[2].toIntOrNull() ?: return VisualAction.Invalid(trimmed)
            if (x in 0..screenWidth && y in 0..screenHeight) {
                return VisualAction.TapAt(x, y)
            }
            return VisualAction.Invalid("Coordinates out of bounds: $x,$y (screen ${screenWidth}x${screenHeight})")
        }

        return VisualAction.Invalid(trimmed)
    }
}
