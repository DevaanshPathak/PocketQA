package com.indium.pocketqa.controller

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import java.io.File
import java.io.FileOutputStream

/**
 * Captures the current screen using the AccessibilityService screenshot API (API 30+).
 * Screenshots are saved to app-private cache for session-scoped visual reasoning.
 */
object ScreenshotCapture {

    private const val TAG = "PocketQAScreenshot"
    private const val SCREENSHOT_DIR = "screenshots"
    private var captureCount = 0

    sealed interface CaptureResult {
        data class Success(val file: File, val width: Int, val height: Int) : CaptureResult
        data class Failed(val reason: String) : CaptureResult
    }

    /**
     * Captures a screenshot from the accessibility service and saves it as a PNG
     * in app-private cache. Calls [onResult] on the main thread.
     */
    fun capture(service: AccessibilityService, onResult: (CaptureResult) -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        val callback = object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                val hardwareBuffer = result.hardwareBuffer
                try {
                    val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, result.colorSpace)
                    if (bitmap == null) {
                        handler.post { onResult(CaptureResult.Failed("Failed to wrap HardwareBuffer into Bitmap")) }
                        return
                    }
                    val dir = File(service.cacheDir, SCREENSHOT_DIR).also { it.mkdirs() }
                    val file = File(dir, "capture_${++captureCount}_${System.currentTimeMillis()}.png")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                    }
                    val width = bitmap.width
                    val height = bitmap.height
                    bitmap.recycle()
                    Log.i(TAG, "Screenshot saved: ${file.absolutePath} (${width}x${height})")
                    handler.post { onResult(CaptureResult.Success(file, width, height)) }
                } catch (e: Exception) {
                    Log.e(TAG, "Screenshot processing failed", e)
                    handler.post { onResult(CaptureResult.Failed(e.message ?: "Unknown error")) }
                } finally {
                    hardwareBuffer.close()
                }
            }

            override fun onFailure(errorCode: Int) {
                Log.w(TAG, "Screenshot capture failed with error code: $errorCode")
                handler.post { onResult(CaptureResult.Failed("System screenshot error code $errorCode")) }
            }
        }

        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, callback)
        } catch (e: Exception) {
            Log.e(TAG, "takeScreenshot() threw", e)
            onResult(CaptureResult.Failed(e.message ?: "takeScreenshot exception"))
        }
    }

    /** Deletes all cached screenshots from previous runs. */
    fun clearCache(service: AccessibilityService) {
        val dir = File(service.cacheDir, SCREENSHOT_DIR)
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { it.delete() }
        }
        captureCount = 0
    }
}
