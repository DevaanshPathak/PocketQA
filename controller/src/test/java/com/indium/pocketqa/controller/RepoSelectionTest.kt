package com.indium.pocketqa.controller

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepoSelectionTest {
    @Test fun `uses a stable isolated binding file for each target app`() {
        assertTrue(RepoSelection.bindingFileName("com.pocketqa.pocketqa").startsWith("target-"))
        assertFalse(RepoSelection.bindingFileName("com.pocketqa.pocketqa") == RepoSelection.bindingFileName("com.example.other"))
    }

    @Test fun `rejects unsafe target package`() = assertFalse(RepoSelection.isSafePackageName("../../files"))

    @Test fun `accepts credential free https repository URL`() =
        assertTrue(RepoSelection.isSafeHttpsRepoUrl("https://github.com/acme/mobile.git"))

    @Test fun `rejects non https and embedded credentials`() {
        assertFalse(RepoSelection.isSafeHttpsRepoUrl("http://github.com/acme/mobile.git"))
        assertFalse(RepoSelection.isSafeHttpsRepoUrl("https://token@github.com/acme/mobile.git"))
    }

    @Test fun `accepts normal source subtree`() = assertTrue(RepoSelection.isSafeSubfolder("apps/mobile/lib"))
    @Test fun `rejects traversal and absolute subtree`() {
        assertFalse(RepoSelection.isSafeSubfolder("../../secret"))
        assertFalse(RepoSelection.isSafeSubfolder("/etc"))
        assertFalse(RepoSelection.isSafeSubfolder("C:\\Users"))
    }
}
