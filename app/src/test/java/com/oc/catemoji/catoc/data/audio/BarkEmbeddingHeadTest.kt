package com.oc.catemoji.catoc.data.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class BarkEmbeddingHeadTest {
    private fun head(positive: Float = 1f) = ByteBuffer.allocate(36)
        .order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0x31484B42); putInt(2); putInt(1)
            putFloat(1f); putFloat(0f); putFloat(0.5f)
            putFloat(positive); putFloat(0f); putFloat(0f)
        }.array()

    @Test fun nonlinearDistanceAndCoefficientSignsDetermineAcceptance() {
        val classifier = BarkEmbeddingHead(head())
        assertTrue(classifier.isBark(floatArrayOf(0f, 0f)))
        assertTrue(classifier.isBark(floatArrayOf(0.5f, 0f)))
        assertFalse(classifier.isBark(floatArrayOf(1f, 1f)))
        assertFalse(BarkEmbeddingHead(head(-1f)).isBark(floatArrayOf(0f, 0f)))
    }

    @Test fun invalidEmbeddingNeverScores() {
        val classifier = BarkEmbeddingHead(head())
        assertFalse(classifier.isBark(floatArrayOf(Float.NaN, 0f)))
        assertFalse(classifier.isBark(floatArrayOf(0f, Float.POSITIVE_INFINITY)))
    }

    @Test fun truncatedOrIncompatibleAssetsFailAtLoading() {
        for (bytes in listOf(byteArrayOf(), head().copyOf(32), head().apply { this[0] = 0 })) {
            assertThrows(IllegalArgumentException::class.java) { BarkEmbeddingHead(bytes) }
        }
    }
}
