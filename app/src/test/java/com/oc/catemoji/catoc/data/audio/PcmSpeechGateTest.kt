package com.oc.catemoji.catoc.data.audio

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmSpeechGateTest {
    @Test fun stationaryBroadbandHissNeverOpensTheDecoder() {
        val gate = PcmSpeechGate(16_000, 640)
        val random = Random(4382)
        repeat(150) {
            val hiss = ShortArray(640) { random.nextInt(-8000, 8001).toShort() }
            assertEquals(0, gate.process(hiss, hiss.size))
        }
    }

    @Test fun quietPeriodicVoiceOpensWithoutNoiseCalibration() {
        val gate = PcmSpeechGate(16_000, 640)
        val voice = tone(640, amplitude = 150)
        assertEquals(640, gate.process(voice, voice.size))
        assertArrayEquals(voice, gate.buffer.copyOf(640))
    }

    @Test fun voicedHarmonicsCanOpenOverBroadbandNoise() {
        val gate = PcmSpeechGate(16_000, 640)
        val random = Random(73)
        val voice = ShortArray(640) { index ->
            val phase = 2 * PI * 180 * index / 16_000
            (3000 * sin(phase) + 1200 * sin(2 * phase) +
                600 * sin(3 * phase) + random.nextInt(-1800, 1801)).toInt().toShort()
        }
        assertEquals(640, gate.process(voice, voice.size))
    }

    @Test fun onsetIncludesTheLastTwoHundredMillisecondsInChronologicalOrder() {
        val gate = PcmSpeechGate(16_000, 640)
        val random = Random(553)
        val history = ArrayList<Short>()
        repeat(8) {
            val hiss = ShortArray(640) { random.nextInt(-4000, 4001).toShort() }
            history.addAll(hiss.toList())
            assertEquals(0, gate.process(hiss, hiss.size))
        }
        val voice = tone(640)
        val expected = history.takeLast(3200).toShortArray() + voice
        assertEquals(expected.size, gate.process(voice, voice.size))
        assertArrayEquals(expected, gate.buffer.copyOf(expected.size))
    }

    @Test fun shortReadsAccumulateEnoughAnalysisWithoutDroppingTheOnset() {
        val gate = PcmSpeechGate(16_000, 640)
        val voice = tone(640)
        val pieces = listOf(voice.copyOfRange(0, 101), voice.copyOfRange(101, 370),
            voice.copyOfRange(370, 639), voice.copyOfRange(639, 640))
        pieces.dropLast(1).forEach { assertEquals(0, gate.process(it, it.size)) }
        assertEquals(640, gate.process(pieces.last(), 1))
        assertArrayEquals(voice, gate.buffer.copyOf(640))
    }

    @Test fun emptyReadsAndUnreadInputTailDoNotEnterTheAnalysisOrHistory() {
        val gate = PcmSpeechGate(16_000, 640)
        val invalidTail = tone(640)
        assertEquals(0, gate.process(invalidTail, 0))
        val waiting = ShortArray(640) { if (it < 320) 0 else invalidTail[it] }
        val original = waiting.copyOf()
        assertEquals(0, gate.process(waiting, 320))
        assertArrayEquals(original, waiting)
        val voice = tone(640)
        assertEquals(960, gate.process(voice, 640))
        assertArrayEquals(ShortArray(320) + voice, gate.buffer.copyOf(960))
    }

    @Test fun activeGatePassesHeldAudioAndQuietGapsUnchangedUsingTheSameBuffer() {
        val gate = PcmSpeechGate(16_000, 640)
        val reused = gate.buffer
        assertEquals(640, gate.process(tone(640), 640))
        val random = Random(91)
        repeat(20) {
            val current = ShortArray(640) { random.nextInt(-32768, 32768).toShort() }
            val original = current.copyOf()
            assertEquals(317, gate.process(current, 317))
            assertArrayEquals(original.copyOf(317), gate.buffer.copyOf(317))
            assertArrayEquals(original, current)
            assertTrue(reused === gate.buffer)
        }
        assertEquals(640, gate.process(ShortArray(640), 640))
        assertArrayEquals(ShortArray(640), gate.buffer.copyOf(640))
    }

    @Test fun resetClosesTheGateAndDropsOldPrerollAndAnalysis() {
        val gate = PcmSpeechGate(16_000, 640)
        assertEquals(0, gate.process(ShortArray(640), 640))
        assertEquals(1280, gate.process(tone(640), 640))
        gate.reset()
        assertArrayEquals(ShortArray(gate.buffer.size), gate.buffer)
        val voice = tone(640)
        assertEquals(640, gate.process(voice, 640))
        assertArrayEquals(voice, gate.buffer.copyOf(640))
        gate.reset()
        assertEquals(0, gate.process(ShortArray(640), 640))
    }

    @Test fun nearSilenceAndDcCannotLookLikeVoicedActivity() {
        val gate = PcmSpeechGate(16_000, 640)
        repeat(10) { assertEquals(0, gate.process(tone(640, amplitude = 50), 640)) }
        gate.reset()
        repeat(10) { assertEquals(0, gate.process(ShortArray(640) { 10_000 }, 640)) }
    }

    @Test fun voicePitchRangeWorksAtDifferentSampleRates() {
        for (sampleRate in listOf(8_000, 16_000, 48_000)) {
            val count = sampleRate * 40 / 1000
            for (frequency in listOf(80, 180, 500)) {
                val gate = PcmSpeechGate(sampleRate, count)
                val voice = tone(count, sampleRate = sampleRate, frequency = frequency)
                assertEquals(count, gate.process(voice, count))
            }
        }
    }

    private fun tone(
        count: Int,
        sampleRate: Int = 16_000,
        frequency: Int = 200,
        amplitude: Int = 3000,
    ) = ShortArray(count) { (amplitude * sin(2 * PI * frequency * it / sampleRate)).toInt().toShort() }
}
