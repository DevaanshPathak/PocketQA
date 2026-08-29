package com.indium.pocketqa.controller

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Annotates screenshots with visual bug highlights.
 * Draws bounding boxes, circles, and labels at bug coordinates.
 */
object VisualBugHighlighter {

    private const val TAG = "VisualBugHighlighter"
    private const val HIGHLIGHT_COLOR = 0xFFFF3B30.toInt() // iOS red
    private const val HIGHLIGHT_STROKE_WIDTH = 6f
    private const val LABEL_BG_COLOR = 0xFFFF3B30.toInt()
    private const val LABEL_TEXT_COLOR = 0xFFFFFFFF.toInt()
    private const val LABEL_PADDING = 12
    private const val CORNER_RADIUS = 16f

    data class Highlight(
        val x: Int,
        val y: Int,
        val radius: Int = 80,
        val label: String = "BUG",
        val style: Style = Style.CIRCLE
    ) {
        enum class Style { CIRCLE, RECTANGLE, PULSE }
    }

    data class AnnotatedResult(
        val file: File,
        val width: Int,
        val height: Int,
        val highlightsApplied: Int
    )

    /**
     * Annotates a screenshot with bug highlights and saves it.
     * @param context App context for cache directory
     * @param sourceFile Original screenshot file
     * @param highlights List of highlights to draw
     * @param onResult Callback with annotated file or failure reason
     */
    fun annotate(
        context: Context,
        sourceFile: File,
        highlights: List<Highlight>,
        onResult: (AnnotationResult) -> Unit
    ) {
        if (!sourceFile.isFile || sourceFile.length() == 0L) {
            onResult(AnnotationResult.Failed("Source screenshot not found or empty"))
            return
        }
        if (highlights.isEmpty()) {
            onResult(AnnotationResult.Failed("No highlights provided"))
            return
        }

        // Load bitmap
        val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
        if (bitmap == null) {
            onResult(AnnotationResult.Failed("Failed to decode screenshot bitmap"))
            return
        }

        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        bitmap.recycle()

        val canvas = Canvas(mutableBitmap)
        val width = mutableBitmap.width
        val height = mutableBitmap.height

        // Paint for highlight shapes
        val highlightPaint = Paint().apply {
            color = HIGHLIGHT_COLOR
            style = Paint.Style.STROKE
            strokeWidth = HIGHLIGHT_STROKE_WIDTH
            isAntiAlias = true
        }

        // Paint for label background
        val labelBgPaint = Paint().apply {
            color = LABEL_BG_COLOR
            isAntiAlias = true
        }

        // Paint for label text
        val labelPaint = Paint().apply {
            color = LABEL_TEXT_COLOR
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        var applied = 0
        for (highlight in highlights) {
            val cx = highlight.x.coerceIn(0, width)
            val cy = highlight.y.coerceIn(0, height)
            val r = highlight.radius.coerceIn(20, minOf(width, height) / 2)

            when (highlight.style) {
                Highlight.Style.CIRCLE -> {
                    canvas.drawCircle(cx.toFloat(), cy.toFloat(), r.toFloat(), highlightPaint)
                    // Inner circle for emphasis
                    val innerPaint = Paint(highlightPaint).apply {
                        strokeWidth = 2f
                        color = Color.WHITE
                    }
                    canvas.drawCircle(cx.toFloat(), cy.toFloat(), (r * 0.6f).toFloat(), innerPaint)
                }
                Highlight.Style.RECTANGLE -> {
                    val rect = RectF(
                        (cx - r).toFloat(),
                        (cy - r).toFloat(),
                        (cx + r).toFloat(),
                        (cy + r).toFloat()
                    )
                    canvas.drawRoundRect(rect, CORNER_RADIUS, CORNER_RADIUS, highlightPaint)
                }
                Highlight.Style.PULSE -> {
                    // Draw multiple concentric circles for pulse effect
                    for (i in 0..2) {
                        val pulsePaint = Paint(highlightPaint).apply {
                            strokeWidth = HIGHLIGHT_STROKE_WIDTH - i * 1.5f
                            alpha = 255 - i * 60
                        }
                        canvas.drawCircle(cx.toFloat(), cy.toFloat(), (r * (1.0 + i * 0.3f)).toFloat(), pulsePaint)
                    }
                }
            }

            // Draw label above the highlight
            val labelText = highlight.label
            val textBounds = Rect()
            labelPaint.getTextBounds(labelText, 0, labelText.length, textBounds)
            val labelWidth = textBounds.width() + LABEL_PADDING * 2
            val labelHeight = textBounds.height() + LABEL_PADDING

            val labelX = cx
            val labelY = (cy - r - 16).coerceAtLeast(labelHeight + 8)

            val labelRect = RectF(
                (labelX - labelWidth / 2f).coerceIn(8f, width - 8f - labelWidth),
                (labelY - labelHeight).toFloat(),
                (labelX + labelWidth / 2f).coerceIn(8f + labelWidth, width - 8f),
                labelY.toFloat()
            )

            canvas.drawRoundRect(labelRect, 8f, 8f, labelBgPaint)
            canvas.drawText(labelText, labelX.toFloat(), (labelRect.bottom - LABEL_PADDING / 2f).toFloat(), labelPaint)

            applied++
        }

        // Save annotated bitmap
        val cacheDir = File(context.cacheDir, "annotated_screenshots").also { it.mkdirs() }
        val outputFile = File(cacheDir, "annotated_${System.currentTimeMillis()}.png")
        try {
            FileOutputStream(outputFile).use { out ->
                mutableBitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            Log.i(TAG, "Annotated screenshot saved: ${outputFile.absolutePath} ($applied highlights)")
            onResult(AnnotationResult.Success(AnnotatedResult(outputFile, width, height, applied)))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save annotated screenshot", e)
            onResult(AnnotationResult.Failed(e.message ?: "Save failed"))
        } finally {
            mutableBitmap.recycle()
        }
    }

    sealed interface AnnotationResult {
        data class Success(val value: AnnotatedResult) : AnnotationResult
        data class Failed(val reason: String) : AnnotationResult
    }
}