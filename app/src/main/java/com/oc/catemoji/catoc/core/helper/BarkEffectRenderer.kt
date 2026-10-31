package com.oc.catemoji.catoc.core.helper

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.DrawableImageViewTarget
import com.bumptech.glide.request.transition.Transition
import com.oc.catemoji.catoc.data.repository.TrainingEffect

/** Shared bark visuals for Training and Play. Each accepted bark owns one effect. */
class BarkEffectRenderer(private val root: ViewGroup, private val layer: FrameLayout) {
    private val activeEffects = mutableMapOf<ImageView, AnimatorSet?>()
    private var effects: List<TrainingEffect> = emptyList()
    private val pending = mutableListOf<Pair<View, Boolean>>()
    private var disposed = false

    fun setEffects(effects: List<TrainingEffect>) {
        if (disposed) return
        this.effects = effects
        if (effects.isNotEmpty()) {
            val queued = pending.toList()
            pending.clear()
            queued.forEach { (anchor, mirrored) -> show(anchor, mirrored) }
        }
    }

    fun clear() {
        pending.clear()
        activeEffects.keys.toList().forEach(::removeEffect)
    }

    fun dispose() {
        disposed = true
        clear()
    }

    fun show(anchor: View, mirrored: Boolean = false) {
        if (disposed) return
        if (effects.isEmpty()) {
            if (pending.size < MAX_ACTIVE_EFFECTS) pending.add(anchor to mirrored)
            return
        }
        if (activeEffects.size >= MAX_ACTIVE_EFFECTS) return
        val effect = effects.randomOrNull() ?: return
        val bounds = RectF(0f, 0f, anchor.width.toFloat(), anchor.height.toFloat())
        var descendant = anchor
        while (descendant !== root) {
            descendant.matrix.mapRect(bounds)
            val parent = descendant.parent as? View ?: return
            bounds.offset((descendant.left - parent.scrollX).toFloat(), (descendant.top - parent.scrollY).toFloat())
            descendant = parent
        }
        val size = minOf(bounds.width(), bounds.height()).toInt()
        if (size <= 0) return
        val image = ImageView(layer.context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        layer.addView(image, FrameLayout.LayoutParams(size, size).apply {
            leftMargin = (bounds.centerX() - layer.left - size / 2f).toInt()
            topMargin = (bounds.centerY() - layer.top - size / 2f).toInt()
        })
        activeEffects[image] = null
        Glide.with(layer).load(effect.path).dontAnimate()
            .into(object : DrawableImageViewTarget(image) {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    super.onResourceReady(resource, transition)
                    if (activeEffects.containsKey(image) && !disposed) {
                        animateEffect(image, effect.folder, size, mirrored)
                    }
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    super.onLoadFailed(errorDrawable)
                    // Clear the request outside Glide's callback.
                    image.post { removeEffect(image) }
                }
            })
    }

    private fun animateEffect(image: ImageView, folder: Int, size: Int, mirrored: Boolean) {
        image.alpha = 1f
        val stationary = folder == 1
        val growDuration = if (stationary) BURST_DURATION_MS else FLIGHT_DURATION_MS
        val endScale = if (stationary) 1.8f else 1.4f
        val distance = size * 1.6f
        val animation = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(image, View.SCALE_X, 0.15f, endScale).apply {
                    duration = growDuration
                    interpolator = DecelerateInterpolator()
                },
                ObjectAnimator.ofFloat(image, View.SCALE_Y, 0.15f, endScale).apply {
                    duration = growDuration
                    interpolator = DecelerateInterpolator()
                },
                ObjectAnimator.ofFloat(image, View.TRANSLATION_X, 0f,
                    if (folder == 2) distance * (if (mirrored) -1f else 1f) else 0f)
                    .setDuration(growDuration),
                ObjectAnimator.ofFloat(
                    image,
                    View.TRANSLATION_Y,
                    0f,
                    when (folder) {
                        2 -> -distance
                        3 -> -distance
                        else -> 0f
                    },
                )
                    .setDuration(growDuration),
                ObjectAnimator.ofFloat(image, View.ALPHA, 1f, 0f).apply {
                    startDelay = BURST_DURATION_MS
                    duration = FADE_DURATION_MS
                },
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // With system animations disabled this can run inside Glide's callback.
                    image.post { removeEffect(image) }
                }
            })
        }
        activeEffects[image] = animation
        animation.start()
    }

    private fun removeEffect(image: ImageView) {
        activeEffects.remove(image)?.apply {
            removeAllListeners()
            cancel()
        }
        // App-scoped RequestManager is safe even when a delayed callback executes
        // after Activity destruction. Glide.with(layer) is NOT safe in that case.
        Glide.with(layer.context.applicationContext).clear(image)
        layer.removeView(image)
    }

    private companion object {
        const val MAX_ACTIVE_EFFECTS = 12
        const val BURST_DURATION_MS = 180L
        const val FADE_DURATION_MS = 380L
        const val FLIGHT_DURATION_MS = BURST_DURATION_MS + FADE_DURATION_MS
    }
}
