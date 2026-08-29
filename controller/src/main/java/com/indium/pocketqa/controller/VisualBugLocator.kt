package com.indium.pocketqa.controller

import kotlin.math.min

/**
 * Prompts for locating buggy elements in screenshots using the vision model.
 */
object VisualBugLocator {

    /** Result of visual bug location. */
    sealed interface LocateResult {
        data class Success(
            val x: Int,
            val y: Int,
            val radius: Int,
            val confidence: Double,
            val reasoning: String
        ) : LocateResult
        data class Failed(val reason: String) : LocateResult
    }

    /**
     * Builds a prompt asking the vision model to locate a specific bug in the screenshot.
     */
    fun buildLocatePrompt(
        bugTitle: String,
        bugEvidence: String,
        screenWidth: Int,
        screenHeight: Int,
        visibleLabels: List<String> = emptyList()
    ): String = """
        You are PocketQA, an offline Android QA agent.
        A bug was detected but no source repository is linked for code-level diagnosis.
        Your task: locate the buggy UI element in this screenshot by pixel coordinates.

        Bug: $bugTitle
        Evidence: $bugEvidence
        Screen size: ${screenWidth}x${screenHeight} pixels
        Visible labels: ${visibleLabels.take(15).joinToString(", ").ifBlank { "none detected" }}

        Inspect the screenshot carefully. Identify the specific UI control, region, or element
        that corresponds to the bug evidence. Consider:
        - Cart quantity displays showing negative values
        - Missing validation error messages near form fields
        - Unresponsive buttons that should be disabled
        - Missing product cards in a catalog grid
        - Overlapping or misaligned UI elements

        Reply with exactly one line in this format:
        BUG_AT: <x>,<y>,<radius> | <confidence 0-1> | <brief reasoning>

        Examples:
        BUG_AT: 540,960,80 | 0.92 | Cart quantity shows -1 at bottom center
        BUG_AT: 320,640,60 | 0.85 | Place Order button enabled with empty fields
        BUG_AT: 400,400,100 | 0.78 | Third product card missing from grid

        Coordinates must be within 0,0 to $screenWidth,$screenHeight.
        Radius should cover the buggy element (typical 40-120).
        Do not explain beyond the required format.
    """.trimIndent()

    /**
     * Parses the model response into a [LocateResult].
     */
    fun parseLocateResponse(response: String, screenWidth: Int, screenHeight: Int): LocateResult {
        val trimmed = response.lines()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: return LocateResult.Failed("Empty response")

        val match = Regex("""(?i)BUG_AT\s*:\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\|\s*([\d.]+)\s*\|\s*(.+)""").find(trimmed)
        if (match == null) {
            return LocateResult.Failed("Unparseable format: $trimmed")
        }

        val x = match.groupValues[1].toIntOrNull()
        val y = match.groupValues[2].toIntOrNull()
        val radius = match.groupValues[3].toIntOrNull()
        val confidence = match.groupValues[4].toDoubleOrNull()
        val reasoning = match.groupValues[5].trim()

        if (x == null || y == null || radius == null || confidence == null) {
            return LocateResult.Failed("Invalid numeric values in: $trimmed")
        }

        if (x !in 0..screenWidth || y !in 0..screenHeight) {
            return LocateResult.Failed("Coordinates out of bounds: $x,$y (screen ${screenWidth}x${screenHeight})")
        }

        if (radius !in 10..min(screenWidth, screenHeight)) {
            return LocateResult.Failed("Invalid radius: $radius")
        }

        if (confidence !in 0.0..1.0) {
            return LocateResult.Failed("Invalid confidence: $confidence")
        }

        return LocateResult.Success(x, y, radius, confidence, reasoning)
    }
}