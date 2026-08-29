package com.indium.pocketqa.controller

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceRagIndexTest {
    @Test fun `retrieval ranks matching source chunks`() {
        val index = SourceRagIndex(listOf(
            SourceChunk("lib/cart.dart", 1, 20, "quantity decrement cart total"),
            SourceChunk("lib/login.dart", 1, 20, "email password authentication"),
        ))
        assertEquals("lib/cart.dart", index.search("negative cart quantity", 1).single().sourceKey)
    }
}
