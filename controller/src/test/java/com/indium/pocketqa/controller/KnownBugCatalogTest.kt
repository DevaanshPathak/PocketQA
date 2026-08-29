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

        assertEquals("bug_app/bugged/lib/providers/cart_provider.dart", diagnosis.sourceKey)
    }
}
