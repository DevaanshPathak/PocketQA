package com.indium.pocketqa.controller

import android.content.Context
import java.io.File

object ModelInstallContract {
    const val repository = "litert-community/gemma-4-E4B-it-litert-lm"
    const val fileName = "gemma-4-E4B-it-gpu.litertlm"
    const val deviceRelativePath = "models/$fileName"

    // Private internal storage is intentional: Android 16 can show a shell-pushed
    // external file while denying the app access to it. The installer uses adb
    // run-as so the model is owned and readable by PocketQA itself.
    fun file(context: Context): File = File(context.filesDir, deviceRelativePath)
}
