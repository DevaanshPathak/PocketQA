package com.indium.pocketqa.controller

import android.content.Context
import java.io.File

object ModelInstallContract {
    const val repository = "litert-community/gemma-4-E4B-it-litert-lm"
    const val visionFileName = "gemma-4-E4B-it.litertlm"
    const val gpuFileName = "gemma-4-E4B-it-gpu.litertlm"
    const val fileName = gpuFileName
    const val deviceRelativePath = "models/$fileName"

    // Private internal storage is intentional: Android 16 can show a shell-pushed
    // external file while denying the app access to it. The installer uses adb
    // run-as so the model is owned and readable by PocketQA itself.
    /** Prefer the full multimodal artifact whenever it is installed. */
    fun file(context: Context): File {
        val vision = File(context.filesDir, "models/$visionFileName")
        return if (vision.isFile && vision.length() > 0) vision else File(context.filesDir, deviceRelativePath)
    }

    fun supportsVision(context: Context): Boolean = file(context).name == visionFileName
}
