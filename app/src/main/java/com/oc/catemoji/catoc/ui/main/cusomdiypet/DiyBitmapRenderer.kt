package com.oc.catemoji.catoc.ui.main.cusomdiypet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.min
import kotlin.math.roundToInt

/** CPU work only; independent of Activities, Views, and their lifecycle. */
internal object DiyBitmapRenderer {
    private val typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    fun renderText(
        text: String,
        color: Int,
        textSize: Float,
        font: Typeface? = null,
        background: Bitmap? = null
    ): Bitmap = if (background == null) {
        renderPlainText(text, color, textSize, font)
    } else {
        renderTextOnBackground(text, color, textSize, font, background)
    }

    private fun renderPlainText(
        text: String,
        color: Int,
        textSize: Float,
        font: Typeface?
    ): Bitmap {
        val width = 900
        val height = 260
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = font ?: DiyBitmapRenderer.typeface
            this.color = color
            this.textSize = textSize
        }
        while (paint.measureText(text) > width * 0.9f && paint.textSize > 38f) {
            paint.textSize -= 4f
        }
        val baseline = height / 2f - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(text, width / 2f, baseline, paint)
        return bitmap
    }

    /** Mirrors DialogSpeech: preserve the bubble ratio and center up to two text lines inside it. */
    private fun renderTextOnBackground(
        text: String,
        color: Int,
        requestedTextSize: Float,
        font: Typeface?,
        background: Bitmap
    ): Bitmap {
        val width = background.width.coerceAtLeast(1)
        val height = background.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawBitmap(background, 0f, 0f, null)

        // DialogSpeech uses 65dp horizontal margins inside the bubble. Keeping
        // roughly the same relative width makes the text stay in the bubble body.
        val textAreaWidth = (width * 0.55f).roundToInt().coerceAtLeast(1)
        val maximumTextHeight = height * 0.28f
        val maximumTextSize = min(width * 0.15f, height * 0.10f)
        val minimumTextSize = min(width, height) * 0.035f
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.LEFT
            typeface = font ?: DiyBitmapRenderer.typeface
            this.color = color
            this.textSize = min(requestedTextSize, maximumTextSize)
        }

        var layout = createTextLayout(text, paint, textAreaWidth)
        while ((layout.lineCount > 2 || layout.height > maximumTextHeight) &&
            paint.textSize > minimumTextSize
        ) {
            paint.textSize = (paint.textSize - 2f).coerceAtLeast(minimumTextSize)
            layout = createTextLayout(text, paint, textAreaWidth)
        }

        val left = (width - textAreaWidth) / 2f
        // DialogSpeech centers the TextView and moves it slightly upward.
        val top = (height - layout.height) / 2f - height * 0.02f
        canvas.save()
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
        return bitmap
    }

    private fun createTextLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(0f, 1f)
            .build()

    /** Preserves every visible pixel and handles narrow or fully transparent PNGs. */
    fun trimTransparentPadding(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val row = IntArray(bitmap.width)
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in row.indices) {
                if (row[x] ushr 24 != 0) {
                    left = minOf(left, x)
                    right = maxOf(right, x)
                    top = minOf(top, y)
                    bottom = y
                }
            }
        }
        if (right < left || (left == 0 && top == 0 && right == bitmap.width - 1 && bottom == bitmap.height - 1)) {
            return bitmap
        }
        return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
    }
}
