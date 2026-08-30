package com.indium.pocketqa.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceCorpusContractTest {
    @Test
    fun `maps only approved Buggy App source keys to bundled assets`() {
        val expected = mapOf(
            "lib/providers/cart_provider.dart" to "sources/providers/cart_provider.dart",
            "lib/ui/screens/profile/edit_profile_screen.dart" to "sources/ui/screens/profile/edit_profile_screen.dart",
            "lib/ui/screens/settings/delivery_preferences_screen.dart" to "sources/ui/screens/settings/delivery_preferences_screen.dart",
            "lib/ui/screens/experimental/low_semantics_screen.dart" to "sources/ui/screens/experimental/low_semantics_screen.dart",
            "lib/ui/screens/category/category_screen.dart" to "sources/ui/screens/category/category_screen.dart",
        )

        expected.forEach { (sourceKey, assetPath) ->
            assertEquals(assetPath, SourceCorpusContract.assetFor(sourceKey))
        }
        assertNull(SourceCorpusContract.assetFor("../secrets.dart"))
        assertNull(SourceCorpusContract.assetFor("lib/unknown.dart"))
    }
}
