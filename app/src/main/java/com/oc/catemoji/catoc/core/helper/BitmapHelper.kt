package com.oc.catemoji.catoc.core.helper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.core.graphics.createBitmap

object BitmapHelper {
    fun getBitmapFromEditText(view: View): Bitmap {
        val backgroundDrawable = view.background

        val bitmap = createBitmap(view.width, view.height)
        val canvas = Canvas(bitmap)
        view.draw(canvas)

        if (backgroundDrawable != null) {
            backgroundDrawable.setBounds(0, 0, canvas.width, canvas.height)
            backgroundDrawable.draw(canvas)
        }
        return bitmap
    }
}