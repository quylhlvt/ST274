package com.oc.catemoji.catoc.data.audio

/** Confirms bark-only hypotheses and counts distinct sounds without replaying decoder revisions. */
class BarkSpeechCounter {
    private data class WordSpan(val startMs: Long, val endMs: Long, val barks: Int)

    var count: Int = 0
        private set
    private var countedInUtterance = 0
    private val detectedWords = mutableListOf<WordSpan>()
    private var repeatedPartial = ""
    private var repeatedPartialSinceMs = -1L

    fun accept(
        text: String,
        isFinal: Boolean,
        words: List<BarkSpeechWord> = emptyList(),
        partialText: String? = null,
        audioTimeMs: Long = -1L,
    ) {
        val liveTokens = BarkWordMatcher.tokens(partialText ?: text)
        val alignedTokens = BarkWordMatcher.tokens(text)
        // Do not union a live bark guess with an incompatible delayed hypothesis.
        // A sentence containing a bark-like word is still ordinary speech.
        val barkOnly = liveTokens.isNotEmpty() && liveTokens.all(BarkWordMatcher::isBarkWord) &&
            alignedTokens.all(BarkWordMatcher::isBarkWord) && words.all { BarkWordMatcher.isBarkWord(it.text) }
        val matched = if (barkOnly) {
            val confirmedWords = words.filter { it.confidence.isFinite() && it.confidence >= MIN_CONFIDENCE }
            confirmedWords.forEach(::remember)
            // A singleton fast partial has no confidence and often changes from go to
            // gõ when more audio arrives. Short sounds score at the acoustic endpoint.
            // A stable repeated bark phrase can score live, without waiting for silence.
            val live = liveTokens.joinToString(" ")
            if (live != repeatedPartial) {
                repeatedPartial = live
                repeatedPartialSinceMs = audioTimeMs
            }
            val stableRepeatedCount = if (!isFinal && liveTokens.size >= 2 && audioTimeMs >= 0 &&
                repeatedPartialSinceMs >= 0 && audioTimeMs - repeatedPartialSinceMs >= REPEATED_STABILITY_MS)
                liveTokens.size else 0
            val finalCount = if (isFinal && words.isEmpty()) liveTokens.size else 0
            maxOf(detectedWords.sumOf { it.barks }, stableRepeatedCount, finalCount)
        } else {
            repeatedPartial = ""
            repeatedPartialSinceMs = -1L
            0
        }
        if (matched > countedInUtterance) {
            count += matched - countedInUtterance
            countedInUtterance = matched
        }
        if (isFinal) {
            countedInUtterance = 0
            detectedWords.clear()
            repeatedPartial = ""
            repeatedPartialSinceMs = -1L
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

    private companion object {
        const val MIN_CONFIDENCE = 0.4f
        const val REPEATED_STABILITY_MS = 80L
    }

}
