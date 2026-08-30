package com.indium.pocketqa.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownBugCatalogTest {
    @Test
    fun negativeQuantityFindingMapsToLocalSourceAndDiff() {
        val diagnosis = KnownBugCatalog.diagnose(
            BugFinding("Cart quantity goes below zero", "Observed -1"),
        )

        assertEquals("lib/state/cart_provider.dart", diagnosis.sourceKey)
        assertTrue(diagnosis.diff.contains("clamp"))
    }

    @Test
    fun quickCartQuantityFindingMapsToQuickCartSource() {
        val diagnosis = KnownBugCatalog.diagnose(
            BugFinding("Cart quantity goes below zero", "QuickCart showed quantity -1"),
        )

        assertEquals("lib/providers/cart_provider.dart", diagnosis.sourceKey)
    }

    @Test
    fun primaryQuickCartFixturesMapToTheirOwnSourceFiles() {
        assertEquals(
            "lib/ui/screens/category/category_screen.dart",
            KnownBugCatalog.diagnose(BugFinding("Final list off-by-one", "QuickCart boundary")).sourceKey,
        )
        assertEquals(
            "lib/ui/screens/profile/edit_profile_screen.dart",
            KnownBugCatalog.diagnose(BugFinding("Rapid double save", "QuickCart profile")).sourceKey,
        )
        assertEquals(
            "lib/ui/screens/experimental/low_semantics_screen.dart",
            KnownBugCatalog.diagnose(BugFinding("Visual hitbox mismatch", "QuickCart Fresh Picks")).sourceKey,
        )
    }

    @Test
    fun `every live demo fixture maps to bundled source`() {
        val findings = listOf(
            BugFinding("Rapid cart quantity update race", "QuickCart cart"),
            BugFinding("Quantity zero boundary failure", "QuickCart showed quantity -1"),
            BugFinding("Rapid double save race", "QuickCart profile"),
            BugFinding("Cancelled form mutates shared state", "QuickCart preferences"),
            BugFinding("Low-semantics visual hitbox mismatch", "QuickCart Fresh Picks"),
            BugFinding("Final list item off-by-one", "QuickCart boundary"),
        )

        findings.forEach { finding ->
            val sourceKey = KnownBugCatalog.diagnose(finding).sourceKey
            assertTrue("Missing bundled source mapping for $sourceKey", SourceCorpusContract.assetFor(sourceKey) != null)
        }
    }
}
