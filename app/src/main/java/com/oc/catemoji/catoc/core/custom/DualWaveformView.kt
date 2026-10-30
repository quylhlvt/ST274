package com.oc.catemoji.catoc.core.custom

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
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
    private val trackEdgePadding = dp(3f)
    private val minimumBar = dp(4f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val playerBarColor = Color.rgb(255, 137, 28)
    private val opponentBarColor = Color.rgb(8, 184, 50)
    private val rect = RectF()
    private val avatarClip = Path()
    private val battleProgress = DualWaveformBattleProgress()
    private var avatar: Drawable? = null
    private var avatarTranslationX = 0f
    private var displayedBattleProgress = battleProgress.value
    private var balanceAnimator: ValueAnimator? = null
    private var finishDispatched = false
    private var onBattleFinished: (() -> Unit)? = null
    private var onBattleProgressChanged: ((Float) -> Unit)? = null
    private var realBars = FloatArray(1)
    private var fakeBars = FloatArray(1)
    private var head = 0
    private var count = 0
    private var latestLevel = 0f
    private var smoothLevel = 0f
    private var fakePhase = 0f
    private var fakeLevel = 0.45f
    private var running = false
    private var simulateOpponent = true
    private var staticWaveform = false
    private var tapOpponentIntervalMs = 300L
    private val opponentPointIntervalMs: Long
        get() = if (staticWaveform) tapOpponentIntervalMs else OPPONENT_POINT_INTERVAL_MS
    private val tick = object : Runnable {
        override fun run() {
            if (!running || staticWaveform || !isAttachedToWindow || !isShown) return
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
    private val opponentTick = object : Runnable {
        override fun run() {
            if (!running || !simulateOpponent || !isAttachedToWindow || !isShown || battleProgress.isFinished) return
            animateBattleProgress(battleProgress.addOpponentPoint())
            if (!battleProgress.isFinished) postDelayed(this, opponentPointIntervalMs)
        }
    }

    init { minimumHeight = dp(56f).toInt() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(dp(320f).toInt(), widthMeasureSpec), resolveSize(dp(64f).toInt(), heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val capacity = ((w - avatarRadius - avatarGap - dp(12f)) / barStep)
            .toInt().coerceAtLeast(1)
        realBars = FloatArray(capacity)
        fakeBars = FloatArray(capacity)
        head = 0; count = 0
        updateAvatarGeometry()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f + avatarTranslationX
        val cy = height / 2f
        if (!staticWaveform) {
            drawBars(canvas, realBars, cx, cy, true, playerBarColor)
            drawBars(canvas, fakeBars, cx, cy, false, opponentBarColor)
        }
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
        if (staticWaveform) return
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
        val cx = width / 2f + avatarTranslationX
        avatar?.setBounds((cx - avatarRadius).toInt(), (height / 2f - avatarRadius).toInt(),
            (cx + avatarRadius).toInt(), (height / 2f + avatarRadius).toInt())
    }

    private fun updateAvatarGeometry() {
        val maximumTranslation = (width / 2f - avatarRadius - trackEdgePadding).coerceAtLeast(0f)
        avatarTranslationX = (displayedBattleProgress - 0.5f) * 2f * maximumTranslation
        val cx = width / 2f + avatarTranslationX
        avatarClip.reset()
        avatarClip.addCircle(cx, height / 2f, avatarRadius - dp(2f), Path.Direction.CW)
        updateAvatarBounds()
        // The avatar must remain fully inside the waveform, while the external
        // divider and color split are allowed to reach both absolute edges.
        onBattleProgressChanged?.invoke(displayedBattleProgress.coerceIn(0f, 1f))
        invalidate()
    }

    private fun animateBattleProgress(target: Float) {
        balanceAnimator?.cancel()
        val animator = ValueAnimator.ofFloat(displayedBattleProgress, target)
        animator.duration = BALANCE_ANIMATION_DURATION_MS
        animator.addUpdateListener { animation ->
            displayedBattleProgress = animation.animatedValue as Float
            updateAvatarGeometry()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (cancelled || balanceAnimator !== animation) return
                balanceAnimator = null
                displayedBattleProgress = target
                updateAvatarGeometry()
                if (battleProgress.isFinished && !finishDispatched) {
                    finishDispatched = true
                    onBattleFinished?.invoke()
                }
            }
        })
        balanceAnimator = animator
        animator.start()
    }

    /** One confirmed player bark occupies another 2% of the track. */
    fun addPlayerPoint() {
        if (!battleProgress.isFinished) animateBattleProgress(battleProgress.addPlayerPoint())
    }

    val isBattleFinished: Boolean
        get() = battleProgress.isFinished

    fun addOpponentPoint() {
        if (!battleProgress.isFinished) animateBattleProgress(battleProgress.addOpponentPoint())
    }

    fun setOnBattleFinishedListener(listener: (() -> Unit)?) {
        onBattleFinished = listener
    }

    fun setOnBattleProgressChangedListener(listener: ((Float) -> Unit)?) {
        onBattleProgressChanged = listener
        listener?.invoke(displayedBattleProgress.coerceIn(0f, 1f))
    }

    fun start(simulateOpponent: Boolean = true) {
        staticWaveform = false
        if (this.simulateOpponent != simulateOpponent) {
            this.simulateOpponent = simulateOpponent
            removeCallbacks(opponentTick)
            if (running && simulateOpponent && isAttachedToWindow && isShown && !battleProgress.isFinished) {
                postDelayed(opponentTick, opponentPointIntervalMs)
            }
        }
        if (running) return
        running = true
        removeCallbacks(tick)
        removeCallbacks(opponentTick)
        if (isAttachedToWindow && isShown) {
            post(tick)
            if (simulateOpponent && !battleProgress.isFinished) postDelayed(opponentTick, opponentPointIntervalMs)
        }
    }

    /** Tap mode hides audio bars while keeping the opponent's battle timer active. */
    fun setTapOpponentInterval(intervalMs: Long) {
        require(intervalMs > 0)
        if (tapOpponentIntervalMs == intervalMs) return
        tapOpponentIntervalMs = intervalMs
        if (staticWaveform && running && isAttachedToWindow && isShown && !battleProgress.isFinished) {
            removeCallbacks(opponentTick)
            postDelayed(opponentTick, opponentPointIntervalMs)
        }
    }

    fun showStaticWaveform() {
        if (staticWaveform && running) return
        staticWaveform = true
        running = true
        simulateOpponent = true
        removeCallbacks(tick)
        removeCallbacks(opponentTick)
        latestLevel = 0f
        smoothLevel = 0f
        realBars.fill(0f)
        fakeBars.fill(0f)
        head = 0
        count = 0
        if (isAttachedToWindow && isShown && !battleProgress.isFinished) {
            postDelayed(opponentTick, opponentPointIntervalMs)
        }
        invalidate()
    }

    fun stop() {
        running = false
        removeCallbacks(tick)
        removeCallbacks(opponentTick)
        balanceAnimator?.cancel()
        balanceAnimator = null
        displayedBattleProgress = battleProgress.value
        updateAvatarGeometry()
        latestLevel = 0f
    }

    fun clear() {
        balanceAnimator?.cancel()
        balanceAnimator = null
        finishDispatched = false
        displayedBattleProgress = battleProgress.reset()
        realBars.fill(0f); fakeBars.fill(0f)
        head = 0; count = 0; latestLevel = 0f; smoothLevel = 0f; fakePhase = 0f; fakeLevel = 0.45f
        updateAvatarGeometry()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (running && isShown) {
            if (!staticWaveform) post(tick)
            if (simulateOpponent && !battleProgress.isFinished) postDelayed(opponentTick, opponentPointIntervalMs)
        }
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // Visibility may be dispatched from View's constructor before tick is initialized.
        if (isAttachedToWindow) {
            removeCallbacks(tick)
            removeCallbacks(opponentTick)
            if (running && isShown) {
                if (!staticWaveform) post(tick)
                if (simulateOpponent && !battleProgress.isFinished) postDelayed(opponentTick, opponentPointIntervalMs)
            }
        }
    }
    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        removeCallbacks(opponentTick)
        balanceAnimator?.cancel()
        balanceAnimator = null
        super.onDetachedFromWindow()
    }

    private companion object {
        const val OPPONENT_POINT_INTERVAL_MS = 1_500L
        const val BALANCE_ANIMATION_DURATION_MS = 180L
    }
}
