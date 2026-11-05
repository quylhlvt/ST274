package com.oc.catemoji.catoc.data.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

/** Small RBF classifier over acoustic embeddings; no vocabulary or language is involved. */
internal class BarkEmbeddingHead(bytes: ByteArray) {
    val features: Int
    private val vectors: Int
    private val gamma: Float
    private val bias: Float
    private val threshold: Float
    private val coefficients: FloatArray
    private val support: FloatArray

    init {
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size >= 24 && data.int == 0x31484B42) { "Invalid bark classifier header" }
        features = data.int
        vectors = data.int
        gamma = data.float
        bias = data.float
        threshold = data.float
        require(features in 1..4096 && vectors in 1..10_000 && gamma.isFinite() && gamma > 0f &&
            bias.isFinite() && threshold.isFinite() &&
            bytes.size.toLong() == 24L + vectors.toLong() * (features + 1) * Float.SIZE_BYTES
        ) { "Invalid bark classifier dimensions" }
        coefficients = FloatArray(vectors) { data.float }
        support = FloatArray(vectors * features) { data.float }
    }

    fun score(embedding: FloatArray): Double {
        require(embedding.size == features)
        if (embedding.any { !it.isFinite() }) return Double.NEGATIVE_INFINITY
        var score = bias.toDouble()
        var offset = 0
        for (vector in 0 until vectors) {
            var distance = 0.0
            for (feature in embedding.indices) {
                val difference = embedding[feature].toDouble() - support[offset++]
                distance += difference * difference
            }
            score += coefficients[vector] * exp(-gamma * distance)
        }
        return score
    }

    val passThreshold get() = threshold

    fun isBark(embedding: FloatArray): Boolean {
        val s = score(embedding)
        return s.isFinite() && s >= threshold
    }
}
