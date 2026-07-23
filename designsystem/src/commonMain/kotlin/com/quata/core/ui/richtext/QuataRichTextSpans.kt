package com.quata.core.ui.richtext

import kotlin.math.max
import kotlin.math.min

public sealed interface QuataSpanStyle {
    data object Bold : QuataSpanStyle
    data object Italic : QuataSpanStyle
    data object Underline : QuataSpanStyle
    data object Strike : QuataSpanStyle
    data object InlineCode : QuataSpanStyle
    data object Highlight : QuataSpanStyle
    data class Link(val url: String) : QuataSpanStyle
}

public data class QuataTextSpan(
    val start: Int,
    val end: Int,
    val style: QuataSpanStyle,
) {
    init {
        require(start >= 0)
        require(end >= start)
    }
}

public enum class QuataStyleStatus {
    FullyActive,
    Partial,
    Absent,
}

public object QuataSpanAlgorithms {
    fun normalize(spans: List<QuataTextSpan>, textLength: Int): List<QuataTextSpan> {
        if (spans.isEmpty()) return emptyList()
        val clamped = spans.mapNotNull { span ->
            val start = span.start.coerceIn(0, textLength)
            val end = span.end.coerceIn(start, textLength)
            if (start < end) QuataTextSpan(start, end, span.style) else null
        }
        return canonicalize(clamped)
    }

    fun adjustForEdit(
        spans: List<QuataTextSpan>,
        editStart: Int,
        deletedLength: Int,
        insertedLength: Int,
        textLength: Int,
    ): List<QuataTextSpan> {
        val editEnd = editStart + deletedLength
        val delta = insertedLength - deletedLength
        val adjusted = spans.mapNotNull { span ->
            val start = when {
                span.start < editStart -> span.start
                span.start >= editEnd -> span.start + delta
                else -> editStart
            }
            val end = when {
                span.end <= editStart -> span.end
                span.end > editEnd -> span.end + delta
                else -> editStart
            }
            if (start < end) QuataTextSpan(start, end, span.style) else null
        }
        return normalize(adjusted, textLength)
    }

    fun splitAt(spans: List<QuataTextSpan>, position: Int): Pair<List<QuataTextSpan>, List<QuataTextSpan>> {
        val first = mutableListOf<QuataTextSpan>()
        val second = mutableListOf<QuataTextSpan>()
        for (span in spans) {
            when {
                span.end <= position -> first += span
                span.start >= position -> second += span.copy(start = span.start - position, end = span.end - position)
                else -> {
                    first += span.copy(end = position)
                    second += span.copy(start = 0, end = span.end - position)
                }
            }
        }
        return first to second
    }

    fun mergeSpans(
        firstSpans: List<QuataTextSpan>,
        secondSpans: List<QuataTextSpan>,
        firstTextLength: Int,
        mergedTextLength: Int,
    ): List<QuataTextSpan> {
        val shifted = secondSpans.map { span ->
            span.copy(start = span.start + firstTextLength, end = span.end + firstTextLength)
        }
        return normalize(firstSpans + shifted, mergedTextLength)
    }

    fun applyStyle(
        spans: List<QuataTextSpan>,
        rangeStart: Int,
        rangeEnd: Int,
        style: QuataSpanStyle,
        textLength: Int,
    ): List<QuataTextSpan> {
        val start = min(rangeStart, rangeEnd).coerceIn(0, textLength)
        val end = max(rangeStart, rangeEnd).coerceIn(0, textLength)
        if (start >= end) return spans
        val base = if (style is QuataSpanStyle.Link) removeLinks(spans, start, end) else spans
        return normalize(base + QuataTextSpan(start, end, style), textLength)
    }

    fun removeStyle(
        spans: List<QuataTextSpan>,
        rangeStart: Int,
        rangeEnd: Int,
        style: QuataSpanStyle,
    ): List<QuataTextSpan> {
        return removeMatching(spans, rangeStart, rangeEnd) { existing ->
            operationMatches(existing.style, style)
        }
    }

    fun removeLinks(spans: List<QuataTextSpan>, rangeStart: Int, rangeEnd: Int): List<QuataTextSpan> {
        return removeMatching(spans, rangeStart, rangeEnd) { it.style is QuataSpanStyle.Link }
    }

    fun toggleStyle(
        spans: List<QuataTextSpan>,
        rangeStart: Int,
        rangeEnd: Int,
        style: QuataSpanStyle,
        textLength: Int,
    ): List<QuataTextSpan> {
        val start = min(rangeStart, rangeEnd)
        val end = max(rangeStart, rangeEnd)
        if (start >= end) return spans
        return when (queryStyleStatus(spans, start, end, style)) {
            QuataStyleStatus.FullyActive -> removeStyle(spans, start, end, style)
            else -> applyStyle(spans, start, end, style, textLength)
        }
    }

    fun queryStyleStatus(
        spans: List<QuataTextSpan>,
        rangeStart: Int,
        rangeEnd: Int,
        style: QuataSpanStyle,
    ): QuataStyleStatus {
        val start = max(0, min(rangeStart, rangeEnd))
        val end = max(0, max(rangeStart, rangeEnd))
        if (start == end) {
            val active = spans.any { operationMatches(it.style, style) && it.start <= start && it.end > start }
            return if (active) QuataStyleStatus.FullyActive else QuataStyleStatus.Absent
        }

        val ranges = spans
            .filter { operationMatches(it.style, style) }
            .map { max(it.start, start) to min(it.end, end) }
            .filter { it.first < it.second }
            .sortedBy { it.first }
        if (ranges.isEmpty()) return QuataStyleStatus.Absent

        var covered = 0
        var currentStart = ranges.first().first
        var currentEnd = ranges.first().second
        for (index in 1 until ranges.size) {
            val (nextStart, nextEnd) = ranges[index]
            if (nextStart <= currentEnd) {
                currentEnd = max(currentEnd, nextEnd)
            } else {
                covered += currentEnd - currentStart
                currentStart = nextStart
                currentEnd = nextEnd
            }
        }
        covered += currentEnd - currentStart
        return when {
            covered >= end - start -> QuataStyleStatus.FullyActive
            covered > 0 -> QuataStyleStatus.Partial
            else -> QuataStyleStatus.Absent
        }
    }

    private inline fun removeMatching(
        spans: List<QuataTextSpan>,
        rangeStart: Int,
        rangeEnd: Int,
        shouldRemove: (QuataTextSpan) -> Boolean,
    ): List<QuataTextSpan> {
        val start = max(0, min(rangeStart, rangeEnd))
        val end = max(0, max(rangeStart, rangeEnd))
        if (start >= end) return spans
        return canonicalize(spans.flatMap { span ->
            if (!shouldRemove(span)) {
                listOf(span)
            } else {
                buildList {
                    if (span.start < start) add(span.copy(end = min(span.end, start)))
                    if (span.end > end) add(span.copy(start = max(span.start, end)))
                }
            }
        })
    }

    private fun canonicalize(spans: List<QuataTextSpan>): List<QuataTextSpan> {
        if (spans.isEmpty()) return emptyList()
        val grouped = linkedMapOf<Any, MutableList<QuataTextSpan>>()
        for (span in normalizeLinkOverlaps(spans)) {
            grouped.getOrPut(kindKey(span.style)) { mutableListOf() } += span
        }
        val result = mutableListOf<QuataTextSpan>()
        for (group in grouped.values) {
            val sorted = group.sortedBy { it.start }
            var current = sorted.first()
            for (index in 1 until sorted.size) {
                val next = sorted[index]
                if (next.start <= current.end) {
                    current = current.copy(end = max(current.end, next.end), style = next.style)
                } else {
                    if (current.start < current.end) result += current
                    current = next
                }
            }
            if (current.start < current.end) result += current
        }
        return result.sortedWith(compareBy({ it.start }, { it.end }))
    }

    private fun normalizeLinkOverlaps(spans: List<QuataTextSpan>): List<QuataTextSpan> {
        val links = spans.filter { it.style is QuataSpanStyle.Link }
        if (links.size <= 1) return spans
        val nonLinks = spans.filterNot { it.style is QuataSpanStyle.Link }
        val resolved = mutableListOf<QuataTextSpan>()
        for (link in links) {
            val clipped = resolved.flatMap { existing ->
                if (existing.end <= link.start || existing.start >= link.end) {
                    listOf(existing)
                } else {
                    buildList {
                        if (existing.start < link.start) add(existing.copy(end = link.start))
                        if (existing.end > link.end) add(existing.copy(start = link.end))
                    }
                }
            }
            resolved.clear()
            resolved += clipped
            resolved += link
        }
        return nonLinks + resolved
    }

    private fun operationMatches(existing: QuataSpanStyle, requested: QuataSpanStyle): Boolean {
        return if (requested is QuataSpanStyle.Link) {
            existing is QuataSpanStyle.Link
        } else {
            kindKey(existing) == kindKey(requested)
        }
    }

    private fun kindKey(style: QuataSpanStyle): Any = when (style) {
        QuataSpanStyle.Bold -> QuataSpanStyle.Bold
        QuataSpanStyle.Italic -> QuataSpanStyle.Italic
        QuataSpanStyle.Underline -> QuataSpanStyle.Underline
        QuataSpanStyle.Strike -> QuataSpanStyle.Strike
        QuataSpanStyle.InlineCode -> QuataSpanStyle.InlineCode
        QuataSpanStyle.Highlight -> QuataSpanStyle.Highlight
        is QuataSpanStyle.Link -> style
    }
}
