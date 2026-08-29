package com.indium.pocketqa.controller

/** The intentionally small, known source corpus PocketQA ships for its testbed. */
object SourceCorpusContract {
    private val approvedKeys = setOf(
        "lib/ui/screens/catalog_screen.dart",
        "lib/ui/screens/cart_screen.dart",
        "lib/ui/screens/checkout_screen.dart",
        "lib/state/cart_provider.dart",
        "lib/ui/widgets/cart_item_tile.dart",
    )

    fun assetFor(sourceKey: String): String? = sourceKey
        .takeIf { it in approvedKeys }
        ?.removePrefix("lib/")
        ?.let { "sources/$it" }
}
