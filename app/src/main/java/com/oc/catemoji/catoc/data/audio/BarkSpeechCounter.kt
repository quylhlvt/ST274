package com.oc.catemoji.catoc.data.audio

/** Counts partials immediately, including new word positions in a rolling hypothesis. */
class BarkSpeechCounter {
    private data class WordSpan(val startMs: Long, val endMs: Long, val barks: Int)

    var count: Int = 0
        private set
    private var countedInUtterance = 0
    private val detectedWords = mutableListOf<WordSpan>()

    fun accept(text: String, isFinal: Boolean, words: List<BarkSpeechWord> = emptyList()) {
        for (word in words) remember(word)
        // Text is retained as a fast fallback when alignment is not available.
        // Positions let the total grow even if a rolling partial still says
        // only "gô", but that word now belongs to a later sound.
        val matched = maxOf(BarkWordMatcher.countBarks(text), detectedWords.sumOf { it.barks })
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
        val barks = BarkWordMatcher.countBarks(word.text)
        if (barks == 0 || word.startMs < 0 || word.endMs <= word.startMs) return

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
            maxOf(barks, previousBarks),
        )
        detectedWords.removeAll(overlapping.toSet())
        detectedWords.add(span)
    }
}
