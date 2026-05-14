package com.oc.catemoji.catoc.core.custom

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.graphics.createBitmap


class DrawDraw(override var drawable: Drawable, private val filePath: String) : Draw() {

    private val bounds = Rect(0, 0, width, height)

    override val width: Int
        get() = drawable.intrinsicWidth
    override val height: Int
        get() = drawable.intrinsicHeight

    override fun draw(canvas: Canvas) {
        val matrix = getMatrix()
        canvas.save()
        canvas.concat(matrix)
        drawable.bounds = bounds
        drawable.draw(canvas)
        canvas.restore()
    }

    override fun setDrawable(drawable: Drawable): DrawDraw {
        this.drawable = drawable
        return this
    }

    override val drawablePath: String
        get() = filePath

    override fun setAlpha(alpha: Int): Draw {
        drawable.alpha = alpha
        return this
    }

}

sealed class SaveState {
    data class Success(val path: String) : SaveState()
    data class Error(val exception: Exception) : SaveState()
    object Loading : SaveState()
}

@Throws(OutOfMemoryError::class)
fun createBimapFromView(view: View): Bitmap {
    try {
        val output = createBitmap(view.width, view.height)
        val canvas = Canvas(output)
        view.draw(canvas)
        return output
    } catch (error: OutOfMemoryError) {
        throw error
    }
}