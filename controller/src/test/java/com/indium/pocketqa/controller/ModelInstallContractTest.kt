package com.indium.pocketqa.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelInstallContractTest {
    @Test
    fun `Gemma 4 E4B GPU model has a stable offline install contract`() {
        assertEquals(
            "litert-community/gemma-4-E4B-it-litert-lm",
            ModelInstallContract.repository,
        )
        assertEquals(
            "gemma-4-E4B-it-gpu.litertlm",
            ModelInstallContract.fileName,
        )
        assertTrue(ModelInstallContract.deviceRelativePath.startsWith("models/"))
    }

    @Test
    fun `full E4B artifact is the required vision model`() {
        assertEquals("gemma-4-E4B-it.litertlm", ModelInstallContract.visionFileName)
        assertTrue(ModelInstallContract.visionFileName.endsWith(".litertlm"))
    }
}
