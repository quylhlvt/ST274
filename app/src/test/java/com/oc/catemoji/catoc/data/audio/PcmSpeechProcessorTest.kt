package com.oc.catemoji.catoc.data.audio

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.*
import org.junit.Test

class PcmSpeechProcessorTest {
    @Test fun silenceStaysSilentAndUnreadBufferTailIsUntouched() {
        val processor = PcmSpeechProcessor(16_000)
        val block = ShortArray(640)
        repeat(25) {
            assertEquals(0f, processor.process(block, block.size), 0f)
            assertTrue(block.all { it == 0.toShort() })
        }
        block.fill(1234, 320)
        processor.process(block, 320)
        assertTrue(block.take(320).all { it == 0.toShort() })
        assertTrue(block.drop(320).all { it == 1234.toShort() })
    }

    @Test fun removesDcAndAttenuatesLowRumbleWhilePreservingVoiceBand() {
        fun outputRms(frequency: Double): Double {
            val processor = PcmSpeechProcessor(16_000)
            processor.process(ShortArray(16_000), 16_000)
            val output = ShortArray(16_000) { (6000 * sin(2 * PI * frequency * it / 16_000) + 2000).toInt().toShort() }
            processor.process(output, output.size)
            return rms(output.copyOfRange(8000, output.size))
        }
        assertTrue(outputRms(25.0) < outputRms(500.0) * 0.4)
        val dc = ShortArray(16_000) { 8000 }
        PcmSpeechProcessor(16_000).process(dc, dc.size)
        assertTrue(rms(dc.copyOfRange(8000, dc.size)) < 2.0)
    }

    @Test fun quietVoicedSignalIsBoostedWithoutChangingItsInputMeter() {
        val processor = PcmSpeechProcessor(16_000)
        val block = ShortArray(640)
        repeat(25) { processor.process(block, block.size) }
        var level = 0f
        repeat(30) {
            for (i in block.indices) block[i] = (1000 * sin(2 * PI * 500 * i / 16_000)).toInt().toShort()
            level = processor.process(block, block.size)
        }
        assertEquals((1000 / sqrt(2.0) / 32768).toFloat(), level, 0.001f)
        assertTrue(rms(block) > 1700)
    }

    @Test fun steadyBackgroundIsNotAmplifiedByVoiceGain() {
        val processor = PcmSpeechProcessor(16_000)
        val noise = ShortArray(640) { (1000 * sin(2 * PI * 1500 * it / 16_000)).toInt().toShort() }
        var output = noise.copyOf()
        repeat(50) {
            output = noise.copyOf()
            processor.process(output, output.size)
        }
        assertTrue(rms(output) <= rms(noise) * 1.1)
    }

    @Test fun conditionedSamplesAreIndependentOfReadBlockBoundaries() {
        // The high-pass filter must retain state even when AudioRecord returns short reads.
        val input = ShortArray(1600) { (100 * sin(2 * PI * 400 * it / 16_000)).toInt().toShort() }
        val full = input.copyOf()
        PcmSpeechProcessor(16_000).process(full, full.size)
        val split = PcmSpeechProcessor(16_000)
        val first = input.copyOfRange(0, 317)
        val second = input.copyOfRange(317, input.size)
        split.process(first, first.size)
        split.process(second, second.size)
        assertArrayEquals(full, first + second)
    }

    private fun rms(pcm: ShortArray) = sqrt(pcm.sumOf { it.toDouble() * it } / pcm.size)
}
