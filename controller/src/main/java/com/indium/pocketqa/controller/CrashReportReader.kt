package com.indium.pocketqa.controller

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONObject

data class CrashFrame(val sourceKey: String?, val line: Int?, val function: String?)

data class CrashReport(
    val id: String,
    val capturedAtMs: Long,
    val exceptionType: String,
    val message: String,
    val fatal: Boolean,
    val frames: List<CrashFrame>,
    val triggerHint: String?,
)

class CrashReportReader(private val contentResolver: ContentResolver) {
    private val latestUri: Uri = Uri.parse(CrashReportContract.latestUri)
    private var lastReportId: String? = null

    fun observe(onReport: (CrashReport) -> Unit): ContentObserver {
        return object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                readLatest()?.takeIf { it.id != lastReportId }?.let {
                    lastReportId = it.id
                    onReport(it)
                }
            }
        }.also { contentResolver.registerContentObserver(latestUri, false, it) }
    }

    fun readLatest(): CrashReport? {
        contentResolver.query(latestUri, arrayOf(CrashReportContract.reportColumn), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            return parse(cursor.getString(cursor.getColumnIndexOrThrow(CrashReportContract.reportColumn)))
        }
        return null
    }

    fun stopObserving(observer: ContentObserver) = contentResolver.unregisterContentObserver(observer)

    private fun parse(json: String): CrashReport? = runCatching {
        val root = JSONObject(json)
        val frames = root.optJSONArray("frames")
        val firstFrame = frames?.optJSONObject(0)
        val validation = CrashReportContract.validate(
            schemaVersion = root.optInt("schemaVersion"),
            id = root.optString("id"),
            appPackage = root.optString("appPackage"),
            sourceKey = firstFrame?.optString("sourceKey")?.takeIf { it.isNotBlank() },
            line = firstFrame?.takeIf { it.has("line") }?.optInt("line"),
        )
        if (!validation.isValid) return null
        CrashReport(
            id = root.getString("id"),
            capturedAtMs = root.getLong("capturedAtMs"),
            exceptionType = root.optString("exceptionType", "UnknownException"),
            message = root.optString("message"),
            fatal = root.optBoolean("fatal"),
            frames = buildList {
                for (index in 0 until (frames?.length() ?: 0)) {
                    val frame = frames!!.getJSONObject(index)
                    add(CrashFrame(frame.optString("sourceKey").takeIf { it.isNotBlank() }, frame.optInt("line").takeIf { it > 0 }, frame.optString("function").takeIf { it.isNotBlank() }))
                }
            },
            triggerHint = root.optString("triggerHint").takeIf { it.isNotBlank() },
        )
    }.getOrNull()
}
