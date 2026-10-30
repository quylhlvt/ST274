package com.oc.catemoji.catoc.core.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.oc.catemoji.catoc.R
import kotlin.math.abs

/** Two battle areas behind [DualWaveformView], split at the moving avatar centre. */
class DualWaveformBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val playerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.play_voice_player_background)
    }
    private val opponentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.play_voice_opponent_background)
    }
    private val fillBounds = RectF()
    private val fillClip = Path()
    private val waveformInset = dp(2.5f)
    private var dividerFraction = 0.5f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val frameInset = dp(3f)
        fillBounds.set(frameInset, frameInset, w - frameInset, h - frameInset)
        fillClip.reset()
        fillClip.addRoundRect(fillBounds, dp(4f), dp(4f), Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        val dividerX = waveformInset +
            (width - waveformInset * 2f).coerceAtLeast(0f) * dividerFraction
        val saveCount = canvas.save()
        canvas.clipPath(fillClip)
        canvas.drawRect(fillBounds.left, fillBounds.top, dividerX, fillBounds.bottom, playerPaint)
        canvas.drawRect(dividerX, fillBounds.top, fillBounds.right, fillBounds.bottom, opponentPaint)
        canvas.restoreToCount(saveCount)
    }

    override fun verifyDrawable(who: Drawable): Boolean = super.verifyDrawable(who)

    fun setDividerFraction(value: Float) {
        val nextValue = value.coerceIn(0f, 1f)
        if (abs(nextValue - dividerFraction) < MINIMUM_PROGRESS_CHANGE) return
        dividerFraction = nextValue
        invalidate()
    }

    private companion object {
        const val MINIMUM_PROGRESS_CHANGE = 0.0001f
    }
}
