package com.quata.core.ui.richtext

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.math.max
import kotlin.math.min

@Stable
public class QuataBlockTextStates {
    private val states = mutableStateMapOf<String, TextFieldValue>()
    private val pendingProgrammaticCommits = mutableMapOf<String, String>()
    var generation: Int by mutableIntStateOf(0)
        private set

    fun getOrCreate(
        blockId: String,
        initialText: String,
        initialSelection: TextRange = TextRange(initialText.length),
    ): TextFieldValue {
        return states.getOrPut(blockId) {
            TextFieldValue(initialText, selection = initialSelection.clampTo(initialText.length))
        }
    }

    fun get(blockId: String): TextFieldValue? = states[blockId]

    fun set(blockId: String, value: TextFieldValue, programmatic: Boolean = false) {
        val safe = value.copy(selection = value.selection.clampTo(value.text.length))
        val previous = states[blockId]
        states[blockId] = safe
        if (programmatic && previous?.text != safe.text) {
            pendingProgrammaticCommits[blockId] = safe.text
        }
    }

    fun setText(blockId: String, text: String, selection: TextRange = TextRange(text.length)) {
        set(blockId, TextFieldValue(text, selection = selection.clampTo(text.length)), programmatic = true)
    }

    fun setSelection(blockId: String, selection: TextRange) {
        val current = states[blockId] ?: return
        states[blockId] = current.copy(selection = selection.clampTo(current.text.length))
    }

    fun replaceRange(
        blockId: String,
        start: Int,
        endExclusive: Int,
        replacement: String,
        cursorPositionAfter: Int? = null,
    ): TextFieldValue? {
        val current = states[blockId] ?: return null
        val safeStart = start.coerceIn(0, current.text.length)
        val safeEnd = endExclusive.coerceIn(safeStart, current.text.length)
        val nextText = current.text.substring(0, safeStart) + replacement + current.text.substring(safeEnd)
        val cursor = (cursorPositionAfter ?: (safeStart + replacement.length)).coerceIn(0, nextText.length)
        val next = TextFieldValue(nextText, selection = TextRange(cursor))
        set(blockId, next, programmatic = true)
        return next
    }

    fun mergeInto(sourceId: String, targetId: String): Int? {
        val source = states[sourceId] ?: return null
        val target = states[targetId] ?: return null
        val targetLength = target.text.length
        setText(targetId, target.text + source.text, TextRange(targetLength))
        remove(sourceId)
        return targetLength
    }

    fun consumeProgrammaticCommit(blockId: String): String? = pendingProgrammaticCommits.remove(blockId)

    fun remove(blockId: String) {
        states.remove(blockId)
        pendingProgrammaticCommits.remove(blockId)
    }

    fun cleanup(existingBlockIds: Set<String>) {
        val stale = states.keys - existingBlockIds
        stale.forEach(::remove)
        val staleCommits = pendingProgrammaticCommits.keys - existingBlockIds
        staleCommits.forEach { pendingProgrammaticCommits.remove(it) }
    }

    fun clear() {
        states.clear()
        pendingProgrammaticCommits.clear()
        generation++
    }
}

@Stable
public class QuataBlockSpanStates {
    private val states = mutableStateMapOf<String, List<QuataTextSpan>>()
    private val pendingStyles = mutableStateMapOf<String, Set<QuataSpanStyle>>()
    var generation: Int by mutableIntStateOf(0)
        private set

    fun getOrCreate(
        blockId: String,
        initialSpans: List<QuataTextSpan> = emptyList(),
        textLength: Int,
    ): List<QuataTextSpan> {
        require(textLength >= 0) { "textLength must be non-negative" }
        return states.getOrPut(blockId) {
            QuataSpanAlgorithms.normalize(initialSpans, textLength)
        }
    }

    fun getSpans(blockId: String): List<QuataTextSpan> = states[blockId].orEmpty()

    fun set(blockId: String, spans: List<QuataTextSpan>, textLength: Int) {
        states[blockId] = QuataSpanAlgorithms.normalize(spans, textLength)
    }

    fun adjustForUserEdit(blockId: String, edit: TextEdit, textLength: Int) {
        states[blockId] = QuataSpanAlgorithms.adjustForEdit(
            spans = getSpans(blockId),
            editStart = edit.start,
            deletedLength = edit.deletedLength,
            insertedLength = edit.insertedLength,
            textLength = textLength,
        )
    }

    fun split(sourceBlockId: String, newBlockId: String, position: Int) {
        val (first, second) = QuataSpanAlgorithms.splitAt(getSpans(sourceBlockId), position)
        states[sourceBlockId] = first
        states[newBlockId] = second
        pendingStyles.remove(sourceBlockId)
        pendingStyles.remove(newBlockId)
    }

    fun mergeInto(sourceId: String, targetId: String, targetTextLength: Int, mergedTextLength: Int) {
        states[targetId] = QuataSpanAlgorithms.mergeSpans(
            firstSpans = getSpans(targetId),
            secondSpans = getSpans(sourceId),
            firstTextLength = targetTextLength,
            mergedTextLength = mergedTextLength,
        )
        remove(sourceId)
        pendingStyles.remove(targetId)
    }

    fun applyStyle(blockId: String, start: Int, end: Int, style: QuataSpanStyle, textLength: Int) {
        states[blockId] = QuataSpanAlgorithms.applyStyle(getSpans(blockId), start, end, style, textLength)
    }

    fun removeStyle(blockId: String, start: Int, end: Int, style: QuataSpanStyle) {
        states[blockId] = QuataSpanAlgorithms.removeStyle(getSpans(blockId), start, end, style)
    }

    fun removeLinkSpans(blockId: String, start: Int, end: Int) {
        states[blockId] = QuataSpanAlgorithms.removeLinks(getSpans(blockId), start, end)
    }

    fun toggleStyle(blockId: String, start: Int, end: Int, style: QuataSpanStyle, textLength: Int) {
        states[blockId] = QuataSpanAlgorithms.toggleStyle(getSpans(blockId), start, end, style, textLength)
    }

    fun queryStyleStatus(blockId: String, start: Int, end: Int, style: QuataSpanStyle): QuataStyleStatus {
        return QuataSpanAlgorithms.queryStyleStatus(getSpans(blockId), start, end, style)
    }

    fun activeStylesAt(blockId: String, position: Int): Set<QuataSpanStyle> {
        return getSpans(blockId)
            .filter { position >= it.start && position < it.end }
            .mapTo(mutableSetOf()) { it.style }
    }

    fun getPendingStyles(blockId: String): Set<QuataSpanStyle>? = pendingStyles[blockId]

    fun setPendingStyles(blockId: String, styles: Set<QuataSpanStyle>) {
        pendingStyles[blockId] = styles
    }

    fun clearPendingStyles(blockId: String) {
        pendingStyles.remove(blockId)
    }

    fun resolveStylesForInsertion(blockId: String, position: Int): Set<QuataSpanStyle> {
        val pending = pendingStyles.remove(blockId)
        if (pending != null) return pending.insertionContinuableStyles()
        if (position <= 0) return emptySet()
        return activeStylesAt(blockId, position - 1).insertionContinuableStyles()
    }

    fun remove(blockId: String) {
        states.remove(blockId)
        pendingStyles.remove(blockId)
    }

    fun cleanup(existingBlockIds: Set<String>) {
        val stale = states.keys - existingBlockIds
        stale.forEach(::remove)
        val stalePending = pendingStyles.keys - existingBlockIds
        stalePending.forEach { pendingStyles.remove(it) }
    }

    fun clear() {
        states.clear()
        pendingStyles.clear()
        generation++
    }

    private fun Set<QuataSpanStyle>.insertionContinuableStyles(): Set<QuataSpanStyle> {
        return filterNotTo(mutableSetOf()) { it is QuataSpanStyle.Link }
    }
}

public data class QuataLinkTarget(
    val blockId: String,
    val range: TextRange,
    val url: String,
    val text: String,
)

public object QuataLinkHitTester {
    fun resolve(block: QuataRichTextBlock, offset: Int): QuataLinkTarget? {
        val safeOffset = offset.coerceIn(0, block.text.text.length)
        val span = block.spans
            .filter { it.style is QuataSpanStyle.Link && safeOffset >= it.start && safeOffset <= it.end }
            .minByOrNull { it.end - it.start }
            ?: return null
        val url = (span.style as QuataSpanStyle.Link).url
        return QuataLinkTarget(
            blockId = block.id,
            range = TextRange(span.start, span.end),
            url = url,
            text = block.text.text.substring(span.start.coerceIn(0, block.text.text.length), span.end.coerceIn(0, block.text.text.length)),
        )
    }
}

private fun TextRange.clampTo(length: Int): TextRange {
    return TextRange(start.coerceIn(0, length), end.coerceIn(0, length))
}
