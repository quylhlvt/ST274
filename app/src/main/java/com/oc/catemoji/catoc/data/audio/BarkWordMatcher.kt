package com.oc.catemoji.catoc.data.audio

import java.text.Normalizer
import java.util.Locale

/** Explicit playable sounds; keep tone marks so unrelated variants stay separate. */
object BarkWordMatcher {
    private val separators = Regex("[^\\p{L}\\p{M}\\p{N}]+")
    private val acceptedWords = setOf("gâu", "go", "gô")

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFC)

    fun isBarkWord(text: String): Boolean = normalize(text) in acceptedWords

    fun tokens(text: String): List<String> = normalize(text).split(separators).filter { it.isNotEmpty() }

    fun countBarks(text: String): Int = tokens(text).count(::isBarkWord)

    fun isBark(text: String): Boolean = countBarks(text) > 0
}
