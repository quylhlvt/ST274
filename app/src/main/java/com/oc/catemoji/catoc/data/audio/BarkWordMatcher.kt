package com.oc.catemoji.catoc.data.audio

import java.text.Normalizer
import java.util.Locale

object BarkWordMatcher {
    private val latinMarks = Regex("([a-z])\\p{M}+")
    private val separators = Regex("[^\\p{L}\\p{M}\\p{N}]+")

    // Sound imitations, never verbs such as "sủa", "bark", "bellen" or "aboyer".
    // Sources and speech-model limitations: docs/bark-detection.md.
    private val wordsByLanguage = mapOf(
        "vi" to listOf("gau", "gao", "go"),
        "en" to listOf("woof", "arf", "ruff"),
        "fr" to listOf("ouaf", "ouah", "wouf", "waouh"),
        "de" to listOf("wau", "wuff"),
        "es" to listOf("guau"),
        "nl" to listOf("waf"),
        "sv" to listOf("voff"),
        "no" to listOf("voff"),
        "da" to listOf("vov", "vuf"),
        "ru" to listOf("gav", "гав"),
        "uk" to listOf("hav", "гав"),
        "pl" to listOf("hau"),
        "cs" to listOf("haf"),
        "hu" to listOf("vau"),
        "el" to listOf("gav", "ghav", "γαβ"),
        "tr" to listOf("hav", "hev"),
        "ar" to listOf("haw", "hau", "هاو"),
        "he" to listOf("hav"),
        "hi" to listOf("bho", "भौं"),
        "ja" to listOf("wan", "ワン", "わん"),
        "zh" to listOf("wang", "wāng", "汪"),
        "ko" to listOf("mung", "meong", "wal", "멍", "왈"),
        "th" to listOf("โฮ่ง"),
    )
    private val barkWords = wordsByLanguage.values.flatten().map(::normalize).toSet()

    fun normalize(text: String): String =
        Normalizer.normalize(
            Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(latinMarks, "$1")
                .replace('đ', 'd'),
            Normalizer.Form.NFC,
        )

    /** Original behavior: count matching tokens, including within a longer sentence. */
    fun countBarks(text: String): Int = normalize(text).split(separators).sumOf(::countToken)

    private fun countToken(token: String): Int {
        if (token in barkWords) return 1
        // Thai ๆ repeats the preceding sound; e.g. โฮ่งๆ is two barks.
        if (token.endsWith("ๆ")) return countToken(token.dropLast(1)) * 2
        // Some recognizers omit spaces: wanwan, gaugau, 汪汪, ワンワン, 멍멍.
        // Only a whole token made from repetitions of one known sound is accepted.
        return barkWords.firstNotNullOfOrNull { word ->
            if (token.length > word.length && token.length % word.length == 0 &&
                token.chunked(word.length).all { it == word }) token.length / word.length
            else null
        } ?: 0
    }

    fun isBark(text: String): Boolean = countBarks(text) > 0
}
