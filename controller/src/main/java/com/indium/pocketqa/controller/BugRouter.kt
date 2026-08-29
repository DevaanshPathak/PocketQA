package com.indium.pocketqa.controller

enum class PatchRoute { LOCAL_GEMMA, OPENROUTER, NEEDS_CONFIGURATION }

object BugRouter {
    fun route(sourceChars: Int, files: Int, confidence: Double, cloudReady: Boolean = false): PatchRoute {
        // The on-device model has a deliberately small prompt window. Route before truncation
        // would make the evidence ambiguous and produce unsafe patches.
        val tooLarge = sourceChars > 6_000 || files > 3 || confidence < .55
        return when {
            !tooLarge -> PatchRoute.LOCAL_GEMMA
            cloudReady -> PatchRoute.OPENROUTER
            else -> PatchRoute.NEEDS_CONFIGURATION
        }
    }
}
