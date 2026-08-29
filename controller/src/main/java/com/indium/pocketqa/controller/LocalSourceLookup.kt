package com.indium.pocketqa.controller

import android.content.Context

class LocalSourceLookup(context: Context) {
    private val assets = context.applicationContext.assets

    fun read(sourceKey: String): String? {
        val assetPath = SourceCorpusContract.assetFor(sourceKey) ?: return null
        return runCatching {
            assets.open(assetPath).bufferedReader().use { it.readText() }
        }.getOrNull()
    }
}
