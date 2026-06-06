package com.quata.core.captions.core

data class CaptionSegment(
    val words: List<WordTiming>
) {
    val startMs: Long = words.minOfOrNull { it.startMs } ?: 0L
    val endMs: Long = words.maxOfOrNull { it.endMs } ?: startMs
    val text: String = words.joinToString(" ") { it.text }

    fun contains(timeMs: Long): Boolean = timeMs in startMs..endMs
}

data class CaptionDocument(
    val segments: List<CaptionSegment>
) {
    val isEmpty: Boolean get() = segments.isEmpty()

    fun segmentAt(timeMs: Long): CaptionSegment? =
        segments.firstOrNull { it.contains(timeMs) }

    fun trimTo(startMs: Long, endMs: Long): CaptionDocument {
        val trimmedWords = segments
            .flatMap { it.words }
            .filter { it.endMs >= startMs && it.startMs <= endMs }
            .map {
                it.copy(
                    startMs = (it.startMs - startMs).coerceAtLeast(0L),
                    endMs = (it.endMs - startMs).coerceAtLeast(0L)
                )
            }
        return fromWords(trimmedWords)
    }

    companion object {
        fun fromWords(words: List<WordTiming>): CaptionDocument {
            if (words.isEmpty()) return CaptionDocument(emptyList())
            val segments = mutableListOf<CaptionSegment>()
            val current = mutableListOf<WordTiming>()
            words.sortedBy { it.startMs }.forEach { word ->
                val previous = current.lastOrNull()
                val shouldBreak = previous != null &&
                    (word.startMs - previous.endMs > CaptionSegmentMaxGapMs ||
                        current.size >= CaptionSegmentMaxWords ||
                        word.endMs - current.first().startMs > CaptionSegmentMaxDurationMs ||
                        previous.text.endsWith(".") ||
                        previous.text.endsWith("?") ||
                        previous.text.endsWith("!"))
                if (shouldBreak) {
                    segments += CaptionSegment(current.toList())
                    current.clear()
                }
                current += word
            }
            if (current.isNotEmpty()) segments += CaptionSegment(current.toList())
            return CaptionDocument(segments)
        }

        fun placeholder(text: String): CaptionDocument {
            val cleanWords = text.split(Regex("\\s+")).filter { it.isNotBlank() }
            val words = cleanWords.mapIndexed { index, word ->
                val start = index * 360L
                WordTiming(
                    text = word,
                    startMs = start,
                    endMs = start + 320L,
                    confidence = 1f
                )
            }
            return fromWords(words)
        }
    }
}

private const val CaptionSegmentMaxWords = 5
private const val CaptionSegmentMaxDurationMs = 2_400L
private const val CaptionSegmentMaxGapMs = 700L
