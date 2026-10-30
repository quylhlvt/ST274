package com.oc.catemoji.catoc.core.custom

import android.graphics.Rect
import android.view.View
import kotlin.math.roundToInt

/** Maps battle progress to the full marker track and its two colored regions. */
class VoiceTrackDivider(
    private val waveform: View,
    private val marker: View,
    private val playerBackground: View,
    private val opponentBackground: View,
) {
    private val playerClip = Rect()
    private val opponentClip = Rect()
    private var battleFraction = 0.5f
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        updateGeometry()
    }

    init {
        waveform.addOnLayoutChangeListener(layoutListener)
        marker.addOnLayoutChangeListener(layoutListener)
        playerBackground.addOnLayoutChangeListener(layoutListener)
        opponentBackground.addOnLayoutChangeListener(layoutListener)
        updateGeometry()
    }

    fun updateBattleProgress(fraction: Float) {
        battleFraction = fraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f
        updateGeometry()
    }

    private fun updateGeometry() {
        if (waveform.width <= 0) return
        // All four views share the same parent. Use their measured positions
        // so margins, tablets and a later layout pass cannot offset the split.
        val centerX = waveform.left + waveform.width * battleFraction
        marker.translationX = centerX - marker.left - marker.width / 2f

        val playerEnd = (centerX - playerBackground.left).roundToInt()
            .coerceIn(0, playerBackground.width)
        playerClip.set(0, 0, playerEnd, playerBackground.height)
        playerBackground.clipBounds = playerClip

        val opponentStart = (centerX - opponentBackground.left).roundToInt()
            .coerceIn(0, opponentBackground.width)
        opponentClip.set(opponentStart, 0, opponentBackground.width, opponentBackground.height)
        opponentBackground.clipBounds = opponentClip
    }

    fun dispose() {
        waveform.removeOnLayoutChangeListener(layoutListener)
        marker.removeOnLayoutChangeListener(layoutListener)
        playerBackground.removeOnLayoutChangeListener(layoutListener)
        opponentBackground.removeOnLayoutChangeListener(layoutListener)
    }
}
