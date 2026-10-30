package com.oc.catemoji.catoc.core.custom

import android.view.View

/** Grows the leading avatar across the gap, touching its opponent at an endpoint. */
class BattleAvatarGrowth(private val player: View, private val opponent: View) {
    private var progress = 0.5f
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> render() }

    init {
        player.addOnLayoutChangeListener(layoutListener)
        opponent.addOnLayoutChangeListener(layoutListener)
    }

    fun updateBattleProgress(value: Float) {
        progress = value.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.5f
        render()
    }

    private fun render() {
        if (player.width <= 0 || opponent.width <= 0) return
        val gap = (opponent.left - player.right).coerceAtLeast(0).toFloat()
        val playerLead = ((progress - 0.5f) * 2f).coerceAtLeast(0f)
        val opponentLead = ((0.5f - progress) * 2f).coerceAtLeast(0f)
        player.pivotX = 0f
        player.pivotY = player.height.toFloat()
        opponent.pivotX = opponent.width.toFloat()
        opponent.pivotY = opponent.height.toFloat()
        val playerScale = 1f + gap / player.width * playerLead
        val opponentScale = 1f + gap / opponent.width * opponentLead
        player.scaleX = playerScale
        player.scaleY = playerScale
        opponent.scaleX = opponentScale
        opponent.scaleY = opponentScale
    }

    fun dispose() {
        player.removeOnLayoutChangeListener(layoutListener)
        opponent.removeOnLayoutChangeListener(layoutListener)
    }
}
