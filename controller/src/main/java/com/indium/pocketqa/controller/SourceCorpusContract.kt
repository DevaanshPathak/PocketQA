package com.indium.pocketqa.controller

/** The intentionally small, known source corpus PocketQA ships for its testbed. */
object SourceCorpusContract {
    private val approvedKeys = setOf(
        "lib/providers/cart_provider.dart",
        "lib/ui/screens/profile/edit_profile_screen.dart",
        "lib/ui/screens/settings/delivery_preferences_screen.dart",
        "lib/ui/screens/experimental/low_semantics_screen.dart",
        "lib/ui/screens/category/category_screen.dart",
    )

    fun assetFor(sourceKey: String): String? = sourceKey
        .takeIf { it in approvedKeys }
        ?.removePrefix("lib/")
        ?.let { "sources/$it" }
}
