package com.oc.catemoji.catoc.ui.main.play

import com.oc.catemoji.catoc.data.model.DogSkinCatalogue
import kotlin.math.roundToLong

/** Each tick pushes one point; later skins and mastery demand a faster response. */
object BattleDifficulty {
    fun opponentIntervalMs(skinId: Int, progressPercent: Int, useMicrophone: Boolean): Long {
        val skinLevel = (skinId.coerceIn(1, DogSkinCatalogue.SKIN_COUNT) - 1).toDouble() /
            (DogSkinCatalogue.SKIN_COUNT - 1)
        val mastery = progressPercent.coerceIn(0, 100) / 100.0
        val speed = 1.0 + skinLevel + mastery * 0.5
        val baseIntervalMs = if (useMicrophone) 1_500L else 300L
        return (baseIntervalMs / speed).roundToLong()
    }
}
