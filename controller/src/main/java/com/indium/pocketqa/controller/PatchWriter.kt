package com.indium.pocketqa.controller

import android.content.Context
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File

object PatchWriter {
    fun save(context: Context, diagnosis: LocalDiagnosis): File {
        val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "patches")
        directory.mkdirs()
        return File(directory, "pocketqa-${System.currentTimeMillis()}.diff").apply {
            writeText(diagnosis.diff + "\n")
        }
    }

    fun uri(context: Context, file: File) = FileProvider.getUriForFile(
        context,
        "${context.packageName}.files",
        file,
    )
}
