package com.oc.catemoji.catoc.data.audio

data class SoundScore(val index: Int, val label: String, val score: Float)

data class SoundPrediction(val scores: List<SoundScore>, val level: Float, val hasClassification: Boolean = true)

data class BarkDetection(val top: SoundScore?, val isBark: Boolean, val level: Float)

/** Stabilizes model predictions; class identities come from the exported model's label map. */
class BarkDetectionPolicy(
    private val barkClassIndex: Int,
    private val threshold: Float = 0.75f,
    private val minimumLevel: Float = 0.005f,
    private val requiredMatches: Int = 2,
) {
    private var matches = 0
    private var lastTop: SoundScore? = null

    init {
        require(barkClassIndex >= 0 && threshold in 0f..1f && minimumLevel >= 0f && requiredMatches > 0)
    }

    fun evaluate(prediction: SoundPrediction): BarkDetection {
        val level = prediction.level.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        if (prediction.hasClassification) {
            lastTop = prediction.scores.filter { it.score.isFinite() && it.score in 0f..1f }.maxByOrNull { it.score }
            val candidate = lastTop?.let { it.index == barkClassIndex && it.score >= threshold } == true && level >= minimumLevel
            matches = if (candidate) (matches + 1).coerceAtMost(requiredMatches) else 0
        }
        return BarkDetection(lastTop, matches >= requiredMatches && level >= minimumLevel, level)
    }
}
