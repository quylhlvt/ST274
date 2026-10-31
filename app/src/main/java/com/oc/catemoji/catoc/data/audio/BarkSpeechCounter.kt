package com.oc.catemoji.catoc.data.audio

/** Scores the first gâu/go partial immediately; alignment only prevents replay. */
class BarkSpeechCounter {
    private data class WordSpan(val startMs: Long, val endMs: Long, val barks: Int)

    var count: Int = 0
        private set
    private var countedInUtterance = 0
    private val detectedWords = mutableListOf<WordSpan>()

    fun accept(text: String, isFinal: Boolean, words: List<BarkSpeechWord> = emptyList(), partialText: String? = null) {
        words.forEach(::remember)
        // A usable word must not wait for confidence, alignment, a second partial,
        // or a phrase endpoint. Both transcripts belong to the same hypothesis.
        val matched = maxOf(BarkWordMatcher.countBarks(text),
            BarkWordMatcher.countBarks(partialText.orEmpty()), detectedWords.sumOf { it.barks })
        if (matched > countedInUtterance) {
            count += matched - countedInUtterance
            countedInUtterance = matched
        }
        if (isFinal) {
            countedInUtterance = 0
            detectedWords.clear()
        }
    }

    private fun remember(word: BarkSpeechWord) {
        if (!BarkWordMatcher.isBarkWord(word.text) || word.startMs < 0 || word.endMs <= word.startMs) return

        // Boundary revisions and spelling changes of the same sound must not
        // award it again. Overlapping spans are merged; adjacent sounds remain
        // separate even when there is no silence between them.
        val overlapping = detectedWords.filter { old ->
            val overlap = minOf(old.endMs, word.endMs) - maxOf(old.startMs, word.startMs)
            overlap > 0 && overlap * 2 >= minOf(old.endMs - old.startMs, word.endMs - word.startMs)
        }
        val previousBarks = overlapping.sumOf { it.barks }
        val span = WordSpan(
            minOf(word.startMs, overlapping.minOfOrNull { it.startMs } ?: word.startMs),
            maxOf(word.endMs, overlapping.maxOfOrNull { it.endMs } ?: word.endMs),
            maxOf(1, previousBarks),
        )
        detectedWords.removeAll(overlapping.toSet())
        detectedWords.add(span)
    }

}
