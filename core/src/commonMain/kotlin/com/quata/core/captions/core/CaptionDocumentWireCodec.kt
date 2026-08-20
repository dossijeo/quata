package com.quata.core.captions.core

object CaptionDocumentWireCodec {
    fun encodeWords(words: List<WordTiming>): String =
        words.joinToString("\n") { word ->
            listOf(
                sanitize(word.text),
                word.startMs.toString(),
                word.endMs.toString(),
                word.confidence.toString(),
            ).joinToString("\t")
        }

    fun decodeWords(value: String): List<WordTiming> =
        value.lineSequence()
            .mapNotNull { line ->
                val parts = line.split('\t')
                val text = parts.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val start = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
                val end = parts.getOrNull(2)?.toLongOrNull()?.coerceAtLeast(start + 1) ?: return@mapNotNull null
                val confidence = parts.getOrNull(3)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
                WordTiming(text = text, startMs = start.coerceAtLeast(0L), endMs = end, confidence = confidence)
            }
            .toList()

    fun encodeDocument(document: CaptionDocument): String =
        document.segments.joinToString("\n\n") { segment -> encodeWords(segment.words) }

    fun decodeDocument(value: String): CaptionDocument {
        val segments = value
            .split(Regex("\\n\\s*\\n"))
            .map { chunk -> CaptionSegment(decodeWords(chunk)) }
            .filter { it.words.isNotEmpty() }
        return CaptionDocument(segments)
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[\\t\\r\\n|]+"), " ").trim()
}
