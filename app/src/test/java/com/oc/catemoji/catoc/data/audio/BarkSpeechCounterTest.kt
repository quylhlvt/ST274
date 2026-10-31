package com.oc.catemoji.catoc.data.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class BarkSpeechCounterTest {
    private fun word(text: String, start: Long, end: Long, confidence: Float = 0f) =
        BarkSpeechWord(text, start, end, confidence)

    @Test fun firstGauAndGoScoreOnTheirFirstPartialWithoutConfidenceOrAlignment() {
        listOf("gâu", "gau", "go", "gô").forEach { sound ->
            val counter = BarkSpeechCounter()
            counter.accept("", false, partialText = sound)
            assertEquals(sound, 1, counter.count)
        }
    }

    @Test fun normalTranscriptAlsoScoresImmediatelyWhenFastPartialIsAbsent() {
        val counter = BarkSpeechCounter()
        counter.accept("gâu", false)
        assertEquals(1, counter.count)
        counter.accept("gâu go", false)
        assertEquals(2, counter.count)
        counter.accept("gâu go gô", false)
        assertEquals(3, counter.count)
    }

    @Test fun lowConfidenceOrConflictingDelayedAlignmentCannotBlockALiveGo() {
        val counter = BarkSpeechCounter()
        counter.accept("gô", false, listOf(word("gô", 261, 432, 0.505126f)), partialText = "gô")
        assertEquals(1, counter.count)
        counter.accept("gỗ", false, listOf(word("gỗ", 261, 432, 0.95f)), partialText = "gô")
        assertEquals(1, counter.count)
        counter.accept("gô", true, listOf(word("gô", 261, 432, 0.4f)))
        assertEquals(1, counter.count)
    }

    @Test fun growingPartialsEachAddOneAndReplayedPartialsOrFinalAddNothing() {
        val counter = BarkSpeechCounter()
        counter.accept("", false, partialText = "gâu")
        assertEquals(1, counter.count)
        repeat(3) { counter.accept("", false, partialText = "gâu") }
        counter.accept("", false, partialText = "gâu go")
        assertEquals(2, counter.count)
        counter.accept("", false, partialText = "gâu go gô")
        assertEquals(3, counter.count)
        counter.accept("gâu go gô", true, listOf(word("gâu", 100, 300), word("go", 300, 500), word("gô", 500, 700)))
        assertEquals(3, counter.count)
        counter.accept("go", false)
        assertEquals(4, counter.count)
    }

    @Test fun repeatedTextAtNewWordPositionsAddsTheNewBarkOnly() {
        val counter = BarkSpeechCounter()
        listOf(100L, 300L, 500L).forEachIndexed { index, start ->
            counter.accept("gô", false, listOf(word("gô", start, start + 200)))
            assertEquals(index + 1, counter.count)
        }
        counter.accept("gâu", true, listOf(word("gâu", 520, 720)))
        assertEquals(3, counter.count)
    }

    @Test fun reportedNonBarkWordsRemainRejected() {
        val counter = BarkSpeechCounter()
        listOf("mõ", "vàng", "giàng", "gấu", "gầu", "gạo", "gõ", "gỗ", "mộ", "ngõ", "[unk]").forEach { text ->
            repeat(3) { counter.accept(text, false, listOf(word(text, 100, 300, 1f)), partialText = text) }
            counter.accept(text, true, listOf(word(text, 100, 300, 1f)))
            assertEquals(text, 0, counter.count)
        }
    }
}
