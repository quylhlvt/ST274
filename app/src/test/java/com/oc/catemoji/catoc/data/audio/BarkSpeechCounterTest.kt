package com.oc.catemoji.catoc.data.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class BarkSpeechCounterTest {
    @Test
    fun partialsAddPointsImmediatelyAndOnlyCountTheIncrease() {
        val counter = BarkSpeechCounter()
        counter.accept("gâu", false)
        assertEquals(1, counter.count)
        counter.accept("gâu gâu", false)
        assertEquals(2, counter.count)
        repeat(3) { counter.accept("gâu gâu", false) }
        assertEquals(2, counter.count)
        counter.accept("gâu gâu gâu", false)
        assertEquals(3, counter.count)
    }

    @Test
    fun finalResultOnlyAddsWordsNotAlreadyCountedFromPartials() {
        val counter = BarkSpeechCounter()
        counter.accept("gâu", false)
        counter.accept("gâu gâu", true)
        assertEquals(2, counter.count)
        counter.accept("gâu", false)
        assertEquals(3, counter.count)
        counter.accept("gâu", true)
        assertEquals(3, counter.count)
    }

    @Test
    fun shorterHypothesesDoNotReplayPreviouslyAwardedPoints() {
        val counter = BarkSpeechCounter()
        counter.accept("gâu gâu", false)
        counter.accept("gâu", false)
        counter.accept("gâu gâu", false)
        counter.accept("", true)
        assertEquals(2, counter.count)
    }

    @Test
    fun finalOnlyUtterancesAndJoinedBarksStillCount() {
        val counter = BarkSpeechCounter()
        counter.accept("gâu", true)
        counter.accept("gâu", true)
        counter.accept("gaugau", true)
        assertEquals(4, counter.count)
    }

    @Test
    fun textIsMatchedWithoutConfidenceLanguageOrTimingFilters() {
        val counter = BarkSpeechCounter()
        counter.accept("gâu woof gầu", false)
        assertEquals(3, counter.count)
        counter.accept("xin chào", true)
        counter.accept("sủa yap yip bau hong au guk", true)
        assertEquals(3, counter.count)
    }

    @Test
    fun samePartialTextAtNewWordPositionsAddsEachContinuousBark() {
        val counter = BarkSpeechCounter()
        counter.accept("gô", false, listOf(BarkSpeechWord("gô", 100, 300)))
        assertEquals(1, counter.count)
        counter.accept("", false)
        counter.accept("gô", false, listOf(BarkSpeechWord("gô", 300, 500)))
        assertEquals(2, counter.count)
        counter.accept("gô", false, listOf(BarkSpeechWord("gô", 500, 700)))
        assertEquals(3, counter.count)
    }

    @Test
    fun replayedOrRevisedWordSpansDoNotScoreAgain() {
        val counter = BarkSpeechCounter()
        repeat(3) { counter.accept("gô", false, listOf(BarkSpeechWord("gô", 100, 300))) }
        counter.accept("", false)
        counter.accept("gõ", false, listOf(BarkSpeechWord("gõ", 130, 330)))
        assertEquals(1, counter.count)
        counter.accept("gõ", true, listOf(BarkSpeechWord("gõ", 100, 330)))
        assertEquals(1, counter.count)
    }

    @Test
    fun textFallbackAndLaterAlignmentDoNotDuplicateTheFirstPoint() {
        val counter = BarkSpeechCounter()
        counter.accept("gô", false)
        counter.accept("gô", false, listOf(BarkSpeechWord("gô", 100, 300)))
        assertEquals(1, counter.count)
        counter.accept("gô gô", false, listOf(
            BarkSpeechWord("gô", 100, 300), BarkSpeechWord("gô", 300, 500),
        ))
        assertEquals(2, counter.count)
        counter.accept("gô gô", true, listOf(
            BarkSpeechWord("gô", 100, 300), BarkSpeechWord("gô", 300, 500),
        ))
        assertEquals(2, counter.count)
    }

    @Test
    fun joinedAndSplitRevisionsDoNotReplayTheSameBarks() {
        val counter = BarkSpeechCounter()
        counter.accept("gô gô", false, listOf(
            BarkSpeechWord("gô", 100, 300), BarkSpeechWord("gô", 300, 500),
        ))
        counter.accept("gôgô", false, listOf(BarkSpeechWord("gôgô", 100, 500)))
        counter.accept("gô gô", true, listOf(
            BarkSpeechWord("gô", 100, 300), BarkSpeechWord("gô", 300, 500),
        ))
        assertEquals(2, counter.count)
    }

    @Test
    fun endpointsResetWordPositionsAndInvalidAlignmentKeepsTextFallback() {
        val counter = BarkSpeechCounter()
        counter.accept("gô", true, listOf(BarkSpeechWord("gô", 100, 300)))
        counter.accept("gô", false, listOf(BarkSpeechWord("gô", -1, -1)))
        counter.accept("gô", true)
        assertEquals(2, counter.count)
    }
}
