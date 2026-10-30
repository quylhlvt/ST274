package com.oc.catemoji.catoc.data.audio

/** Word location is used only to distinguish repeated sounds, not to filter pauses. */
data class BarkSpeechWord(val text: String, val startMs: Long, val endMs: Long)
