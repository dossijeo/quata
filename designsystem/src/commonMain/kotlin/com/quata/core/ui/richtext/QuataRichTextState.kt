package com.quata.core.ui.richtext

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.math.max
import kotlin.math.min

public enum class RichTextBlockType {
    Paragraph,
    Heading1,
    Heading2,
    Heading3,
    Heading4,
    Heading5,
    Heading6,
    Bullet,
    Numbered,
    Quote,
    Info,
    Todo,
    Code,
    Divider,
}

public enum class InlineFormat {
    Bold,
    Italic,
    Underline,
    Strike,
    InlineCode,
    Highlight,
}

public class QuataRichTextBlock(
    val id: String = richTextId(),
    type: RichTextBlockType = RichTextBlockType.Paragraph,
    text: String = "",
    checked: Boolean = false,
    indent: Int = 0,
    spans: List<QuataTextSpan> = emptyList(),
) {
    var type by mutableStateOf(type)
    var text by mutableStateOf(TextFieldValue(text))
    var isChecked by mutableStateOf(checked)
    var indentLevel by mutableStateOf(0)
    var spans by mutableStateOf(QuataSpanAlgorithms.normalize(spans, text.length))
}

private val DEFAULT_BLOCK_TYPE = RichTextBlockType.Paragraph
private const val ORDERED_MARKER = "ordered"
private const val BULLET_MARKER = "bullet"
private const val TODO_MARKER = "todo"
private const val TEXT_HISTORY_MERGE_WINDOW_MS = 500L

public class QuataRichTextEditorState(initialHtml: String = "") {
    val blocks: SnapshotStateList<QuataRichTextBlock> = mutableStateListOf()
    val selectedBlockId = mutableStateOf<String?>(null)
    val selectedBlockIds: SnapshotStateList<String> = mutableStateListOf()
    private val selectionAnchorId = mutableStateOf<String?>(null)
    private val historyStack: SnapshotStateList<QuataRichTextHistoryEntry> = mutableStateListOf()
    private var historyCursor = -1
    private val maxHistoryDepth = 80
    private var isApplyingHistory = false
    var canUndo: Boolean by mutableStateOf(false)
        private set
    var canRedo: Boolean by mutableStateOf(false)
        private set
    val maxIndent = mutableIntStateOf(8)
    private val slashCommandSession = mutableStateOf<QuataTextSlashSession?>(null)
    val isShiftPressed = mutableStateOf(false)
    val isCtrlPressed = mutableStateOf(false)

    val isBold = mutableStateOf(false)
    val isItalic = mutableStateOf(false)
    val isUnderline = mutableStateOf(false)
    val isStrikethrough = mutableStateOf(false)
    val isInlineCode = mutableStateOf(false)
    val isHighlight = mutableStateOf(false)
    val isHeading = mutableStateOf(false)
    val selectedHeadingLevel = mutableIntStateOf(0)
    val isBulletedList = mutableStateOf(false)
    val isNumberedList = mutableStateOf(false)
    val isTodo = mutableStateOf(false)
    val isQuote = mutableStateOf(false)
    val isInfo = mutableStateOf(false)
    val isCode = mutableStateOf(false)
    val isDivider = mutableStateOf(false)
    val isLinked = mutableStateOf(false)
    val isSelectionCollapsed = mutableStateOf(true)

    private val _pendingInlineStyles: SnapshotStateMap<String, MutableSet<InlineFormat>> = mutableStateMapOf()
    private val _lastSelection: SnapshotStateMap<String, TextRange> = mutableStateMapOf()
    private val textStates = QuataBlockTextStates()
    private val spanStates = QuataBlockSpanStates()
    private val htmlState = mutableStateOf("")
    private var dragMachine: QuataTextDragMachine? = null
    private var lastTextHistoryBlockId: String? = null
    private var lastTextHistoryTimestampMs: Long = 0L

    val html: String get() = htmlState.value
    val activeSlashSession: QuataTextSlashSession? get() = slashCommandSession.value

    init {
        val parsed = parseHtmlToRichTextBlocks(initialHtml)
        if (parsed.isEmpty()) {
            blocks.add(QuataRichTextBlock(type = DEFAULT_BLOCK_TYPE))
        } else {
            parsed.forEach { blocks.add(it) }
        }
        syncRuntimeHolders()
        selectSingleBlock(blocks.first().id)
        refreshFormattingStateForCurrentSelection()
        rebuildHtml()
        captureHistoryState()
    }

    private fun selectSingleBlock(blockId: String?) {
        val safeId = if (blockId != null && blocks.any { it.id == blockId }) {
            blockId
        } else {
            blocks.firstOrNull()?.id
        }
        selectedBlockId.value = safeId
        selectedBlockIds.clear()
        safeId?.let(::selectSetAdd)
        safeId?.let { selectionAnchorId.value = it }
    }

    private fun selectSetAdd(blockId: String) {
        if (!selectedBlockIds.contains(blockId)) {
            selectedBlockIds.add(blockId)
        }
    }

    private fun selectSetRemove(blockId: String) {
        val index = selectedBlockIds.indexOf(blockId)
        if (index >= 0) {
            selectedBlockIds.removeAt(index)
        }
    }

    fun selectBlock(
        blockId: String,
        clearSelection: Boolean = true,
        useShift: Boolean = false,
        useCtrlOrCmd: Boolean = false,
    ) {
        if (activeSlashSession?.blockId != blockId) {
            clearSlashCommandSession()
        }

        val block = blockById(blockId) ?: return
        if (clearSelection) {
            selectedBlockIds.clear()
            selectSetAdd(block.id)
            selectedBlockId.value = block.id
            selectionAnchorId.value = block.id
            refreshFormattingStateForCurrentSelection()
            return
        }

        val anchor = selectionAnchorId.value ?: selectedBlockId.value
        if (useShift && anchor != null) {
            val anchorIndex = indexOfBlock(anchor)
            val targetIndex = indexOfBlock(block.id)
            if (anchorIndex >= 0 && targetIndex >= 0) {
                selectedBlockIds.clear()
                val start = min(anchorIndex, targetIndex)
                val end = max(anchorIndex, targetIndex)
                for (index in start..end) {
                    selectSetAdd(blocks[index].id)
                }
                selectedBlockId.value = block.id
                refreshFormattingStateForCurrentSelection()
                return
            }
        }

        if (useCtrlOrCmd) {
            if (selectedBlockIds.contains(block.id)) {
                selectSetRemove(block.id)
            } else {
                selectSetAdd(block.id)
            }
            if (selectedBlockIds.isEmpty()) {
                selectSetAdd(block.id)
            }
            selectedBlockId.value = block.id
            refreshFormattingStateForCurrentSelection()
            return
        }
        selectedBlockIds.clear()
        selectSetAdd(block.id)
        selectedBlockId.value = block.id
        selectionAnchorId.value = block.id
        refreshFormattingStateForCurrentSelection()
    }

    fun selectAdjacentBlock(offset: Int, useShift: Boolean = false, useCtrlOrCmd: Boolean = false) {
        val currentId = selectedBlockId.value ?: return
        val currentIndex = indexOfBlock(currentId)
        if (currentIndex < 0) return
        val targetIndex = currentIndex + offset
        if (targetIndex !in blocks.indices) return
        val target = blocks[targetIndex]
        if (target.id == currentId) return
        selectBlock(
            blockId = target.id,
            clearSelection = !(useShift || useCtrlOrCmd),
            useShift = useShift,
            useCtrlOrCmd = useCtrlOrCmd,
        )
    }

    fun movePrimaryBlockUp() {
        val currentId = selectedBlockId.value ?: return
        moveBlockUp(currentId)
    }

    fun movePrimaryBlockDown() {
        val currentId = selectedBlockId.value ?: return
        moveBlockDown(currentId)
    }

    fun clearSelection() {
        selectedBlockIds.clear()
        selectionAnchorId.value = selectedBlockId.value
        selectedBlockId.value = blocks.firstOrNull()?.id
        selectedBlockId.value?.let { selectSetAdd(it) }
        refreshFormattingStateForCurrentSelection()
    }

    fun setModifierKeys(shiftPressed: Boolean, ctrlPressed: Boolean) {
        isShiftPressed.value = shiftPressed
        isCtrlPressed.value = ctrlPressed
    }

    fun selectAllBlocks() {
        selectedBlockIds.clear()
        blocks.forEach { block -> selectSetAdd(block.id) }
        if (blocks.isNotEmpty()) {
            selectedBlockId.value = blocks.first().id
            selectionAnchorId.value = blocks.first().id
        }
        refreshFormattingStateForCurrentSelection()
        captureHistoryState()
    }

    fun addBlock(afterBlockId: String? = null) {
        val insertAt = afterBlockId?.let(::indexOfBlock)?.plus(1) ?: blocks.size
        dispatchRichTextAction(
            InsertRichTextBlock(
                block = newBlock().toModel(),
                atIndex = insertAt,
            ),
        )
    }

    public fun isBlockSelected(blockId: String): Boolean = selectedBlockIds.contains(blockId)

    public fun captureDragSnapshot(): QuataRichTextDragSnapshot {
        val blockSnapshots = blocks.map { block ->
            QuataRichTextBlockSnapshot(
                id = block.id,
                type = block.type,
                text = block.text.text,
                selectionStart = block.text.selection.start,
                selectionEnd = block.text.selection.end,
                isChecked = block.isChecked,
                indentLevel = block.indentLevel,
                spans = block.spans,
            )
        }
        val lastSelection = _lastSelection.toMap().mapValues { entry ->
            TextRange(entry.value.start, entry.value.end)
        }
        val pendingStyles = _pendingInlineStyles.mapValues { entry ->
            entry.value.toSet()
        }

        return QuataRichTextDragSnapshot(
            blocks = blockSnapshots,
            selectedBlockId = selectedBlockId.value,
            selectedBlockIds = selectedBlockIds.toList(),
            selectionByBlockId = lastSelection,
            pendingInlineStyles = pendingStyles,
        )
    }

    public fun restoreFromDragSnapshot(snapshot: QuataRichTextDragSnapshot, pushHistory: Boolean = false) {
        applyDragSnapshotInternal(snapshot)
        if (pushHistory) {
            captureHistoryState()
        } else {
            rebuildHtml()
        }
    }

    fun undo(): Boolean {
        if (!canUndo) return false
        isApplyingHistory = true
        return try {
            applyHistoryState(historyStack.getOrNull(historyCursor - 1))
            historyCursor--
            updateHistoryAvailability()
            true
        } finally {
            isApplyingHistory = false
        }
    }

    fun redo(): Boolean {
        if (!canRedo) return false
        isApplyingHistory = true
        return try {
            applyHistoryState(historyStack.getOrNull(historyCursor + 1))
            historyCursor++
            updateHistoryAvailability()
            true
        } finally {
            isApplyingHistory = false
        }
    }

    fun duplicateBlock(blockId: String) {
        duplicateSelectedBlocks(blockId)
    }

    fun duplicateSelectedBlocks(anchorBlockId: String? = null) {
        val targets = activeRootTargets(anchorBlockId)
        if (targets.isEmpty()) return
        val payloadIndices = resolveSubtreePayload(targets)
        if (payloadIndices.isEmpty()) return

        val payload = payloadIndices.map { blocks[it] }
        val duplicatePairs = payload.map { source ->
            val duplicate = QuataRichTextBlock(
                type = source.type,
                text = source.text.text,
                checked = source.isChecked,
                indent = source.indentLevel,
                spans = source.spans,
            )
            source.id to duplicate
        }
        val duplicateModels = duplicatePairs.map { (_, duplicate) -> duplicate.toModel() }
        if (duplicateModels.isEmpty()) return

        val inserted = dispatchRichTextAction(
            InsertRichTextBlocks(
                blocks = duplicateModels,
                atIndex = payloadIndices.last() + 1,
                selectedBlockId = duplicateModels.firstOrNull()?.id,
            ),
            recordHistory = false,
        )
        if (!inserted) return

        selectedBlockIds.clear()
        duplicateModels.forEach { model -> selectSetAdd(model.id) }
        selectedBlockId.value = duplicateModels.firstOrNull()?.id ?: anchorBlockId
        selectionAnchorId.value = selectedBlockId.value
        duplicatePairs.forEach { (sourceId, duplicate) ->
            copyPendingStyles(sourceId, duplicate.id)
        }
        captureHistoryState()
    }

    fun removeSelectedBlocks() {
        val targets = activeRootTargets().let { ids ->
            if (ids.isEmpty()) {
                selectedBlockId.value?.let { listOf(it) } ?: emptyList()
            } else {
                ids
            }
        }
        if (targets.isEmpty()) return

        val payloadIndices = resolveSubtreePayload(targets)
        if (payloadIndices.isEmpty()) return

        val toRemove = payloadIndices.map { blocks[it].id }.toSet()
        for (id in toRemove) {
            if (activeSlashSession?.blockId == id) {
                clearSlashCommandSession()
            }
        }
        dispatchRichTextAction(DeleteRichTextBlocks(toRemove))
    }

    fun removeBlock(blockId: String) {
        val targets = activeRootTargets(blockId)
        if (targets.isEmpty()) return
        selectedBlockIds.clear()
        targets.forEach(::selectSetAdd)
        removeSelectedBlocks()
    }

    private fun resolveSubtreePayload(rootIds: List<String>): List<Int> {
        if (rootIds.isEmpty()) return emptyList()
        val rootIndices = rootIds.mapNotNull { blockId ->
            val index = indexOfBlock(blockId)
            if (index >= 0) index else null
        }.sorted()

        val selectedRootIndices = rootIndices.filterNot { rootIndex ->
            rootIndices.any { candidateRootIndex ->
                candidateRootIndex < rootIndex &&
                    rootIndex < subtreeEndExclusive(candidateRootIndex)
            }
        }

        val payload = linkedSetOf<Int>()
        for (rootIndex in selectedRootIndices) {
            for (index in rootIndex until subtreeEndExclusive(rootIndex)) {
                payload.add(index)
            }
        }
        return payload.toList()
    }

    private fun activeRootTargets(fallbackBlockId: String? = null): List<String> {
        val anchors = selectedBlockIds.toList()
        val target = if (anchors.isNotEmpty()) {
            anchors
        } else {
            listOfNotNull(fallbackBlockId ?: selectedBlockId.value)
        }
        if (target.isEmpty()) return emptyList()

        val rootIndices = target.mapNotNull { id -> indexOfBlock(id).takeIf { it >= 0 } }
        val roots = resolveSubtreeRootIndices(rootIndices)
        return roots.map(blocks::get).map { it.id }
    }

    private fun activeRootTargets(blockIds: Collection<String>): List<String> {
        val target = if (blockIds.isNotEmpty()) {
            blockIds.toList()
        } else {
            listOfNotNull(selectedBlockId.value)
        }
        if (target.isEmpty()) return emptyList()

        val rootIndices = target
            .mapNotNull { id -> indexOfBlock(id).takeIf { it >= 0 } }
            .sorted()
        val roots = resolveSubtreeRootIndices(rootIndices)
        return roots.map(blocks::get).map { it.id }
    }

    private fun resolveSubtreeRootIndices(selectedIndices: List<Int>): List<Int> {
        if (selectedIndices.isEmpty()) return emptyList()
        val sorted = selectedIndices.sorted()
        return sorted.filterNot { selectedIndex ->
            sorted.any { candidateRootIndex ->
                candidateRootIndex < selectedIndex && selectedIndex < subtreeEndExclusive(candidateRootIndex)
            }
        }
    }

    private fun subtreeEndExclusive(rootIndex: Int): Int {
        if (rootIndex !in blocks.indices) return rootIndex
        val rootIndent = blocks[rootIndex].indentLevel
        var next = rootIndex + 1
        while (next < blocks.size) {
            val candidate = blocks[next]
            if (!supportsIndent(candidate.type)) break
            if (candidate.indentLevel <= rootIndent) {
                break
            }
            next++
        }
        return next
    }

    fun moveBlockUp(blockId: String) {
        if (selectedBlockIds.isNotEmpty()) {
            moveRootsUp(selectedBlockIds.toSet())
            return
        }
        moveRootsUp(listOf(blockId))
    }

    fun moveBlockDown(blockId: String) {
        if (selectedBlockIds.isNotEmpty()) {
            moveRootsDown(selectedBlockIds.toSet())
            return
        }
        moveRootsDown(listOf(blockId))
    }

    fun moveSelectedRootsTo(targetIndex: Int): Boolean {
        return moveSelectedRootsTo(
            targetIndex = targetIndex,
            futureRootIndent = null,
            recordHistory = true,
        )
    }

    fun moveSelectedRootsTo(
        targetIndex: Int,
        futureRootIndent: Int?,
    ): Boolean {
        return moveSelectedRootsTo(
            targetIndex = targetIndex,
            futureRootIndent = futureRootIndent,
            recordHistory = true,
        )
    }

    fun moveSelectedRootsTo(
        targetIndex: Int,
        futureRootIndent: Int?,
        recordHistory: Boolean = true,
    ): Boolean {
        val payload = getDragPayloadSnapshot() ?: return false
        if (payload.primarySupportsIndent && futureRootIndent != null && futureRootIndent < 0) return false
        if (payload.primarySupportsIndent && futureRootIndent == null) {
            return moveSelectedRootsToWithoutDepth(targetIndex, recordHistory)
        }

        if (targetIndex !in 0..blocks.size) return false
        val payloadIndices = payload.payloadIndices
        if (payload.payloadRanges.isEmpty() || payloadIndices.isEmpty()) return false
        if (targetIndex.isInsidePayloadRanges(payload.payloadRanges)) return false

        val depthDelta = if (payload.primarySupportsIndent && futureRootIndent != null) {
            futureRootIndent - payload.primaryRootIndent
        } else {
            0
        }
        if (depthDelta != 0 && !payload.primarySupportsIndent) return false

        val firstIndex = payloadIndices.first()
        val lastIndex = payloadIndices.last()
        if (depthDelta == 0 && (targetIndex == firstIndex || targetIndex == lastIndex + 1)) {
            return false
        }

        val movedIds = payload.payloadBlockIds.toSet()
        val movedBlocks = payloadIndices.map { index ->
            val sourceBlock = blocks[index]
            if (depthDelta != 0 && !supportsIndent(sourceBlock.type)) return false

            val nextDepth = sourceBlock.indentLevel + depthDelta
            if (nextDepth !in 0..maxIndent.intValue) return false
            if (!supportsIndent(sourceBlock.type) && nextDepth != 0) return false
            if (nextDepth != sourceBlock.indentLevel) {
                val moved = QuataRichTextBlock(
                    id = sourceBlock.id,
                    type = sourceBlock.type,
                    text = sourceBlock.text.text,
                    checked = sourceBlock.isChecked,
                    indent = nextDepth,
                    spans = sourceBlock.spans,
                )
                moved.text = sourceBlock.text
                moved
            } else {
                sourceBlock
            }
        }

        val insertionIndex = targetIndex - payloadIndices.count { it < targetIndex }
        val remainingBlocks = blocks.filterNot { it.id in movedIds }.toMutableList()
        if (insertionIndex < 0 || insertionIndex > remainingBlocks.size) return false
        if (insertionIndex == 0 && remainingBlocks.isEmpty() && movedBlocks.isEmpty()) return false

        val orderedBlocks = remainingBlocks.toMutableList().apply {
            addAll(insertionIndex, movedBlocks)
        }
        if (!orderedBlocks.isValidIndentationOutline(maxIndent.intValue)) {
            return false
        }

        return dispatchRichTextAction(
            ReorderRichTextBlocks(
                blocks = orderedBlocks.map { it.toModel() },
                selectedBlockId = movedBlocks.firstOrNull()?.id,
                selectedBlockIds = movedBlocks.firstOrNull()?.id?.let(::listOf).orEmpty(),
            ),
            recordHistory = recordHistory,
        )
    }

    public fun getDragPayloadSnapshot(anchorBlockId: String? = null): QuataRichTextDragPayload? {
        val selectedRoots = activeRootTargets().let { roots ->
            if (roots.isNotEmpty()) {
                roots
            } else if (anchorBlockId != null) {
                activeRootTargets(anchorBlockId)
            } else {
                listOfNotNull(selectedBlockId.value)
            }
        }
        if (selectedRoots.isEmpty()) return null

        val rootIndices = selectedRoots.mapNotNull { blockId -> indexOfBlock(blockId) }
            .filter { it >= 0 }
            .sorted()
        if (rootIndices.isEmpty()) return null

        val anchorIndex = anchorBlockId?.let(::indexOfBlock) ?: selectedBlockIds.firstOrNull()
            ?.let(::indexOfBlock) ?: -1
        val primaryRootIndex = rootIndices.firstOrNull { rootIndex ->
            anchorIndex >= rootIndex && anchorIndex < subtreeEndExclusive(rootIndex)
        } ?: rootIndices.first()
        val primaryRoot = blocks.getOrNull(primaryRootIndex) ?: return null
        val payloadIndices = resolveSubtreePayload(selectedRoots).ifEmpty { return null }
        val payloadRanges = payloadIndices.toIntRanges()
        val payloadIds = payloadIndices.map { blocks[it].id }
        val dragRootIds = rootIndices.map { blocks[it].id }
        val rootRanges = rootIndices.associateWith { rootIndex ->
            rootIndex until subtreeEndExclusive(rootIndex)
        }
        val originalRootIndentLevels = rootIndices.associate { rootIndex ->
            blocks[rootIndex].id to blocks[rootIndex].indentLevel
        }
        val payloadRelativeDepthOffsets = linkedMapOf<String, Int>()
        val payloadRootIdsByBlockId = linkedMapOf<String, String>()
        for (rootIndex in rootIndices) {
            val root = blocks[rootIndex]
            val rootIndent = root.indentLevel
            val range = rootRanges.getValue(rootIndex)
            for (index in range) {
                val block = blocks[index]
                payloadRelativeDepthOffsets[block.id] = block.indentLevel - rootIndent
                payloadRootIdsByBlockId[block.id] = root.id
            }
        }

        var shallowestOffset = Int.MAX_VALUE
        var deepestOffset = Int.MIN_VALUE
        for (index in payloadIndices) {
            val block = blocks[index]
            val payloadOffset = block.indentLevel - primaryRoot.indentLevel
            shallowestOffset = min(shallowestOffset, payloadOffset)
            deepestOffset = max(deepestOffset, payloadOffset)
        }
        val payloadOffsetRange = if (shallowestOffset == Int.MAX_VALUE || deepestOffset == Int.MIN_VALUE) {
            null
        } else {
            IntRange(shallowestOffset, deepestOffset)
        } ?: return null

        val primarySupportsIndent = supportsIndent(primaryRoot.type)
        return QuataRichTextDragPayload(
            primaryRootId = primaryRoot.id,
            primaryRootIndent = primaryRoot.indentLevel,
            primarySupportsIndent = primarySupportsIndent,
            dragRootIds = dragRootIds,
            payloadBlockIds = payloadIds,
            payloadIndices = payloadIndices,
            payloadRanges = payloadRanges,
            originalRootIndentLevels = originalRootIndentLevels,
            payloadRelativeDepthOffsets = payloadRelativeDepthOffsets,
            payloadRootIdsByBlockId = payloadRootIdsByBlockId,
            payloadDepthOffsetRange = payloadOffsetRange,
        )
    }

    fun resolveDragFutureRootIndentation(
        targetIndex: Int,
        horizontalDragDeltaPx: Float,
        indentUnitPx: Float,
        anchorBlockId: String? = null,
    ): Int? {
        if (indentUnitPx <= 0f) return null
        val payload = getDragPayloadSnapshot(anchorBlockId) ?: return null
        if (targetIndex !in 0..blocks.size) return null
        if (targetIndex.isInsidePayloadRanges(payload.payloadRanges)) return null

        val payloadIndexSet = payload.payloadIndices.toSet()
        var nextIndex = targetIndex
        while (nextIndex < blocks.size && nextIndex in payloadIndexSet) {
            nextIndex++
        }
        val nextBlock = blocks.getOrNull(nextIndex)

        val requestedDepth = if (payload.primarySupportsIndent) {
            payload.primaryRootIndent + (horizontalDragDeltaPx / indentUnitPx).toInt()
        } else {
            0
        }

        var minDepth = max(0, -payload.payloadDepthOffsetRange.first)
        var maxDepth = min(maxIndent.intValue, maxIndent.intValue - payload.payloadDepthOffsetRange.last)
        if (!payload.primarySupportsIndent) {
            minDepth = 0
            maxDepth = 0
        } else if (nextBlock != null) {
            minDepth = max(minDepth, nextBlock.indentLevel - payload.payloadDepthOffsetRange.first)
        }

        if (minDepth > maxDepth) return null
        return requestedDepth.coerceIn(minDepth, maxDepth)
    }

    public fun startDragSession(anchorBlockId: String): Boolean {
        if (dragMachine != null) return false

        val payload = getDragPayloadSnapshot(anchorBlockId) ?: return false
        val anchorIndex = indexOfBlock(anchorBlockId)
        if (anchorIndex < 0) return false

        dragMachine = QuataTextDragMachine(
            anchorBlockId = anchorBlockId,
            anchorInitialIndex = anchorIndex,
            primaryRootId = payload.primaryRootId,
            primaryRootOriginalIndex = indexOfBlock(payload.primaryRootId),
            primaryRootOriginalIndent = payload.primaryRootIndent,
            primaryRootSupportsIndent = payload.primarySupportsIndent,
            dragSnapshot = captureDragSnapshot(),
            dragRootIds = payload.dragRootIds,
            payloadBlockIds = payload.payloadBlockIds,
            payloadBlockIdSet = payload.payloadBlockIds.toSet(),
            payloadIndices = payload.payloadIndices,
            payloadIndexSet = payload.payloadIndices.toSet(),
            payloadRanges = payload.payloadRanges,
            originalRootIndentLevels = payload.originalRootIndentLevels,
            payloadRelativeDepthOffsets = payload.payloadRelativeDepthOffsets,
            payloadRootIdsByBlockId = payload.payloadRootIdsByBlockId,
            payloadDepthOffsetRange = payload.payloadDepthOffsetRange,
            futureRootIndent = payload.primaryRootIndent,
        )
        return true
    }

    public fun updateDragSession(
        targetIndex: Int?,
        futureRootIndent: Int?,
        horizontalDragDeltaPx: Float = 0f,
        indentUnitPx: Float = 0f,
        anchorBlockId: String? = null,
    ): Boolean {
        val session = dragMachine ?: return false
        val hoverTarget = resolveDragHoverTarget(
            session = session,
            targetIndex = targetIndex,
            horizontalDragDeltaPx = horizontalDragDeltaPx,
            indentUnitPx = indentUnitPx,
            explicitFutureRootIndent = futureRootIndent,
        )

        if (hoverTarget == null) {
            session.targetIndex = null
            session.futureRootIndent = null
            return false
        }

        session.targetIndex = hoverTarget.targetIndex
        session.futureRootIndent = hoverTarget.futureRootIndent
        return true
    }

    public fun completeDragSession(): Boolean {
        val session = dragMachine ?: return false
        val snapshot = session.dragSnapshot
        val targetIndex = session.targetIndex

        restoreFromDragSnapshot(snapshot, pushHistory = false)
        dragMachine = null

        if (targetIndex == null) return false
        val moved = moveDragPayload(
            session = session,
            visualGap = targetIndex,
            futureRootIndent = session.futureRootIndent ?: session.primaryRootOriginalIndent,
            recordHistory = true,
        )
        if (!moved) {
            restoreFromDragSnapshot(snapshot, pushHistory = false)
        }
        return moved
    }

    public fun cancelDragSession() {
        val session = dragMachine ?: return
        restoreFromDragSnapshot(session.dragSnapshot, pushHistory = false)
        dragMachine = null
    }

    public fun isDragSessionActive(): Boolean = dragMachine != null

    public fun currentDragMachineTargetIndex(): Int? = dragMachine?.targetIndex

    public fun currentDragMachineFutureRootIndent(): Int? = dragMachine?.futureRootIndent

    public fun isBlockInDragPayload(blockId: String): Boolean {
        return dragMachine?.payloadBlockIdSet?.contains(blockId) == true
    }

    private fun resolveDragHoverTarget(
        session: QuataTextDragMachine,
        targetIndex: Int?,
        horizontalDragDeltaPx: Float,
        indentUnitPx: Float,
        explicitFutureRootIndent: Int?,
    ): QuataTextDragHoverTarget? {
        val gap = targetIndex?.takeIf { it in 0..blocks.size } ?: return null
        if (gap.isInsidePayloadRanges(session.payloadRanges)) return null

        val nextBlock = nextNonPayloadBlock(gap, session.payloadIndexSet)
        val requestedDepth = when {
            !session.primaryRootSupportsIndent -> 0
            explicitFutureRootIndent != null -> explicitFutureRootIndent
            indentUnitPx > 0f -> session.primaryRootOriginalIndent + (horizontalDragDeltaPx / indentUnitPx).toInt()
            else -> session.futureRootIndent ?: session.primaryRootOriginalIndent
        }

        val shallowestOffset = session.payloadDepthOffsetRange.first
        val deepestOffset = session.payloadDepthOffsetRange.last
        var minDepth = max(0, -shallowestOffset)
        var maxDepth = min(maxIndent.intValue, maxIndent.intValue - deepestOffset)

        if (!session.primaryRootSupportsIndent) {
            minDepth = 0
            maxDepth = 0
        } else if (nextBlock != null) {
            minDepth = max(minDepth, nextBlock.indentLevel - shallowestOffset)
        }

        if (minDepth > maxDepth) return null
        return QuataTextDragHoverTarget(
            targetIndex = gap,
            futureRootIndent = requestedDepth.coerceIn(minDepth, maxDepth),
        )
    }

    private fun nextNonPayloadBlock(
        visualGap: Int,
        payloadIndexSet: Set<Int>,
    ): QuataRichTextBlock? {
        var index = visualGap
        while (index < blocks.size && index in payloadIndexSet) {
            index++
        }
        return blocks.getOrNull(index)
    }

    private fun moveDragPayload(
        session: QuataTextDragMachine,
        visualGap: Int,
        futureRootIndent: Int,
        recordHistory: Boolean,
    ): Boolean {
        if (blocks.isEmpty() || session.payloadBlockIds.isEmpty()) return false
        val gap = visualGap.coerceIn(0, blocks.size)
        if (gap.isInsidePayloadRanges(session.payloadRanges)) return false

        val payloadIdSet = session.payloadBlockIdSet
        val payloadIndices = blocks
            .mapIndexedNotNull { index, block -> index.takeIf { block.id in payloadIdSet } }
            .sorted()
        if (payloadIndices.size != payloadIdSet.size) return false

        val depthDelta = futureRootIndent - session.primaryRootOriginalIndent
        if (depthDelta == 0 && payloadIndices.isContiguousRange()) {
            val firstIndex = payloadIndices.first()
            val afterIndex = payloadIndices.last() + 1
            if (gap == firstIndex || gap == afterIndex) return false
        }

        val movedBlocks = ArrayList<QuataRichTextBlock>(payloadIndices.size)
        val remainingBlocks = ArrayList<QuataRichTextBlock>(blocks.size - payloadIndices.size)
        var insertionIndex = 0

        for (index in blocks.indices) {
            val block = blocks[index]
            if (block.id in payloadIdSet) {
                val nextIndent = block.indentLevel + depthDelta
                if (nextIndent !in 0..maxIndent.intValue) return false
                if (!supportsIndent(block.type) && nextIndent != 0) return false
                movedBlocks += if (nextIndent == block.indentLevel) {
                    block
                } else {
                    block.copyForMove(nextIndent)
                }
            } else {
                if (index < gap) insertionIndex++
                remainingBlocks += block
            }
        }

        val orderedBlocks = remainingBlocks.toMutableList().apply {
            addAll(insertionIndex.coerceIn(0, remainingBlocks.size), movedBlocks)
        }
        if (orderedBlocks == blocks) return false
        if (!orderedBlocks.isValidIndentationOutline(maxIndent.intValue)) return false

        val selectedId = session.primaryRootId.takeIf { id -> orderedBlocks.any { it.id == id } }
            ?: movedBlocks.firstOrNull()?.id
        return dispatchRichTextAction(
            ReorderRichTextBlocks(
                blocks = orderedBlocks.map { it.toModel() },
                selectedBlockId = selectedId,
                selectedBlockIds = selectedId?.let(::listOf).orEmpty(),
            ),
            recordHistory = recordHistory,
        )
    }

    private fun moveSelectedRootsToWithoutDepth(
        targetIndex: Int,
        recordHistory: Boolean,
    ): Boolean {
        val selectedRoots = activeRootTargets().let { roots ->
            if (roots.isNotEmpty()) roots else listOfNotNull(selectedBlockId.value)
        }
        if (selectedRoots.isEmpty()) return false
        return moveRootsTo(selectedRoots.toSet(), targetIndex, recordHistory)
    }

    fun moveBlockTo(blockId: String, targetIndex: Int) {
        val current = indexOfBlock(blockId)
        if (current == -1 || targetIndex < 0 || targetIndex >= blocks.size) return
        if (current == targetIndex) return

        val reordered = blocks.toMutableList()
        val moved = reordered.removeAt(current)
        val safeIndex = if (targetIndex > current) targetIndex - 1 else targetIndex
        reordered.add(safeIndex, moved)
        dispatchRichTextAction(
            ReorderRichTextBlocks(
                blocks = reordered.map { it.toModel() },
                selectedBlockId = moved.id,
                selectedBlockIds = listOf(moved.id),
            ),
        )
    }

    fun moveRootsUp(blockIds: Collection<String>) {
        moveRootsByOffset(blockIds, -1)
    }

    fun moveRootsDown(blockIds: Collection<String>) {
        moveRootsByOffset(blockIds, +1)
    }

    private fun moveRootsTo(
        blockIds: Collection<String>,
        targetIndex: Int,
        recordHistory: Boolean = true,
    ): Boolean {
        if (blocks.isEmpty() || blockIds.isEmpty()) return false
        if (targetIndex < 0 || targetIndex > blocks.size) return false

        var loops = 0
        while (loops < blocks.size) {
            val roots = activeRootTargets(blockIds)
            if (roots.isEmpty()) return false
            val payload = resolveSubtreePayload(roots)
            if (payload.isEmpty()) return false
            val start = payload.first()
            val end = payload.last()
            if (targetIndex == blocks.size && end == blocks.lastIndex) return true
            if (targetIndex in start..end) return true

            val moved = if (targetIndex < start) {
                moveRootsByOffset(blockIds, -1, recordHistory)
            } else {
                moveRootsByOffset(blockIds, +1, recordHistory)
            }
            if (!moved) return false
            loops++
        }
        return false
    }

    private fun moveRootsByOffset(
        blockIds: Collection<String>,
        delta: Int,
        recordHistory: Boolean = true,
    ): Boolean {
        val targets = activeRootTargets(blockIds)
        if (targets.isEmpty()) return false

        val payloadIndices = resolveSubtreePayload(targets)
        if (payloadIndices.isEmpty()) return false

        val movedIds = payloadIndices.map { blocks[it].id }.toSet()
        val movedBlocks = payloadIndices.map { blocks[it] }
        if (movedIds.isEmpty()) return false

        val minIndex = payloadIndices.first()
        val maxIndex = payloadIndices.last()
        val remaining = blocks.filterNot { it.id in movedIds }
        if (delta == 0) return false
        if (delta < 0 && minIndex == 0) return false
        if (delta > 0 && maxIndex == blocks.lastIndex) return false

        val insertionInRemaining = if (delta < 0) {
            val previousRootIndex = rootStartForIndex(minIndex - 1)
            blocks.take(previousRootIndex).count { it.id !in movedIds }
        } else {
            val nextRootEnd = subtreeEndExclusive(maxIndex + 1)
            blocks.take(nextRootEnd).count { it.id !in movedIds }
        }

        val orderedBlocks = remaining.toMutableList().apply {
            addAll(insertionInRemaining, movedBlocks)
        }
        return dispatchRichTextAction(
            ReorderRichTextBlocks(
                blocks = orderedBlocks.map { it.toModel() },
                selectedBlockId = movedBlocks.firstOrNull()?.id,
                selectedBlockIds = movedBlocks.firstOrNull()?.id?.let(::listOf).orEmpty(),
            ),
            recordHistory = recordHistory,
        )
    }

    private fun rootStartForIndex(index: Int): Int {
        if (index !in blocks.indices) return index.coerceIn(0, blocks.size)
        var root = index
        while (root > 0 && supportsIndent(blocks[root].type)) {
            val previous = blocks[root - 1]
            if (!supportsIndent(previous.type) || previous.indentLevel >= blocks[root].indentLevel) break
            root--
        }
        return root
    }


    fun updateBlockText(blockId: String, value: TextFieldValue) {
        val block = blockById(blockId) ?: return
        updateBlockText(blockId, block.text, value)
    }

    fun updateBlockText(blockId: String, previous: TextFieldValue, next: TextFieldValue) {
        val block = blockById(blockId) ?: return
        val sanitized = normalizePlainText(next.text)
        val safeSelection = clampSelection(next.selection, sanitized)
        val value = next.copy(text = sanitized, selection = safeSelection)

        val textChanged = value.text != previous.text
        val selectionChanged = value.selection != previous.selection
        if (!textChanged && !selectionChanged) return

        updateSlashCommandSession(
            blockId = blockId,
            blockText = previous.text,
            nextText = value.text,
            nextSelection = value.selection,
            isTextChanged = textChanged,
            isSelectionChanged = selectionChanged,
        )

        if (textChanged) {
            val edit = computeTextEdit(previous.text, value.text)
            if (block.type != RichTextBlockType.Code && value.text.contains('\n')) {
                splitTextAtLineBreak(block.id, value)
                return
            }

            val cursorAtStart = previous.selection.start == 0 && previous.selection.end == 0
            val becomesEmpty = previous.text.isNotBlank() && value.text.isEmpty() && value.selection.start == 0
            if (becomesEmpty && cursorAtStart) {
                val handledByStructure = handleListOrTodoBackspaceAtStart(block.id, value.text)
                if (handledByStructure) return
                val merged = mergeWithPrevious(blockId)
                if (merged) return
            }

            block.text = value
            if (edit != null) {
            block.spans = adjustSpansForUserEdit(block.id, block.spans, edit, value.text.length)
            } else {
                block.spans = QuataSpanAlgorithms.normalize(block.spans, value.text.length)
            }
            textStates.set(blockId, block.text)
            spanStates.set(blockId, block.spans, value.text.length)
            _lastSelection[blockId] = block.text.selection
            if (selectedBlockId.value != blockId) {
                selectedBlockId.value = blockId
            }
            if (block.type == RichTextBlockType.Paragraph) {
                maybeConvertTypedListPrefix(blockId)
            }
            refreshFormattingStateForCurrentSelection()
            rebuildHtml()
            captureTextHistoryState(blockId)
            return
        }

        if (blockId == selectedBlockId.value) {
            block.text = block.text.copy(selection = value.selection)
            textStates.set(blockId, block.text)
            _lastSelection[blockId] = block.text.selection
            refreshFormattingStateForCurrentSelection()
            return
        }
    }

    private fun splitTextAtLineBreak(blockId: String, value: TextFieldValue) {
        val block = blockById(blockId) ?: return
        val lineIndex = value.text.indexOf('\n')
        if (lineIndex < 0) return
        if (activeSlashSession?.blockId == block.id) {
            clearSlashCommandSession()
        }
        if (lineIndex == 0 && block.text.text.isNotEmpty()) {
            insertEmptyBlockAfter(blockId)
            return
        }

        val prefix = value.text.substring(0, lineIndex)
        val suffix = value.text.substring(lineIndex + 1)
        val index = indexOfBlock(blockId)
        if (index < 0) return
        val (prefixSpans, suffixSpans) = QuataSpanAlgorithms.splitAt(block.spans, lineIndex)

        block.text = TextFieldValue(prefix, selection = TextRange(prefix.length))
        block.spans = QuataSpanAlgorithms.normalize(prefixSpans, prefix.length)
        textStates.set(blockId, block.text, programmatic = true)
        spanStates.set(blockId, block.spans, prefix.length)
        _lastSelection[blockId] = block.text.selection

        val nextType = if (block.type == RichTextBlockType.Divider) RichTextBlockType.Paragraph else block.type
        val splitBlock = QuataRichTextBlock(
            type = nextType,
            text = suffix,
            checked = if (nextType == RichTextBlockType.Todo) block.isChecked else false,
            indent = block.indentLevel,
            spans = suffixSpans,
        )
        blocks.add(index + 1, splitBlock)
        textStates.set(splitBlock.id, splitBlock.text, programmatic = true)
        spanStates.set(splitBlock.id, splitBlock.spans, suffix.length)
        copyPendingStyles(blockId, splitBlock.id)
        selectedBlockId.value = splitBlock.id
        selectedBlockIds.clear()
        selectedBlockIds.add(splitBlock.id)
        selectionAnchorId.value = splitBlock.id
        _lastSelection[splitBlock.id] = TextRange(suffix.length)
        refreshFormattingStateForCurrentSelection()
        rebuildHtml()
        captureHistoryState()
    }

    private fun updateSlashCommandSession(
        blockId: String,
        blockText: String,
        nextText: String,
        nextSelection: TextRange,
        isTextChanged: Boolean,
        isSelectionChanged: Boolean,
    ) {
        val session = slashCommandSession.value
        if (isTextChanged) {
            val edit = computeTextEdit(blockText, nextText)
            if (edit == null) return

            if (session == null || session.blockId != blockId) {
                maybeOpenSlashSession(blockId, edit, nextText)
                return
            }

            if (session.blockId == blockId) {
                updateSlashSessionFromEdit(session, edit, nextText, nextSelection)
            }
            return
        }

        if (!isSelectionChanged || session == null || session.blockId != blockId) return
        handleSlashSelectionChange(session, nextSelection)
    }

    private fun maybeOpenSlashSession(blockId: String, edit: TextEdit, text: String) {
        if (edit.deletedLength != 0 || edit.insertedLength != 1) return
        if (edit.start !in text.indices) return
        if (text[edit.start] != '/') return
        slashCommandSession.value = QuataTextSlashSession(
            blockId = blockId,
            slashStart = edit.start,
            queryRangeEnd = edit.start + 1,
            query = "",
        )
    }

    private fun updateSlashSessionFromEdit(
        session: QuataTextSlashSession,
        edit: TextEdit,
        text: String,
        selection: TextRange,
    ) {
        if (text.isEmpty()) {
            closeSlashCommandSession()
            return
        }

        var slashStart = session.slashStart
        var queryRangeEnd = session.queryRangeEnd
        val delta = edit.insertedLength - edit.deletedLength
        val editEnd = edit.start + edit.deletedLength
        var editBeforeRange = false

        when {
            editEnd <= slashStart -> {
                slashStart += delta
                queryRangeEnd += delta
                editBeforeRange = true
            }
            edit.start in slashStart..queryRangeEnd -> {
                queryRangeEnd = (queryRangeEnd + delta).coerceAtLeast(slashStart + 1)
            }
            edit.start < slashStart -> {
                slashStart = (slashStart + delta).coerceAtLeast(0)
                queryRangeEnd = (queryRangeEnd + delta).coerceAtLeast(slashStart + 1)
            }
            else -> {}
        }

        if (slashStart !in text.indices) {
            closeSlashCommandSession()
            return
        }
        if (slashStart >= text.length || text[slashStart] != '/') {
            closeSlashCommandSession()
            return
        }

        queryRangeEnd = queryRangeEnd.coerceIn(slashStart + 1, text.length + 1)
        val queryEnd = queryRangeEnd.coerceAtMost(text.length)
        val start = min(selection.start, selection.end).coerceIn(0, text.length)
        val end = max(selection.start, selection.end).coerceIn(0, text.length)
        if (!editBeforeRange && (start < slashStart || end > queryEnd)) {
            closeSlashCommandSession()
            return
        }

        val query = if (slashStart + 1 < queryEnd) text.substring(slashStart + 1, queryEnd) else ""
        slashCommandSession.value = session.copy(
            slashStart = slashStart,
            queryRangeEnd = queryRangeEnd,
            query = query,
        )
    }

    private fun handleSlashSelectionChange(session: QuataTextSlashSession, selection: TextRange) {
        val blockTextLength = blockById(session.blockId)?.text?.text?.length ?: 0
        val queryEnd = session.queryRangeEnd.coerceIn(0, blockTextLength)
        val start = min(selection.start, selection.end).coerceIn(0, blockTextLength)
        val end = max(selection.start, selection.end).coerceIn(0, blockTextLength)
        if (start < session.slashStart || end > queryEnd) {
            closeSlashCommandSession()
        }
    }

    fun handleListOrTodoEnter(blockId: String): Boolean {
        val block = blockById(blockId) ?: return false
        if (!isListOrTodoBlock(block.type)) return false
        if (block.text.text.isNotBlank()) return false

        if (block.indentLevel > 0) {
            toggleIndent(block.id, -1)
            selectedBlockId.value = block.id
            return true
        }

        if (block.type != RichTextBlockType.Paragraph) {
            block.type = RichTextBlockType.Paragraph
            block.isChecked = false
            _lastSelection[block.id] = block.text.selection
            refreshFormattingStateForCurrentSelection()
            rebuildHtml()
            captureHistoryState()
            selectedBlockId.value = block.id
            return true
        }
        return false
    }

    fun handleListOrTodoBackspaceAtStart(blockId: String, nextText: String? = null): Boolean {
        val block = blockById(blockId) ?: return false
        if (!isListOrTodoBlock(block.type)) return false

        val normalizedNextText = nextText ?: block.text.text
        if (normalizedNextText.isNotBlank()) return false
        if (block.text.text != normalizedNextText) {
            block.text = TextFieldValue(normalizedNextText, selection = TextRange(0))
            _lastSelection[block.id] = block.text.selection
        }
        if (block.indentLevel > 0) {
            toggleIndent(block.id, -1)
            selectedBlockId.value = block.id
            return true
        }

        if (block.type != RichTextBlockType.Paragraph) {
            block.type = RichTextBlockType.Paragraph
            block.isChecked = false
            refreshFormattingStateForCurrentSelection()
            rebuildHtml()
            captureHistoryState()
            selectedBlockId.value = block.id
            return true
        }
        return false
    }

    private fun isListOrTodoBlock(type: RichTextBlockType): Boolean = when (type) {
        RichTextBlockType.Bullet, RichTextBlockType.Numbered, RichTextBlockType.Todo -> true
        else -> false
    }

    private fun closeSlashCommandSession() {
        slashCommandSession.value = null
    }

    fun clearSlashCommandSession() {
        slashCommandSession.value = null
    }

    fun executeSlashCommand(type: RichTextBlockType): Boolean {
        val session = slashCommandSession.value ?: return false
        val block = blockById(session.blockId) ?: run {
            clearSlashCommandSession()
            return false
        }

        val text = block.text.text
        val safeEnd = session.queryRangeEnd.coerceIn(0, text.length)
        val safeStart = session.slashStart.coerceIn(0, safeEnd)
        val updatedText = if (safeStart <= safeEnd) {
            text.removeRange(safeStart, safeEnd)
        } else {
            text
        }
        val newCursor = safeStart.coerceIn(0, updatedText.length)
        block.text = TextFieldValue(updatedText, selection = TextRange(newCursor))
        _lastSelection[block.id] = block.text.selection

        clearSlashCommandSession()
        setBlockType(block.id, type)
        stateSelectionAfterSlash(block.id)
        return true
    }

    fun hasActiveSlashSession(blockId: String): Boolean {
        val session = slashCommandSession.value
        return session != null && session.blockId == blockId
    }

    fun splitBlockAtSelection(blockId: String): Boolean {
        val block = blockById(blockId) ?: return false
        if (block.type == RichTextBlockType.Code) return false

        val text = block.text.text
        val selection = block.text.selection
        val start = min(selection.start, selection.end).coerceIn(0, text.length)
        val end = max(selection.start, selection.end).coerceIn(0, text.length)
        if (start == 0 && end == 0 && text.isNotEmpty()) {
            return insertEmptyBlockAfter(blockId)
        }
        val replacement = text.substring(0, start) + "\n" + text.substring(end)
        splitTextAtLineBreak(
            blockId = blockId,
            value = TextFieldValue(
                text = replacement,
                selection = TextRange(start + 1),
            ),
        )
        return true
    }

    private fun insertEmptyBlockAfter(blockId: String): Boolean {
        val block = blockById(blockId) ?: return false
        val index = indexOfBlock(blockId)
        if (index < 0) return false
        if (activeSlashSession?.blockId == blockId) {
            clearSlashCommandSession()
        }

        val nextType = if (block.type == RichTextBlockType.Divider) RichTextBlockType.Paragraph else block.type
        val insertedBlock = QuataRichTextBlock(
            type = nextType,
            text = "",
            checked = if (nextType == RichTextBlockType.Todo) block.isChecked else false,
            indent = block.indentLevel,
        )
        blocks.add(index + 1, insertedBlock)
        textStates.set(insertedBlock.id, insertedBlock.text, programmatic = true)
        spanStates.set(insertedBlock.id, insertedBlock.spans, 0)
        copyPendingStyles(blockId, insertedBlock.id)
        selectedBlockId.value = insertedBlock.id
        selectedBlockIds.clear()
        selectedBlockIds.add(insertedBlock.id)
        selectionAnchorId.value = insertedBlock.id
        _lastSelection[insertedBlock.id] = TextRange(0)
        refreshFormattingStateForCurrentSelection()
        rebuildHtml()
        captureHistoryState()
        return true
    }

    private fun mergeWithPrevious(blockId: String): Boolean {
        val index = indexOfBlock(blockId)
        if (index <= 0) return false
        val current = blockById(blockId) ?: return false
        val previousIndex = index - 1
        val previous = blocks[previousIndex]

        val merged = previous.text.text + current.text.text
        val previousLength = previous.text.text.length
        previous.text = TextFieldValue(merged, selection = TextRange(merged.length))
        previous.spans = QuataSpanAlgorithms.mergeSpans(
            firstSpans = previous.spans,
            secondSpans = current.spans,
            firstTextLength = previousLength,
            mergedTextLength = merged.length,
        )
        textStates.set(previous.id, previous.text, programmatic = true)
        spanStates.set(previous.id, previous.spans, merged.length)
        _lastSelection[previous.id] = previous.text.selection
        if (_pendingInlineStyles[current.id] != null) {
            _pendingInlineStyles.remove(previous.id)
            copyPendingStyles(current.id, previous.id)
        }
        blocks.removeAt(index)
        _pendingInlineStyles.remove(blockId)
        _lastSelection.remove(blockId)
        textStates.remove(blockId)
        spanStates.remove(blockId)
        selectedBlockId.value = previous.id
        refreshFormattingStateForCurrentSelection()
        rebuildHtml()
        captureHistoryState()
        return true
    }

    fun setBlockType(blockId: String, type: RichTextBlockType) {
        if (!dispatchRichTextAction(ConvertRichTextBlockType(blockId, type))) return
        if (!supportsSpans(blockById(blockId)?.type ?: return)) {
            _pendingInlineStyles.remove(blockId)
        }
    }

    fun setBlockTypeFromSlash(blockId: String, type: RichTextBlockType) {
        val block = blockById(blockId) ?: return
        if (type == RichTextBlockType.Divider) {
            block.text = TextFieldValue("")
        }
        setBlockType(blockId, type)
        stateSelectionAfterSlash(blockId)
    }

    fun maybeConvertTypedListPrefix(blockId: String) {
        val block = blockById(blockId) ?: return
        if (block.type != RichTextBlockType.Paragraph) return

        val text = block.text.text
        val orderedMatch = Regex("^(\\d+)\\.\\s+").find(text)
        val removedPrefixLength = when {
            text.startsWith("- ") -> 2
            text.startsWith("* ") -> 2
            orderedMatch != null -> orderedMatch.value.length
            else -> return
        }
        val trimmedText = when {
            text.startsWith("- ") -> text.substring(2)
            text.startsWith("* ") -> text.substring(2)
            orderedMatch != null -> text.substring(orderedMatch.value.length)
            else -> return
        }.trimStart()
        val extraTrim = text.substring(removedPrefixLength).length - trimmedText.length
        val totalRemoved = removedPrefixLength + extraTrim

        block.text = TextFieldValue(
            text = trimmedText,
            selection = TextRange(trimmedText.length),
        )
        block.spans = block.spans.mapNotNull { span ->
            val start = max(span.start, totalRemoved) - totalRemoved
            val end = span.end - totalRemoved
            if (start < end) span.copy(start = start, end = end) else null
        }.let { QuataSpanAlgorithms.normalize(it, trimmedText.length) }

        if (orderedMatch != null) {
            setBlockType(block.id, RichTextBlockType.Numbered)
            return
        }
        if (text.startsWith("- ") || text.startsWith("* ")) {
            setBlockType(block.id, RichTextBlockType.Bullet)
        }
    }

    private fun stateSelectionAfterSlash(blockId: String) {
        if (selectedBlockId.value != blockId) {
            selectedBlockId.value = blockId
        }
        refreshFormattingStateForCurrentSelection()
    }

    fun setTodoChecked(blockId: String, checked: Boolean) {
        dispatchRichTextAction(SetRichTextTodoChecked(blockId, checked))
    }

    fun toggleTodoChecked(blockId: String) {
        val block = blockById(blockId) ?: return
        if (block.type == RichTextBlockType.Todo) {
            dispatchRichTextAction(SetRichTextTodoChecked(blockId, !block.isChecked))
        }
    }

    fun toggleIndent(blockId: String, delta: Int) {
        // Indentation needs an explicit, designed control. Horizontal gestures are reserved for deletion.
        return
    }

    fun toggleHeading(level: Int = 2) {
        val block = currentBlock() ?: return
        dispatchRichTextAction(ToggleRichTextHeading(block.id, level))
    }

    fun toggleList(kind: String) {
        val block = currentBlock() ?: return
        val targetType = when (kind) {
            ORDERED_MARKER -> {
                when {
                    block.type == RichTextBlockType.Numbered -> RichTextBlockType.Paragraph
                    block.type == RichTextBlockType.Todo -> RichTextBlockType.Todo
                    else -> RichTextBlockType.Numbered
                }
            }
            BULLET_MARKER -> {
                if (block.type == RichTextBlockType.Bullet) {
                    RichTextBlockType.Paragraph
                } else {
                    RichTextBlockType.Bullet
                }
            }
            TODO_MARKER -> {
                if (block.type == RichTextBlockType.Todo) {
                    RichTextBlockType.Paragraph
                } else {
                    RichTextBlockType.Todo
                }
            }
            else -> RichTextBlockType.Paragraph
        }
        setBlockType(block.id, targetType)
    }

    fun setQuote() {
        val block = currentBlock() ?: return
        setBlockType(block.id, RichTextBlockType.Quote)
    }

    fun setInfo() {
        val block = currentBlock() ?: return
        setBlockType(block.id, RichTextBlockType.Info)
    }

    fun setCode() {
        val block = currentBlock() ?: return
        setBlockType(block.id, RichTextBlockType.Code)
    }

    fun setDivider() {
        val block = currentBlock() ?: return
        setBlockType(block.id, RichTextBlockType.Divider)
    }

    fun getDragPreviewBlock(): QuataRichTextBlock? = selectedBlockId.value?.let(::blockById)

    fun getDragPreviewBlock(blockId: String): QuataRichTextBlock? = blockById(blockId)

    fun getDragPayloadSize(): Int {
        val roots = activeRootTargets()
        val payloadIndices = if (roots.isNotEmpty()) {
            resolveSubtreePayload(roots)
        } else {
            selectedBlockId.value?.let { blockId ->
                indexOfBlock(blockId).takeIf { it >= 0 }?.let { listOf(it) } ?: emptyList()
            } ?: emptyList()
        }

        return payloadIndices.size.coerceAtLeast(1)
    }

    fun setHtml(rawHtml: String) {
        clearSlashCommandSession()
        val parsed = parseHtmlToRichTextBlocks(rawHtml)
        blocks.clear()
        if (parsed.isEmpty()) {
            blocks.add(newBlock())
        } else {
            parsed.forEach { blocks.add(it) }
        }
        syncRuntimeHolders()
        selectedBlockId.value = blocks.first().id
        selectedBlockIds.clear()
        selectedBlockIds.add(blocks.first().id)
        _pendingInlineStyles.clear()
        _lastSelection.clear()
        selectSingleBlock(blocks.firstOrNull()?.id)
        refreshFormattingStateForCurrentSelection()
        rebuildHtml()
        clearHistoryState()
    }

    fun setLinkForSelection(url: String): Boolean {
        val block = currentBlock() ?: return false
        val current = block.text
        val selection = current.selection
        val normalized = sanitizeUrl(url)
        val start = min(selection.start, selection.end).coerceIn(0, current.text.length)
        val end = max(selection.start, selection.end).coerceIn(0, current.text.length)
        if (start == end) return false

        block.spans = if (normalized.isBlank()) {
            spanStates.removeLinkSpans(block.id, start, end)
            spanStates.getSpans(block.id)
        } else {
            spanStates.applyStyle(block.id, start, end, QuataSpanStyle.Link(normalized), current.text.length)
            spanStates.getSpans(block.id)
        }
        _lastSelection[block.id] = block.text.selection
        refreshFormattingStateForCurrentSelection()
        rebuildHtml()
        captureHistoryState()
        return normalized.isNotBlank()
    }

    fun setLinkForTarget(target: QuataLinkTarget, url: String): Boolean {
        val block = blockById(target.blockId) ?: return false
        block.text = block.text.copy(selection = target.range)
        _lastSelection[block.id] = target.range
        return setLinkForSelection(url)
    }

    fun removeLinkForTarget(target: QuataLinkTarget): Boolean {
        val block = blockById(target.blockId) ?: return false
        spanStates.removeLinkSpans(block.id, target.range.start, target.range.end)
        block.spans = spanStates.getSpans(block.id)
        block.text = block.text.copy(selection = target.range)
        _lastSelection[block.id] = target.range
        refreshFormattingStateForCurrentSelection()
        rebuildHtml()
        captureHistoryState()
        return true
    }

    fun resolveLinkTarget(blockId: String, offset: Int? = null): QuataLinkTarget? {
        val block = blockById(blockId) ?: return null
        val selection = block.text.selection
        val probe = offset ?: if (selection.start == selection.end) selection.start else min(selection.start, selection.end)
        return QuataLinkHitTester.resolve(block, probe)
    }

    fun toggleBold() = toggleInlineFormat(InlineFormat.Bold)
    fun toggleItalic() = toggleInlineFormat(InlineFormat.Italic)
    fun toggleUnderline() = toggleInlineFormat(InlineFormat.Underline)
    fun toggleStrikethrough() = toggleInlineFormat(InlineFormat.Strike)
    fun toggleInlineCode() = toggleInlineFormat(InlineFormat.InlineCode)
    fun toggleHighlight() = toggleInlineFormat(InlineFormat.Highlight)

    fun applyInlineStyleToLineStart(format: InlineFormat, blockId: String) {
        val block = blockById(blockId) ?: return
        val current = block.text
        val lineStart = lastLineStart(current.text, current.selection.start)
        val start = lineStart.coerceIn(0, current.text.length)
        val end = max(current.selection.start, current.selection.end).coerceIn(start, current.text.length)
        if (start == end) {
            setPending(blockId, format, !hasPending(blockId, format))
        } else {
            spanStates.applyStyle(block.id, start, end, format.toSpanStyle(), current.text.length)
            block.spans = spanStates.getSpans(block.id)
        }
        _lastSelection[blockId] = block.text.selection
        refreshFormattingStateForCurrentSelection()
        rebuildHtml()
        captureHistoryState()
    }

    private fun normalizePlainText(value: String): String = value.replace("\u0000", "")

    private fun computeTextEdit(previous: String, current: String): TextEdit? {
        if (previous == current) return null
        val minLength = min(previous.length, current.length)
        var prefixLength = 0
        while (prefixLength < minLength && previous[prefixLength] == current[prefixLength]) {
            prefixLength++
        }

        val maxSuffixLength = min(previous.length - prefixLength, current.length - prefixLength)
        var suffixLength = 0
        while (
            suffixLength < maxSuffixLength &&
            previous[previous.length - 1 - suffixLength] == current[current.length - 1 - suffixLength]
        ) {
            suffixLength++
        }

        val deletedLength = previous.length - prefixLength - suffixLength
        val insertedLength = current.length - prefixLength - suffixLength
        if (deletedLength == 0 && insertedLength == 0) return null
        return TextEdit(
            start = prefixLength,
            deletedLength = deletedLength.coerceAtLeast(0),
            insertedLength = insertedLength.coerceAtLeast(0),
        )
    }

    private fun adjustSpansForUserEdit(
        blockId: String,
        spans: List<QuataTextSpan>,
        edit: TextEdit,
        textLength: Int,
    ): List<QuataTextSpan> {
        var adjusted = QuataSpanAlgorithms.adjustForEdit(
            spans = spans,
            editStart = edit.start,
            deletedLength = edit.deletedLength,
            insertedLength = edit.insertedLength,
            textLength = textLength,
        )
        if (edit.insertedLength > 0) {
            val pending = _pendingInlineStyles[blockId].orEmpty().map { it.toSpanStyle() } +
                spanStates.resolveStylesForInsertion(blockId, edit.start)
            for (style in pending) {
                adjusted = QuataSpanAlgorithms.applyStyle(
                    spans = adjusted,
                    rangeStart = edit.start,
                    rangeEnd = edit.start + edit.insertedLength,
                    style = style,
                    textLength = textLength,
                )
            }
        }
        spanStates.set(blockId, adjusted, textLength)
        return adjusted
    }

    private fun lastLineStart(text: String, index: Int): Int {
        return text.lastIndexOf('\n', max(0, index - 1)).let { found -> if (found == -1) 0 else found + 1 }
    }

    private fun setPending(blockId: String, format: InlineFormat, enabled: Boolean) {
        val active = _pendingInlineStyles.getOrPut(blockId) { mutableSetOf() }
        if (enabled) {
            active.add(format)
        } else {
            active.remove(format)
        }
        spanStates.setPendingStyles(blockId, active.map { it.toSpanStyle() }.toSet())
    }

    private fun hasPending(blockId: String, format: InlineFormat): Boolean {
        return _pendingInlineStyles[blockId]?.contains(format) == true
    }

    private fun isMarkerToggled(blockId: String, marker: String, selection: TextRange, text: String): Boolean {
        if (selection.start == selection.end) {
            if (selection.start >= marker.length && selection.start + marker.length <= text.length) {
                return text.regionMatches(selection.start - marker.length, marker, 0, marker.length) &&
                    text.regionMatches(selection.start, marker, 0, marker.length)
            }
            return hasPending(blockId, markerToFormat(marker))
        }
        val wrapped = isWrappedWith(text, selection.start, selection.end, marker, marker)
        return wrapped || hasPending(blockId, markerToFormat(marker))
    }

    private fun markerToFormat(marker: String): InlineFormat = when (marker) {
        "**" -> InlineFormat.Bold
        "*" -> InlineFormat.Italic
        "__" -> InlineFormat.Underline
        "~~" -> InlineFormat.Strike
        "`" -> InlineFormat.InlineCode
        "==" -> InlineFormat.Highlight
        else -> InlineFormat.Bold
    }

    private fun markerForFormat(format: InlineFormat): String = when (format) {
        InlineFormat.Bold -> "**"
        InlineFormat.Italic -> "*"
        InlineFormat.Underline -> "__"
        InlineFormat.Strike -> "~~"
        InlineFormat.InlineCode -> "`"
        InlineFormat.Highlight -> "=="
    }

    private fun InlineFormat.toSpanStyle(): QuataSpanStyle = when (this) {
        InlineFormat.Bold -> QuataSpanStyle.Bold
        InlineFormat.Italic -> QuataSpanStyle.Italic
        InlineFormat.Underline -> QuataSpanStyle.Underline
        InlineFormat.Strike -> QuataSpanStyle.Strike
        InlineFormat.InlineCode -> QuataSpanStyle.InlineCode
        InlineFormat.Highlight -> QuataSpanStyle.Highlight
    }

    private fun toggleInlineFormat(format: InlineFormat) {
        val block = currentBlock() ?: return
        val current = block.text
        val selection = current.selection
        val start = min(selection.start, selection.end).coerceIn(0, current.text.length)
        val end = max(selection.start, selection.end).coerceIn(0, current.text.length)
        val spanStyle = format.toSpanStyle()

        if (start == end) {
            val active = QuataSpanAlgorithms.queryStyleStatus(block.spans, start, end, spanStyle) == QuataStyleStatus.FullyActive ||
                hasPending(block.id, format)
            setPending(block.id, format, !active)
        } else {
            spanStates.toggleStyle(block.id, start, end, spanStyle, current.text.length)
            block.spans = spanStates.getSpans(block.id)
        }
        block.text = current.copy(selection = selection)
        _lastSelection[block.id] = block.text.selection
        refreshFormattingStateForCurrentSelection()
        rebuildHtml()
        captureHistoryState()
    }

    private fun toggleCollapsedInlineMarker(
        blockId: String,
        current: TextFieldValue,
        marker: String,
        cursor: Int,
        format: InlineFormat,
    ): TextFieldValue {
        val active = isMarkerToggled(blockId, marker, TextRange(cursor), current.text)
        val isAtPlaceHolder = cursor >= marker.length &&
            cursor + marker.length <= current.text.length &&
            current.text.regionMatches(cursor - marker.length, marker, 0, marker.length) &&
            current.text.regionMatches(cursor, marker, 0, marker.length)
        if (active && isAtPlaceHolder) {
            setPending(blockId, format, false)
            val start = cursor - marker.length
            val end = cursor + marker.length
            val text = current.text.removeRange(start, end)
            return TextFieldValue(text = text, selection = TextRange(start))
        }
        setPending(blockId, format, !active)
        val updated = current.text.substring(0, cursor) + marker + marker + current.text.substring(cursor)
        return TextFieldValue(updated, selection = TextRange(cursor + marker.length))
    }

    private fun isWrappedWith(text: String, start: Int, end: Int, before: String, after: String): Boolean {
        return start >= before.length && end + after.length <= text.length &&
            text.regionMatches(start - before.length, before, 0, before.length) &&
            text.regionMatches(end, after, 0, after.length)
    }

    private fun removeWrapped(text: String, start: Int, end: Int, before: String, after: String): String {
        if (!isWrappedWith(text, start, end, before, after)) return text
        val removedStart = start - before.length
        val removedEnd = end + after.length
        return text.substring(0, removedStart) + text.substring(start, end) + text.substring(removedEnd)
    }

    private fun wrapSelection(text: String, start: Int, end: Int, before: String, after: String): TextFieldValue {
        val updated = text.substring(0, start) + before + text.substring(start, end) + after + text.substring(end)
        return TextFieldValue(
            text = updated,
            selection = TextRange(start + before.length, end + before.length),
        )
    }

    private fun replaceRange(source: String, start: Int, end: Int, replacement: String): String {
        if (start < 0 || end < start) return source
        val safeStart = min(source.length, start)
        val safeEnd = min(source.length, max(end, start))
        return source.substring(0, safeStart) + replacement + source.substring(safeEnd)
    }

    private fun unwrapSelectionLink(text: String, start: Int, end: Int): LinkUnwrapResult {
        val startOpen = text.lastIndexOf('[', start.coerceAtLeast(0))
        if (startOpen < 0) return LinkUnwrapResult(text, start, end)
        val startClose = text.indexOf(']', startOpen)
        if (startClose < 0 || startClose > end) return LinkUnwrapResult(text, start, end)
        val openParen = text.indexOf('(', startClose + 1).takeIf { it != -1 } ?: return LinkUnwrapResult(text, start, end)
        val closeParen = text.indexOf(')', openParen + 1)
        if (closeParen == -1 || start < startClose) {
            return LinkUnwrapResult(text, start, end)
        }
        val anchorStart = startOpen
        val anchorEnd = closeParen + 1
        val inner = text.substring(startOpen + 1, startClose)
        val updated = text.substring(0, anchorStart) + inner + text.substring(anchorEnd)
        val selectionStart = anchorStart
        val selectionEnd = anchorStart + inner.length
        return LinkUnwrapResult(updated, selectionStart, selectionEnd)
    }

    private fun isSelectionWrappedByLink(text: String, start: Int, end: Int): Boolean {
        if (start == end) return false
        val linkStart = text.lastIndexOf("[", start).takeIf { it >= 0 } ?: return false
        val closeText = text.indexOf("](", linkStart)
        if (closeText == -1 || closeText < start) return false
        val closeUrl = text.indexOf(")", closeText)
        return closeUrl != -1 && start >= linkStart && end <= closeUrl
    }

    private fun copyPendingStyles(fromId: String, toId: String) {
        val styles = _pendingInlineStyles[fromId] ?: return
        _pendingInlineStyles[toId] = styles.toMutableSet()
    }

    private fun sanitizeUrl(raw: String): String {
        val href = raw.trim()
        if (href.isBlank()) return ""
        if (href.startsWith("//")) return "https:$href"
        return if (href.contains("://") || href.startsWith("mailto:") || href.startsWith("tel:") || href.startsWith("sms:") || href.startsWith("#")) {
            href
        } else {
            "https://$href"
        }
    }

    private fun rebuildHtml() {
        val rendered = convertBlocksToHtml(blocks.toList())
        val safe = sanitizeGeneratedHtml(rendered)
        htmlState.value = safe
    }

    private fun snapshotDocumentState(): QuataRichTextDocumentState {
        return QuataRichTextDocumentState(
            blocks = blocks.map { it.toModel() },
            selectedBlockId = selectedBlockId.value,
            selectedBlockIds = selectedBlockIds.toList(),
            selectionAnchorId = selectionAnchorId.value,
            pendingInlineStyles = _pendingInlineStyles.mapValues { entry -> entry.value.toSet() },
            lastSelection = _lastSelection.toMap().mapValues { entry ->
                TextRange(entry.value.start, entry.value.end)
            },
        )
    }

    private fun applyDocumentState(snapshot: QuataRichTextDocumentState) {
        blocks.clear()
        _lastSelection.clear()
        _pendingInlineStyles.clear()

        snapshot.blocks.forEach { model ->
            val block = model.toBlock()
            blocks.add(block)
            _lastSelection[block.id] = block.text.selection
        }

        snapshot.lastSelection.forEach { (id, selection) ->
            val block = blockById(id) ?: return@forEach
            _lastSelection[id] = TextRange(
                selection.start.coerceIn(0, block.text.text.length),
                selection.end.coerceIn(0, block.text.text.length),
            )
        }

        snapshot.pendingInlineStyles.forEach { (id, styles) ->
            if (styles.isNotEmpty() && blockById(id) != null) {
                _pendingInlineStyles[id] = styles.toMutableSet()
            }
        }

        selectedBlockIds.clear()
        snapshot.selectedBlockIds.forEach { id ->
            if (blockById(id) != null) {
                selectSetAdd(id)
            }
        }

        val safeSelectedId = snapshot.selectedBlockId?.takeIf { blockById(it) != null }
        when {
            safeSelectedId != null -> selectedBlockId.value = safeSelectedId
            selectedBlockIds.isNotEmpty() -> selectedBlockId.value = selectedBlockIds.first()
            else -> selectedBlockId.value = blocks.firstOrNull()?.id
        }

        val safeAnchorId = snapshot.selectionAnchorId?.takeIf { blockById(it) != null }
        selectionAnchorId.value = safeAnchorId ?: selectedBlockId.value
        if (selectedBlockIds.isEmpty()) {
            selectedBlockId.value?.let(::selectSetAdd)
        }

        syncRuntimeHolders()
        refreshFormattingStateForCurrentSelection()
        rebuildHtml()
    }

    private fun dispatchRichTextAction(action: QuataRichTextAction, recordHistory: Boolean = true): Boolean {
        val before = snapshotDocumentState()
        val after = action.reduce(before)
        if (after == before) return false
        applyDocumentState(after)
        if (recordHistory) {
            captureHistoryState()
        }
        return true
    }

    private fun snapshotHistoryState(): QuataRichTextHistoryEntry {
        val blockSnapshots = blocks.map { block ->
            QuataRichTextHistoryBlock(
                id = block.id,
                type = block.type,
                text = block.text.text,
                selectionStart = block.text.selection.start,
                selectionEnd = block.text.selection.end,
                isChecked = block.isChecked,
                indentLevel = block.indentLevel,
                spans = block.spans,
            )
        }
        val lastSelection = _lastSelection.toMap().mapValues { entry ->
            TextRange(entry.value.start, entry.value.end)
        }
        val pendingStyles = _pendingInlineStyles.mapValues { entry ->
            entry.value.toSet()
        }
        return QuataRichTextHistoryEntry(
            blocks = blockSnapshots,
            selectedBlockId = selectedBlockId.value,
            selectedBlockIds = selectedBlockIds.toList(),
            selectionByBlockId = lastSelection,
            pendingInlineStyles = pendingStyles,
        )
    }

    private fun applyHistoryState(snapshot: QuataRichTextHistoryEntry?, pushHistory: Boolean = true) {
        snapshot ?: return
        blocks.clear()
        _lastSelection.clear()
        _pendingInlineStyles.clear()
        for (snapshotBlock in snapshot.blocks) {
            val block = QuataRichTextBlock(
                id = snapshotBlock.id,
                type = snapshotBlock.type,
                text = snapshotBlock.text,
                checked = snapshotBlock.isChecked,
                indent = snapshotBlock.indentLevel,
                spans = snapshotBlock.spans,
            )
            block.text = TextFieldValue(
                text = snapshotBlock.text,
                selection = TextRange(
                    snapshotBlock.selectionStart.coerceIn(0, snapshotBlock.text.length),
                    snapshotBlock.selectionEnd.coerceIn(0, snapshotBlock.text.length),
                ),
            )
            blocks.add(block)
            _lastSelection[block.id] = block.text.selection
        }
        snapshot.pendingInlineStyles.forEach { (id, styles) ->
            if (styles.isNotEmpty()) {
                _pendingInlineStyles[id] = styles.toMutableSet()
            }
        }
        selectedBlockIds.clear()
        snapshot.selectionByBlockId.entries.forEach { (id, selection) ->
            if (_lastSelection.containsKey(id)) {
                _lastSelection[id] = TextRange(
                    selection.start.coerceIn(0, blockById(id)?.text?.text?.length ?: 0),
                    selection.end.coerceIn(0, blockById(id)?.text?.text?.length ?: 0),
                )
            }
        }
        snapshot.selectedBlockIds.forEach { id ->
            if (blockById(id) != null) {
                selectSetAdd(id)
            }
        }
        val safeSelectionId = snapshot.selectedBlockId?.takeIf { blockById(it) != null }
        if (safeSelectionId != null) {
            selectedBlockId.value = safeSelectionId
            selectionAnchorId.value = safeSelectionId
        } else if (selectedBlockIds.isNotEmpty()) {
            selectedBlockId.value = selectedBlockIds.first()
            selectionAnchorId.value = selectedBlockId.value
        } else {
            selectSingleBlock(blocks.firstOrNull()?.id)
        }
        syncRuntimeHolders()
        refreshFormattingStateForCurrentSelection()
        rebuildHtml()
        if (pushHistory) {
            captureHistoryState()
        }
    }

    private fun applyDragSnapshotInternal(snapshot: QuataRichTextDragSnapshot) {
        blocks.clear()
        _lastSelection.clear()
        _pendingInlineStyles.clear()
        for (snapshotBlock in snapshot.blocks) {
            val block = QuataRichTextBlock(
                id = snapshotBlock.id,
                type = snapshotBlock.type,
                text = snapshotBlock.text,
                checked = snapshotBlock.isChecked,
                indent = snapshotBlock.indentLevel,
                spans = snapshotBlock.spans,
            )
            block.text = TextFieldValue(
                text = snapshotBlock.text,
                selection = TextRange(
                    snapshotBlock.selectionStart.coerceIn(0, snapshotBlock.text.length),
                    snapshotBlock.selectionEnd.coerceIn(0, snapshotBlock.text.length),
                ),
            )
            blocks.add(block)
            _lastSelection[block.id] = block.text.selection
        }

        snapshot.pendingInlineStyles.forEach { (id, styles) ->
            if (styles.isNotEmpty()) {
                _pendingInlineStyles[id] = styles.toMutableSet()
            }
        }
        selectedBlockIds.clear()
        snapshot.selectionByBlockId.entries.forEach { (id, selection) ->
            if (blocks.any { it.id == id }) {
                _lastSelection[id] = TextRange(
                    selection.start.coerceIn(0, blockById(id)?.text?.text?.length ?: 0),
                    selection.end.coerceIn(0, blockById(id)?.text?.text?.length ?: 0),
                )
            }
        }
        snapshot.selectedBlockIds.forEach { id ->
            if (blockById(id) != null) {
                selectSetAdd(id)
            }
        }
        val safeSelectionId = snapshot.selectedBlockId?.takeIf { blockById(it) != null }
        if (safeSelectionId != null) {
            selectedBlockId.value = safeSelectionId
            selectionAnchorId.value = safeSelectionId
        } else if (selectedBlockIds.isNotEmpty()) {
            selectedBlockId.value = selectedBlockIds.first()
            selectionAnchorId.value = selectedBlockId.value
        } else {
            selectSingleBlock(blocks.firstOrNull()?.id)
        }
        syncRuntimeHolders()
        refreshFormattingStateForCurrentSelection()
    }

    private fun clearHistoryState() {
        historyStack.clear()
        historyCursor = -1
        canUndo = false
        canRedo = false
        resetTextHistoryBatch()
        captureHistoryState()
    }

    private fun captureTextHistoryState(blockId: String) {
        captureHistoryState(textBlockId = blockId)
    }

    private fun captureHistoryState(textBlockId: String? = null) {
        if (isApplyingHistory) return
        val snapshot = snapshotHistoryState()
        val now = richTextClockMillis()
        val shouldMergeText = textBlockId != null &&
            textBlockId == lastTextHistoryBlockId &&
            now - lastTextHistoryTimestampMs <= TEXT_HISTORY_MERGE_WINDOW_MS &&
            historyCursor == historyStack.lastIndex &&
            historyCursor > 0

        if (historyStack.size > 0 && historyCursor >= 0 && historyCursor < historyStack.size) {
            if (historyStack[historyCursor] == snapshot) {
                return
            }
            if (shouldMergeText) {
                historyStack[historyCursor] = snapshot
                lastTextHistoryTimestampMs = now
                updateHistoryAvailability()
                return
            }
            if (historyCursor < historyStack.lastIndex) {
                val removeCount = historyStack.size - historyCursor - 1
                repeat(removeCount) { historyStack.removeAt(historyStack.lastIndex) }
            }
        }
        historyStack.add(snapshot)
        if (historyStack.size > maxHistoryDepth) {
            historyStack.removeAt(0)
            historyCursor--
        }
        historyCursor = historyStack.lastIndex
        if (textBlockId != null) {
            lastTextHistoryBlockId = textBlockId
            lastTextHistoryTimestampMs = now
        } else {
            resetTextHistoryBatch()
        }
        updateHistoryAvailability()
    }

    private fun resetTextHistoryBatch() {
        lastTextHistoryBlockId = null
        lastTextHistoryTimestampMs = 0L
    }

    private fun syncRuntimeHolders() {
        val existingIds = blocks.map { it.id }.toSet()
        for (block in blocks) {
            textStates.set(block.id, block.text)
            spanStates.set(block.id, block.spans, block.text.text.length)
        }
        textStates.cleanup(existingIds)
        spanStates.cleanup(existingIds)
    }

    private fun updateHistoryAvailability() {
        canUndo = historyCursor > 0
        canRedo = historyCursor in 0 until historyStack.lastIndex
    }

    private fun currentBlock(): QuataRichTextBlock? = selectedBlockId.value?.let(::blockById)

    private fun blockById(blockId: String): QuataRichTextBlock? = blocks.firstOrNull { it.id == blockId }

    private fun indexOfBlock(blockId: String): Int = blocks.indexOfFirst { it.id == blockId }

    private fun refreshFormattingStateForCurrentSelection() {
        val block = currentBlock()
        if (block == null) {
            isSelectionCollapsed.value = true
            isBold.value = false
            isItalic.value = false
            isUnderline.value = false
            isStrikethrough.value = false
            isInlineCode.value = false
            isHighlight.value = false
            isHeading.value = false
            selectedHeadingLevel.intValue = 0
            isBulletedList.value = false
            isNumberedList.value = false
            isTodo.value = false
            isQuote.value = false
            isInfo.value = false
            isCode.value = false
            isDivider.value = false
            isLinked.value = false
            return
        }

        val selection = _lastSelection[block.id] ?: block.text.selection
        isSelectionCollapsed.value = selection.start == selection.end

        isHeading.value = block.type in setOf(
            RichTextBlockType.Heading1,
            RichTextBlockType.Heading2,
            RichTextBlockType.Heading3,
            RichTextBlockType.Heading4,
            RichTextBlockType.Heading5,
            RichTextBlockType.Heading6,
        )
        selectedHeadingLevel.intValue = when (block.type) {
            RichTextBlockType.Heading1 -> 1
            RichTextBlockType.Heading2 -> 2
            RichTextBlockType.Heading3 -> 3
            RichTextBlockType.Heading4 -> 4
            RichTextBlockType.Heading5 -> 5
            RichTextBlockType.Heading6 -> 6
            else -> 0
        }
        isBulletedList.value = block.type == RichTextBlockType.Bullet
        isNumberedList.value = block.type == RichTextBlockType.Numbered
        isTodo.value = block.type == RichTextBlockType.Todo
        isQuote.value = block.type == RichTextBlockType.Quote
        isInfo.value = block.type == RichTextBlockType.Info
        isCode.value = block.type == RichTextBlockType.Code
        isDivider.value = block.type == RichTextBlockType.Divider

        isBold.value = isSpanActive(block.id, block.spans, selection, InlineFormat.Bold)
        isItalic.value = isSpanActive(block.id, block.spans, selection, InlineFormat.Italic)
        isUnderline.value = isSpanActive(block.id, block.spans, selection, InlineFormat.Underline)
        isStrikethrough.value = isSpanActive(block.id, block.spans, selection, InlineFormat.Strike)
        isInlineCode.value = isSpanActive(block.id, block.spans, selection, InlineFormat.InlineCode)
        isHighlight.value = isSpanActive(block.id, block.spans, selection, InlineFormat.Highlight)
        isLinked.value = QuataSpanAlgorithms.queryStyleStatus(
            block.spans,
            selection.start,
            selection.end,
            QuataSpanStyle.Link("https://quata.local"),
        ) != QuataStyleStatus.Absent
    }

    private fun isSpanActive(
        blockId: String,
        spans: List<QuataTextSpan>,
        selection: TextRange,
        format: InlineFormat,
    ): Boolean {
        return QuataSpanAlgorithms.queryStyleStatus(
            spans,
            selection.start,
            selection.end,
            format.toSpanStyle(),
        ) == QuataStyleStatus.FullyActive || hasPending(blockId, format)
    }

    private fun isLinkAt(blockId: String, text: String, selection: TextRange): Boolean {
        return isSelectionWrappedByLink(text, selection.start, selection.end)
    }

    private fun newBlock(): QuataRichTextBlock {
        return QuataRichTextBlock(type = DEFAULT_BLOCK_TYPE)
    }

    private fun clampSelection(selection: TextRange, text: String): TextRange {
        return TextRange(
            selection.start.coerceIn(0, text.length),
            selection.end.coerceIn(0, text.length),
        )
    }
}

private fun supportsIndent(type: RichTextBlockType): Boolean = false

private fun supportsSpans(type: RichTextBlockType): Boolean = when (type) {
    RichTextBlockType.Code,
    RichTextBlockType.Divider -> false
    else -> true
}

private fun List<Int>.toIntRanges(): List<IntRange> {
    if (isEmpty()) return emptyList()
    val sortedIndices = sorted()
    val ranges = mutableListOf<IntRange>()

    var rangeStart = sortedIndices.first()
    var previous = rangeStart
    for (index in 1 until sortedIndices.size) {
        val current = sortedIndices[index]
        if (current == previous + 1) {
            previous = current
        } else {
            ranges.add(rangeStart..previous)
            rangeStart = current
            previous = current
        }
    }
    ranges.add(rangeStart..previous)
    return ranges
}

private fun Int.isInsidePayloadRanges(payloadRanges: List<IntRange>): Boolean {
    return payloadRanges.any { range -> this > range.first && this <= range.last }
}

private fun List<QuataRichTextBlock>.isValidIndentationOutline(maxIndentLevel: Int): Boolean {
    if (isEmpty()) return true

    for (block in this) {
        val indent = block.indentLevel
        if (indent !in 0..maxIndentLevel) return false
        if (!supportsIndent(block.type) && indent != 0) return false
    }
    return true
}

private fun List<Int>.isContiguousRange(): Boolean {
    if (isEmpty()) return false
    return last() - first() + 1 == size
}

private fun QuataRichTextBlock.copyForMove(indentLevel: Int): QuataRichTextBlock {
    val moved = QuataRichTextBlock(
        id = id,
        type = type,
        text = text.text,
        checked = isChecked,
        indent = indentLevel,
        spans = spans,
    )
    moved.text = text
    return moved
}

private data class QuataRichTextHistoryEntry(
    val blocks: List<QuataRichTextHistoryBlock>,
    val selectedBlockId: String?,
    val selectedBlockIds: List<String>,
    val selectionByBlockId: Map<String, TextRange>,
    val pendingInlineStyles: Map<String, Set<InlineFormat>>,
)

public data class QuataRichTextDragSnapshot(
    val blocks: List<QuataRichTextBlockSnapshot>,
    val selectedBlockId: String?,
    val selectedBlockIds: List<String>,
    val selectionByBlockId: Map<String, TextRange>,
    val pendingInlineStyles: Map<String, Set<InlineFormat>>,
)

public data class QuataTextDragMachine(
    val anchorBlockId: String,
    val anchorInitialIndex: Int,
    val primaryRootId: String,
    val primaryRootOriginalIndex: Int,
    val primaryRootOriginalIndent: Int,
    val primaryRootSupportsIndent: Boolean,
    val dragSnapshot: QuataRichTextDragSnapshot,
    val dragRootIds: List<String>,
    val payloadBlockIds: List<String>,
    val payloadBlockIdSet: Set<String>,
    val payloadIndices: List<Int>,
    val payloadIndexSet: Set<Int>,
    val payloadRanges: List<IntRange>,
    val originalRootIndentLevels: Map<String, Int>,
    val payloadRelativeDepthOffsets: Map<String, Int>,
    val payloadRootIdsByBlockId: Map<String, String>,
    val payloadDepthOffsetRange: IntRange,
    var targetIndex: Int? = null,
    var futureRootIndent: Int? = null,
)

private data class QuataTextDragHoverTarget(
    val targetIndex: Int,
    val futureRootIndent: Int,
)

public data class QuataRichTextBlockSnapshot(
    val id: String,
    val type: RichTextBlockType,
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val isChecked: Boolean,
    val indentLevel: Int,
    val spans: List<QuataTextSpan>,
)

private data class QuataRichTextHistoryBlock(
    val id: String,
    val type: RichTextBlockType,
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val isChecked: Boolean,
    val indentLevel: Int,
    val spans: List<QuataTextSpan>,
)

private data class LinkUnwrapResult(
    val text: String,
    val newSelectionStart: Int,
    val newSelectionEnd: Int,
)

public data class TextEdit(
    val start: Int,
    val deletedLength: Int,
    val insertedLength: Int,
)

public data class QuataRichTextDragPayload(
    val primaryRootId: String,
    val primaryRootIndent: Int,
    val primarySupportsIndent: Boolean,
    val dragRootIds: List<String>,
    val payloadBlockIds: List<String>,
    val payloadIndices: List<Int>,
    val payloadRanges: List<IntRange>,
    val originalRootIndentLevels: Map<String, Int>,
    val payloadRelativeDepthOffsets: Map<String, Int>,
    val payloadRootIdsByBlockId: Map<String, String>,
    val payloadDepthOffsetRange: IntRange,
)

public data class QuataTextSlashSession(
    val blockId: String,
    val slashStart: Int,
    val queryRangeEnd: Int,
    val query: String,
)

