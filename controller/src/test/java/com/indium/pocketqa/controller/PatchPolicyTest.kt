package com.indium.pocketqa.controller

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatchPolicyTest {
    @Test fun `accepts manifest scoped unified diff`() {
        assertTrue(PatchPolicy.validate("--- a/lib/cart.dart\n+++ b/lib/cart.dart\n@@ -1 +1 @@\n-a\n+b", setOf("lib/cart.dart")).valid)
    }
    @Test fun `rejects traversal and unknown files`() {
        assertFalse(PatchPolicy.validate("--- a/../../secret\n+++ b/../../secret\n", setOf("lib/cart.dart")).valid)
    }
}
