package com.indium.pocketqa.controller

/** Selects a bounded trace from the target app's visible semantics. */
data class TargetProfile(val kind: Kind, val repository: RepoRequest) {
    enum class Kind { LEGACY_TESTBED, QUICK_CART, GENERIC }

    companion object {
        /** QuickCart now installs alongside the original mini demo app. */
        const val QUICK_CART_PACKAGE = "com.quickcart.buggyapp"

        private val quickCartRepository = RepoRequest(
            url = "https://github.com/DevaanshPathak/PocketQA.git",
            ref = "main",
            subfolder = "bug_app/bugged",
        )

        fun forScreen(packageName: String, labels: List<String>): TargetProfile = when {
            packageName == QUICK_CART_PACKAGE ->
                TargetProfile(Kind.QUICK_CART, quickCartRepository)
            labels.any { it.contains("QuickCart", ignoreCase = true) } ->
                TargetProfile(Kind.QUICK_CART, quickCartRepository)
            packageName == PocketQaAccessibilityService.DEFAULT_TARGET_PACKAGE ->
                TargetProfile(Kind.LEGACY_TESTBED, RepoRequest("", "main", ""))
            else -> TargetProfile(Kind.GENERIC, RepoRequest("", "main", ""))
        }

        fun defaultRepositoryFor(packageName: String) =
            if (packageName == QUICK_CART_PACKAGE) quickCartRepository else null
    }
}
