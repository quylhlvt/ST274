package com.oc.catemoji.catoc.core.custom

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** Rounded two-color background whose split follows the battle avatar. */
class SplitVoiceBackgroundDrawable(
    private val playerColor: Int,
    private val opponentColor: Int,
    private val dividerX: Float,
    private val cornerRadius: Float,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boundsF = RectF()
    private val clipPath = Path()

    override fun draw(canvas: Canvas) {
        boundsF.set(bounds)
        val save = canvas.save()
        clipPath.reset()
        clipPath.addRoundRect(boundsF, cornerRadius, cornerRadius, Path.Direction.CW)
        canvas.clipPath(clipPath)
        paint.color = playerColor
        canvas.drawRect(boundsF.left, boundsF.top, dividerX, boundsF.bottom, paint)
        paint.color = opponentColor
        canvas.drawRect(dividerX, boundsF.top, boundsF.right, boundsF.bottom, paint)
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
