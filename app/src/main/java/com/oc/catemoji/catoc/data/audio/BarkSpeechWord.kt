package com.oc.catemoji.catoc.data.audio

/** Timing distinguishes repeated words; confidence is diagnostic and does not block a point. */
data class BarkSpeechWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float = 0f,
)
