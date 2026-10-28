package com.oc.catemoji.catoc.core.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.random.Random

/** Orange: live microphone envelope. Green: simulated opponent, not another microphone. */
class DualWaveformView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density
    private val barWidth = dp(3f)
    private val barStep = dp(5f)
    private val avatarRadius = dp(20f)
    private val avatarGap = dp(5f)
    private val minimumBar = dp(4f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY; style = Paint.Style.STROKE; strokeWidth = dp(1f)
    }
    private val rect = RectF()
    private val avatarClip = Path()
    private var avatar: Drawable? = null
    private var realBars = FloatArray(1)
    private var fakeBars = FloatArray(1)
    private var head = 0
    private var count = 0
    private var latestLevel = 0f
    private var smoothLevel = 0f
    private var fakePhase = 0f
    private var fakeLevel = 0.45f
    private var running = false
    private val tick = object : Runnable {
        override fun run() {
            if (!running || !isAttachedToWindow || !isShown) return
            smoothLevel = smoothLevel * 0.35f + latestLevel * 0.65f
            fakePhase += 0.22f
            val target = (0.12f + abs(sin(fakePhase * 0.41f)) * 0.43f +
                abs(sin(fakePhase * 1.65f)) * 0.19f + Random.nextFloat() * 0.24f).coerceIn(0f, 1f)
            fakeLevel = fakeLevel * 0.4f + target * 0.6f
            head = (head + realBars.size - 1) % realBars.size
            realBars[head] = smoothLevel
            fakeBars[head] = fakeLevel
            count = (count + 1).coerceAtMost(realBars.size)
            invalidate()
            postDelayed(this, 50L)
        }
    }

    init { minimumHeight = dp(56f).toInt() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(dp(320f).toInt(), widthMeasureSpec), resolveSize(dp(64f).toInt(), heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val capacity = ((w / 2f - avatarRadius - avatarGap - dp(6f)) / barStep).toInt().coerceAtLeast(1)
        realBars = FloatArray(capacity)
        fakeBars = FloatArray(capacity)
        head = 0; count = 0
        avatarClip.reset()
        avatarClip.addCircle(w / 2f, h / 2f, avatarRadius - dp(2f), Path.Direction.CW)
        updateAvatarBounds()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        rect.set(dp(1f), dp(1f), width - dp(1f), height - dp(1f))
        canvas.drawRoundRect(rect, dp(3f), dp(3f), border)
        drawBars(canvas, realBars, cx, cy, true, Color.rgb(255, 137, 28))
        drawBars(canvas, fakeBars, cx, cy, false, Color.rgb(8, 184, 50))
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, avatarRadius, paint)
        val image = avatar
        if (image != null) {
            val save = canvas.save()
            canvas.clipPath(avatarClip)
            image.draw(canvas)
            canvas.restoreToCount(save)
        } else {
            paint.color = Color.LTGRAY
            canvas.drawCircle(cx, cy - dp(5f), dp(7f), paint)
            rect.set(cx - dp(12f), cy + dp(3f), cx + dp(12f), cy + dp(17f))
            canvas.drawOval(rect, paint)
        }
        canvas.drawCircle(cx, cy, avatarRadius, border)
    }

    private fun drawBars(canvas: Canvas, values: FloatArray, cx: Float, cy: Float, leftSide: Boolean, color: Int) {
        paint.color = color
        val maxHeight = (height - dp(8f)).coerceAtLeast(minimumBar)
        for (index in 0 until count) {
            val distance = avatarRadius + avatarGap + index * barStep
            val x = if (leftSide) cx - distance - barWidth else cx + distance
            if (x < dp(3f) || x + barWidth > width - dp(3f)) continue
            val barHeight = minimumBar + values[(head + index) % values.size] * (maxHeight - minimumBar)
            rect.set(x, cy - barHeight / 2f, x + barWidth, cy + barHeight / 2f)
            canvas.drawRoundRect(rect, barWidth / 2f, barWidth / 2f, paint)
        }
    }

    /** PCM 16-bit equivalent RMS, 0..32767, delivered on the main thread. */
    fun addRealAmplitude(rawAmplitude: Int) {
        val amplitude = rawAmplitude.coerceIn(0, 32767)
        latestLevel = if (amplitude == 0) 0f else
            ((20.0 * log10(amplitude / 32767.0) + 55.0) / 55.0).toFloat().coerceIn(0f, 1f)
    }

    fun setAvatar(@DrawableRes resource: Int) = setAvatar(ContextCompat.getDrawable(context, resource))

    fun setAvatar(drawable: Drawable?) {
        avatar = drawable?.constantState?.newDrawable(resources)?.mutate() ?: drawable
        updateAvatarBounds()
        invalidate()
    }

    private fun updateAvatarBounds() {
        avatar?.setBounds((width / 2f - avatarRadius).toInt(), (height / 2f - avatarRadius).toInt(),
            (width / 2f + avatarRadius).toInt(), (height / 2f + avatarRadius).toInt())
    }

    fun start() {
        if (running) return
        running = true
        removeCallbacks(tick)
        if (isAttachedToWindow && isShown) post(tick)
    }

    fun stop() { running = false; removeCallbacks(tick); latestLevel = 0f }

    fun clear() {
        realBars.fill(0f); fakeBars.fill(0f)
        head = 0; count = 0; latestLevel = 0f; smoothLevel = 0f; fakePhase = 0f; fakeLevel = 0.45f
        invalidate()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (running && isShown) post(tick) }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // Visibility may be dispatched from View's constructor before tick is initialized.
        if (isAttachedToWindow) { removeCallbacks(tick); if (running && isShown) post(tick) }
    }
    override fun onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow() }
}
