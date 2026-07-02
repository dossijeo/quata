package com.quata.core.ui.richtext

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.math.max
import kotlin.math.min

internal sealed interface QuataRichTextAction {
    fun reduce(state: QuataRichTextDocumentState): QuataRichTextDocumentState
}

internal data class QuataRichTextDocumentState(
    val blocks: List<QuataRichTextBlockModel>,
    val selectedBlockId: String?,
    val selectedBlockIds: List<String>,
    val selectionAnchorId: String?,
    val pendingInlineStyles: Map<String, Set<InlineFormat>>,
    val lastSelection: Map<String, TextRange>,
)

internal data class QuataRichTextBlockModel(
    val id: String,
    val type: RichTextBlockType,
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val checked: Boolean,
    val indentLevel: Int,
    val spans: List<QuataTextSpan>,
)

internal data class InsertRichTextBlock(
    val block: QuataRichTextBlockModel,
    val atIndex: Int,
) : QuataRichTextAction {
    override fun reduce(state: QuataRichTextDocumentState): QuataRichTextDocumentState {
        return InsertRichTextBlocks(
            blocks = listOf(block),
            atIndex = atIndex,
            selectedBlockId = block.id,
        ).reduce(state)
    }
}

internal data class InsertRichTextBlocks(
    val blocks: List<QuataRichTextBlockModel>,
    val atIndex: Int,
    val selectedBlockId: String? = blocks.lastOrNull()?.id,
) : QuataRichTextAction {
    override fun reduce(state: QuataRichTextDocumentState): QuataRichTextDocumentState {
        if (blocks.isEmpty()) return state
        val normalizedBlocks = blocks.map { it.normalized() }
        val index = atIndex.coerceIn(0, state.blocks.size)
        val nextBlocks = state.blocks.toMutableList().apply { addAll(index, normalizedBlocks) }
        val selectedId = selectedBlockId?.takeIf { id -> normalizedBlocks.any { it.id == id } }
            ?: normalizedBlocks.last().id
        return state.copy(
            blocks = nextBlocks,
            selectedBlockId = selectedId,
            selectedBlockIds = listOf(selectedId),
            selectionAnchorId = selectedId,
            lastSelection = state.lastSelection + normalizedBlocks.associate { model ->
                model.id to model.selectionRange()
            },
        )
    }
}

internal data class DeleteRichTextBlocks(
    val blockIds: Set<String>,
) : QuataRichTextAction {
    override fun reduce(state: QuataRichTextDocumentState): QuataRichTextDocumentState {
        if (blockIds.isEmpty()) return state
        val firstRemovedIndex = state.blocks.indexOfFirst { it.id in blockIds }.takeIf { it >= 0 } ?: return state
        val nextBlocks = state.blocks.filterNot { it.id in blockIds }.ifEmpty { listOf(emptyRichTextBlockModel()) }
        val selectedIndex = firstRemovedIndex.coerceIn(0, nextBlocks.lastIndex)
        val selectedId = nextBlocks[selectedIndex].id
        return state.copy(
            blocks = nextBlocks,
            selectedBlockId = selectedId,
            selectedBlockIds = listOf(selectedId),
            selectionAnchorId = selectedId,
            pendingInlineStyles = state.pendingInlineStyles - blockIds,
            lastSelection = state.lastSelection.filterKeys { it !in blockIds } +
                (selectedId to nextBlocks[selectedIndex].selectionRange()),
        )
    }
}

internal data class ReorderRichTextBlocks(
    val blocks: List<QuataRichTextBlockModel>,
    val selectedBlockId: String?,
    val selectedBlockIds: List<String>,
) : QuataRichTextAction {
    override fun reduce(state: QuataRichTextDocumentState): QuataRichTextDocumentState {
        if (blocks.isEmpty()) return state
        val normalizedBlocks = blocks.map { it.normalized() }
        val idSet = normalizedBlocks.map { it.id }.toSet()
        if (idSet.size != normalizedBlocks.size) return state
        val safeSelectedIds = selectedBlockIds.filter { it in idSet }
        val safeSelectedId = selectedBlockId?.takeIf { it in idSet }
            ?: safeSelectedIds.firstOrNull()
            ?: normalizedBlocks.first().id
        return state.copy(
            blocks = normalizedBlocks,
            selectedBlockId = safeSelectedId,
            selectedBlockIds = safeSelectedIds.ifEmpty { listOf(safeSelectedId) },
            selectionAnchorId = safeSelectedId,
            lastSelection = state.lastSelection.filterKeys { it in idSet } +
                normalizedBlocks.associate { model -> model.id to model.selectionRange() },
            pendingInlineStyles = state.pendingInlineStyles.filterKeys { it in idSet },
        )
    }
}

internal data class ConvertRichTextBlockType(
    val blockId: String,
    val type: RichTextBlockType,
) : QuataRichTextAction {
    override fun reduce(state: QuataRichTextDocumentState): QuataRichTextDocumentState {
        return state.mapBlock(blockId) { block ->
            val nextType = if (block.type == type) {
                if (type == RichTextBlockType.Divider) RichTextBlockType.Paragraph else RichTextBlockType.Paragraph
            } else {
                type
            }
            val nextText = if (nextType == RichTextBlockType.Divider) "" else block.text
            val nextSelection = block.selectionRange().clampTo(nextText)
            val supportsSpans = nextType != RichTextBlockType.Code && nextType != RichTextBlockType.Divider
            block.copy(
                type = nextType,
                text = nextText,
                selectionStart = nextSelection.start,
                selectionEnd = nextSelection.end,
                checked = if (nextType == RichTextBlockType.Todo) block.checked else false,
                indentLevel = 0,
                spans = if (supportsSpans) QuataSpanAlgorithms.normalize(block.spans, nextText.length) else emptyList(),
            )
        }
    }
}

internal data class SetRichTextTodoChecked(
    val blockId: String,
    val checked: Boolean,
) : QuataRichTextAction {
    override fun reduce(state: QuataRichTextDocumentState): QuataRichTextDocumentState {
        return state.mapBlock(blockId) { block ->
            if (block.type == RichTextBlockType.Todo) block.copy(checked = checked) else block
        }
    }
}

internal data class ShiftRichTextIndent(
    val blockId: String,
    val delta: Int,
    val maxIndent: Int,
) : QuataRichTextAction {
    override fun reduce(state: QuataRichTextDocumentState): QuataRichTextDocumentState {
        return state.mapBlock(blockId) { block ->
            block.copy(indentLevel = 0)
        }
    }
}

internal data class ToggleRichTextHeading(
    val blockId: String,
    val level: Int,
) : QuataRichTextAction {
    override fun reduce(state: QuataRichTextDocumentState): QuataRichTextDocumentState {
        val targetType = when (level.coerceIn(1, 6)) {
            1 -> RichTextBlockType.Heading1
            2 -> RichTextBlockType.Heading2
            3 -> RichTextBlockType.Heading3
            4 -> RichTextBlockType.Heading4
            5 -> RichTextBlockType.Heading5
            else -> RichTextBlockType.Heading6
        }
        return state.mapBlock(blockId) { block ->
            block.copy(type = if (block.type == targetType) RichTextBlockType.Paragraph else targetType)
        }
    }
}

internal data class UpdateRichTextBlockInline(
    val blockId: String,
    val text: String,
    val selection: TextRange,
    val spans: List<QuataTextSpan>,
) : QuataRichTextAction {
    override fun reduce(state: QuataRichTextDocumentState): QuataRichTextDocumentState {
        val safeSelection = selection.clampTo(text)
        return state.mapBlock(blockId) { block ->
            block.copy(
                text = text,
                selectionStart = safeSelection.start,
                selectionEnd = safeSelection.end,
                spans = QuataSpanAlgorithms.normalize(spans, text.length),
            )
        }.copy(
            selectedBlockId = blockId,
            selectedBlockIds = state.selectedBlockIds.ifEmpty { listOf(blockId) },
            lastSelection = state.lastSelection + (blockId to safeSelection),
        )
    }
}

private fun QuataRichTextDocumentState.mapBlock(
    blockId: String,
    transform: (QuataRichTextBlockModel) -> QuataRichTextBlockModel,
): QuataRichTextDocumentState {
    var changed = false
    val nextBlocks = blocks.map { block ->
        if (block.id == blockId) {
            changed = true
            transform(block).normalized()
        } else {
            block
        }
    }
    return if (changed) copy(blocks = nextBlocks) else this
}

internal fun QuataRichTextBlock.toModel(): QuataRichTextBlockModel {
    return QuataRichTextBlockModel(
        id = id,
        type = type,
        text = text.text,
        selectionStart = text.selection.start,
        selectionEnd = text.selection.end,
        checked = isChecked,
        indentLevel = 0,
        spans = spans,
    ).normalized()
}

internal fun QuataRichTextBlockModel.toBlock(): QuataRichTextBlock {
    val block = QuataRichTextBlock(
        id = id,
        type = type,
        text = text,
        checked = checked,
        indent = 0,
        spans = spans,
    )
    block.text = TextFieldValue(text, selection = selectionRange().clampTo(text))
    return block
}

internal fun QuataRichTextBlockModel.selectionRange(): TextRange {
    return TextRange(selectionStart.coerceIn(0, text.length), selectionEnd.coerceIn(0, text.length))
}

private fun QuataRichTextBlockModel.normalized(): QuataRichTextBlockModel {
    val supportsSpans = type != RichTextBlockType.Code && type != RichTextBlockType.Divider
    val normalizedText = if (type == RichTextBlockType.Divider) "" else text
    val range = selectionRange().clampTo(normalizedText)
    return copy(
        text = normalizedText,
        selectionStart = range.start,
        selectionEnd = range.end,
        checked = if (type == RichTextBlockType.Todo) checked else false,
        indentLevel = 0,
        spans = if (supportsSpans) QuataSpanAlgorithms.normalize(spans, normalizedText.length) else emptyList(),
    )
}

private fun TextRange.clampTo(text: String): TextRange {
    return TextRange(start.coerceIn(0, text.length), end.coerceIn(0, text.length))
}

private fun emptyRichTextBlockModel(): QuataRichTextBlockModel {
    return QuataRichTextBlockModel(
        id = java.util.UUID.randomUUID().toString(),
        type = RichTextBlockType.Paragraph,
        text = "",
        selectionStart = 0,
        selectionEnd = 0,
        checked = false,
        indentLevel = 0,
        spans = emptyList(),
    )
}
