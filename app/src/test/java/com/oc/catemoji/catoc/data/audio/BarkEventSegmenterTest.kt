package com.oc.catemoji.catoc.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarkEventSegmenterTest {
    private val sound = ShortArray(BarkEventSegmenter.BLOCK_SAMPLES) { 3_000 }
    private val silence = ShortArray(sound.size)

    @Test fun shortGapsSeparateBarksAndBriefDipsDoNotSplitThem() {
        val segmenter = BarkEventSegmenter()
        repeat(2) {
            repeat(4) { assertEquals(0, segmenter.accept(sound, sound.size)) }
            repeat(2) { assertEquals(0, segmenter.accept(silence, silence.size)) }
            repeat(3) { assertEquals(0, segmenter.accept(sound, sound.size)) }
            repeat(2) { assertEquals(0, segmenter.accept(silence, silence.size)) }
            assertTrue(segmenter.accept(silence, silence.size) > 0)
            assertEquals(0, segmenter.accept(silence, silence.size))
        }
    }

    @Test fun aClickAndAHeldSoundNeverProduceBarkCandidates() {
        val segmenter = BarkEventSegmenter()
        assertEquals(0, segmenter.accept(sound, sound.size))
        repeat(3) { assertEquals(0, segmenter.accept(silence, silence.size)) }
        repeat(90) { assertEquals(0, segmenter.accept(sound, sound.size)) }
        repeat(30) { assertEquals(0, segmenter.accept(silence, silence.size)) }
        repeat(4) { assertEquals(0, segmenter.accept(sound, sound.size)) }
        repeat(2) { assertEquals(0, segmenter.accept(silence, silence.size)) }
        assertTrue(segmenter.accept(silence, silence.size) > 0)
    }

    @Test fun settlesOnSteadyNoiseAndFindsBarksAboveIt() {
        val segmenter = BarkEventSegmenter()
        val roomNoise = ShortArray(sound.size) { 600 }
        repeat(100) { assertEquals(0, segmenter.accept(roomNoise, roomNoise.size)) }
        repeat(4) { assertEquals(0, segmenter.accept(sound, sound.size)) }
        repeat(2) { assertEquals(0, segmenter.accept(roomNoise, roomNoise.size)) }
        assertTrue(segmenter.accept(roomNoise, roomNoise.size) > 0)
    }

    @Test fun rapidBarksEachEmitOnceWithoutARecognitionWindowOrCooldown() {
        val segmenter = BarkEventSegmenter()
        var candidates = 0
        repeat(20) {
            repeat(5) { if (segmenter.accept(sound, sound.size) > 0) candidates++ }
            repeat(3) { if (segmenter.accept(silence, silence.size) > 0) candidates++ }
            assertEquals(it + 1, candidates)
        }
        repeat(50) { assertEquals(0, segmenter.accept(silence, silence.size)) }
    }
}
