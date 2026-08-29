package com.indium.pocketqa.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetProfileTest {
    @Test fun `QuickCart screen selects its dedicated profile and repository defaults`() {
        val profile = TargetProfile.forScreen(
            packageName = TargetProfile.QUICK_CART_PACKAGE,
            labels = listOf("QuickCart", "Fresh Picks", "ADD"),
        )

        assertEquals(TargetProfile.Kind.QUICK_CART, profile.kind)
        assertEquals("https://github.com/DevaanshPathak/PocketQA.git", profile.repository.url)
        assertEquals("bug_app/bugged", profile.repository.subfolder)
        assertEquals(
            "bug_app/bugged",
            TargetProfile.defaultRepositoryFor(TargetProfile.QUICK_CART_PACKAGE)?.subfolder,
        )
    }

    @Test fun `unknown app remains generic`() {
        assertTrue(TargetProfile.forScreen("com.example.app", listOf("Settings")).kind == TargetProfile.Kind.GENERIC)
    }
}
