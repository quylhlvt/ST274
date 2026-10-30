package com.oc.catemoji.catoc.core.custom

import kotlin.math.abs
import kotlin.math.roundToInt

/** Pure progress state for the tug-of-war marker rendered by [DualWaveformView]. */
internal class DualWaveformBattleProgress(
    val step: Float = 0.02f,
) {
    private val maximumStep = (1f / step).roundToInt()
    private val initialStep = maximumStep / 2
    private var currentStep = initialStep

    val value: Float
        get() = (currentStep * step).coerceIn(0f, 1f)

    val isFinished: Boolean
        get() = currentStep == 0 || currentStep == maximumStep

    init {
        require(step > 0f && maximumStep > 0 && maximumStep % 2 == 0)
        require(abs(maximumStep * step - 1f) < 0.0001f) {
            "Step must divide the full progress range exactly"
        }
    }

    fun addPlayerPoint(): Float {
        if (!isFinished) currentStep = (currentStep + 1).coerceAtMost(maximumStep)
        return value
    }

    fun addOpponentPoint(): Float {
        if (!isFinished) currentStep = (currentStep - 1).coerceAtLeast(0)
        return value
    }

    fun reset(): Float {
        currentStep = initialStep
        return value
    }
}
