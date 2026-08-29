package com.indium.pocketqa.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceCorpusContractTest {
    @Test
    fun `maps only approved Buggy App source keys to bundled assets`() {
        assertEquals(
            "sources/ui/screens/catalog_screen.dart",
            SourceCorpusContract.assetFor("lib/ui/screens/catalog_screen.dart"),
        )
        assertEquals(
            "sources/state/cart_provider.dart",
            SourceCorpusContract.assetFor("lib/state/cart_provider.dart"),
        )
        assertNull(SourceCorpusContract.assetFor("../secrets.dart"))
        assertNull(SourceCorpusContract.assetFor("lib/unknown.dart"))
    }
}
