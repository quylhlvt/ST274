package com.oc.catemoji.catoc.ui.main.cusomdiypet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

/** CPU work only; independent of Activities, Views, and their lifecycle. */
internal object DiyBitmapRenderer {
    private val typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    fun renderText(text: String, color: Int, textSize: Float): Bitmap {
        val width = 900
        val height = 260
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = DiyBitmapRenderer.typeface
            this.color = color
            this.textSize = textSize
        }
        while (paint.measureText(text) > width * 0.9f && paint.textSize > 38f) {
            paint.textSize -= 4f
        }
        val baseline = height / 2f - (paint.ascent() + paint.descent()) / 2f
        Canvas(bitmap).drawText(text, width / 2f, baseline, paint)
        return bitmap
    }

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
