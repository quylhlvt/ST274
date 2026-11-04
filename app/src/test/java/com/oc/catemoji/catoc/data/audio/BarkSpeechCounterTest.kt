package com.oc.catemoji.catoc.data.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class BarkSpeechCounterTest {
    private fun word(text: String, start: Long, end: Long, confidence: Float = 0.95f) =
        BarkSpeechWord(text, start, end, confidence)

    @Test fun alignedBarksScoreWithoutWaitingForTheWholePhraseToEnd() {
        listOf("gâu", "go", "gô").forEach { sound ->
            val counter = BarkSpeechCounter()
            counter.accept(sound, false, listOf(word(sound, 100, 300, 0.505126f)), partialText = sound)
            assertEquals(sound, 1, counter.count)
            counter.accept(sound, true, listOf(word(sound, 100, 300, 0.8f)))
            assertEquals(sound, 1, counter.count)
        }
    }

    @Test fun unalignedSingletonDoesNotScoreBeforeItCanBeCorrected() {
        val counter = BarkSpeechCounter()
        repeat(10) { counter.accept("", false, partialText = "go", audioTimeMs = it * 40L) }
        counter.accept("gõ", true, listOf(word("gõ", 100, 300)))
        assertEquals(0, counter.count)
    }

    @Test fun isolatedBarkScoresAtTheShortAcousticEndpoint() {
        val counter = BarkSpeechCounter()
        counter.accept("", false, partialText = "gâu")
        assertEquals(0, counter.count)
        counter.accept("gâu", true, listOf(word("gâu", 100, 300)))
        assertEquals(1, counter.count)
    }

    @Test fun stableRepeatedBarksScoreLiveAndTheFinalAddsOnlyMissingWords() {
        val counter = BarkSpeechCounter()
        counter.accept("", false, partialText = "gâu gâu", audioTimeMs = 600)
        counter.accept("", false, partialText = "gâu gâu", audioTimeMs = 640)
        assertEquals(0, counter.count)
        counter.accept("", false, partialText = "gâu gâu", audioTimeMs = 680)
        assertEquals(2, counter.count)
        repeat(30) { counter.accept("", false, partialText = "gâu gâu", audioTimeMs = 720 + it * 40L) }
        counter.accept("gâu gâu gâu", true, listOf(word("gâu", 100, 300), word("gâu", 300, 500), word("gâu", 500, 700)))
        assertEquals(3, counter.count)
    }

    @Test fun transientRepeatedGuessAndARevisionRestartConfirmation() {
        val counter = BarkSpeechCounter()
        counter.accept("", false, partialText = "go go", audioTimeMs = 100)
        counter.accept("", false, partialText = "go gõ", audioTimeMs = 180)
        counter.accept("", false, partialText = "gâu gâu", audioTimeMs = 200)
        counter.accept("", false, partialText = "gâu gâu", audioTimeMs = 240)
        assertEquals(0, counter.count)
        counter.accept("", false, partialText = "gâu gâu", audioTimeMs = 280)
        assertEquals(2, counter.count)
    }

    @Test fun conversationContainingABarkLikeWordDoesNotScore() {
        listOf("giàng gâu vàng", "gô ngõ", "tôi nói gâu hôm nay", "[unk] go").forEach { phrase ->
            val counter = BarkSpeechCounter()
            counter.accept(phrase, false, partialText = phrase, audioTimeMs = 0)
            counter.accept(phrase, false, partialText = phrase, audioTimeMs = 200)
            counter.accept(phrase, true)
            assertEquals(phrase, 0, counter.count)
        }
    }

    @Test fun conflictingLiveAndDelayedTranscriptsCannotConfirmEachOther() {
        val counter = BarkSpeechCounter()
        counter.accept("gõ", false, listOf(word("gõ", 100, 300)), partialText = "gô")
        counter.accept("gô", false, listOf(word("gô", 100, 300)), partialText = "gỗ")
        counter.accept("gỗ", true, listOf(word("gỗ", 100, 300)))
        assertEquals(0, counter.count)
    }

    @Test fun lowOrInvalidConfidenceDoesNotConfirmASingleton() {
        listOf(0f, 0.39f, Float.NaN, Float.POSITIVE_INFINITY).forEach { confidence ->
            val counter = BarkSpeechCounter()
            counter.accept("go", false, listOf(word("go", 100, 300, confidence)))
            counter.accept("go", true, listOf(word("go", 100, 300, confidence)))
            assertEquals("confidence $confidence", 0, counter.count)
        }
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

    @Test fun overlappingBoundaryAndSpellingRevisionsDoNotReplayTheSameSound() {
        val counter = BarkSpeechCounter()
        counter.accept("gô", false, listOf(word("gô", 100, 300)))
        counter.accept("go", false, listOf(word("go", 120, 320)))
        counter.accept("gâu", true, listOf(word("gâu", 110, 310)))
        assertEquals(1, counter.count)
    }

    @Test fun nextAcousticUtteranceHasFreshSpansEvenWhenTimesRestart() {
        val counter = BarkSpeechCounter()
        repeat(3) { counter.accept("gâu", true, listOf(word("gâu", 100, 300))) }
        assertEquals(3, counter.count)
    }

    @Test fun duplicateAlignedEntriesInOneUpdateAreNotSeparateBarks() {
        val counter = BarkSpeechCounter()
        counter.accept("gâu gâu", true, listOf(word("gâu", 100, 300), word("gâu", 110, 310)))
        assertEquals(1, counter.count)
    }

    @Test fun completedTextCanStillBeCountedWhenTheEngineOmitsAlignment() {
        val counter = BarkSpeechCounter()
        counter.accept("gâu gâu", true)
        assertEquals(2, counter.count)
    }

    @Test fun reportedNonBarkWordsRemainRejected() {
        val counter = BarkSpeechCounter()
        listOf("gau", "mõ", "vàng", "giàng", "gấu", "gầu", "gạo", "gõ", "gỗ", "mộ", "ngõ", "[unk]").forEach { text ->
            repeat(3) { counter.accept(text, false, listOf(word(text, 100, 300, 1f)), partialText = text) }
            counter.accept(text, true, listOf(word(text, 100, 300, 1f)))
            assertEquals(text, 0, counter.count)
        }
    }
}
