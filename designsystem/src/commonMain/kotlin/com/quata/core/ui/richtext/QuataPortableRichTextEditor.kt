package com.quata.core.ui.richtext

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times

const val QuataPortableRichTextFieldTestTag = "quata-portable-rich-text-field"
const val QuataPortableRichTextFieldFocusTargetTestTag = "quata-portable-rich-text-focus-target"
private val PortableIndentUnit = 20.dp
private val PortableDragAutoScrollHotZone = 56.dp
private val PortableDragAutoScrollStep = 32.dp

@Composable
fun QuataPortableRichTextEditorBox(
    initialHtml: String,
    placeholder: String,
    onHtmlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenLink: ((String) -> Unit)? = null,
) {
    val state = remember { QuataRichTextEditorState(initialHtml) }
    val html = state.html
    LaunchedEffect(initialHtml) {
        if (initialHtml != state.html) {
            state.setHtml(initialHtml)
        }
    }
    LaunchedEffect(html) {
        onHtmlChange(html)
    }
    QuataPortableRichTextEditor(
        state = state,
        placeholder = placeholder,
        onOpenLink = onOpenLink,
        modifier = modifier,
    )
}

@Composable
private fun QuataPortableRichTextEditor(
    state: QuataRichTextEditorState,
    placeholder: String,
    modifier: Modifier = Modifier,
    onOpenLink: ((String) -> Unit)? = null,
) {
    var showHeadingDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkTarget by remember { mutableStateOf<QuataLinkTarget?>(null) }
    var activeLinkTarget by remember { mutableStateOf<QuataLinkTarget?>(null) }
    val slashRegistry = remember { QuataSlashCommandRegistry() }
    val slashExecutor = remember(state) { QuataSlashCommandExecutor(state) }
    val density = LocalDensity.current
    val uriHandler = LocalUriHandler.current
    val indentUnitPx = with(density) { PortableIndentUnit.toPx() }
    val dragAutoScrollHotZonePx = with(density) { PortableDragAutoScrollHotZone.toPx() }
    val dragAutoScrollStepPx = with(density) { PortableDragAutoScrollStep.toPx() }
    val listState = rememberLazyListState()
    var listBounds by remember { mutableStateOf<Rect?>(null) }
    val blockBounds = remember { mutableStateMapOf<String, Rect>() }
    var draggedBlockId by remember { mutableStateOf<String?>(null) }
    var dragPointerYInRoot by remember { mutableStateOf<Float?>(null) }
    val dragAccumulatorX = remember { mutableFloatStateOf(0f) }
    val resolveTargetIndex: (Float) -> Int = { pointerY ->
        state.blocks
            .mapIndexedNotNull { index, block ->
                blockBounds[block.id]?.let { bounds -> index to ((bounds.top + bounds.bottom) / 2f) }
            }
            .firstOrNull { (_, centerY) -> pointerY < centerY }
            ?.first ?: state.blocks.size
    }
    val resolveFutureIndent: (Int) -> Int? = { targetIndex ->
        state.resolveDragFutureRootIndentation(
            targetIndex = targetIndex,
            horizontalDragDeltaPx = dragAccumulatorX.floatValue,
            indentUnitPx = indentUnitPx,
            anchorBlockId = draggedBlockId,
        )
    }
    val dragAutoScrollDelta: (Float) -> Float = { pointerYInRoot ->
        val bounds = listBounds
        if (bounds == null) {
            0f
        } else {
            when {
                pointerYInRoot < bounds.top + dragAutoScrollHotZonePx -> -dragAutoScrollStepPx
                pointerYInRoot > bounds.bottom - dragAutoScrollHotZonePx -> dragAutoScrollStepPx
                else -> 0f
            }
        }
    }
    LaunchedEffect(draggedBlockId) {
        val activeDragId = draggedBlockId ?: return@LaunchedEffect
        while (draggedBlockId == activeDragId) {
            val pointerYInRoot = dragPointerYInRoot
            val delta = pointerYInRoot?.let(dragAutoScrollDelta) ?: 0f
            if (delta != 0f && pointerYInRoot != null) {
                listState.scrollBy(delta)
                val targetIndex = resolveTargetIndex(pointerYInRoot)
                state.updateDragSession(
                    targetIndex = targetIndex,
                    futureRootIndent = resolveFutureIndent(targetIndex),
                    horizontalDragDeltaPx = dragAccumulatorX.floatValue,
                    indentUnitPx = indentUnitPx,
                    anchorBlockId = activeDragId,
                )
            }
            withFrameNanos { }
        }
    }
    LaunchedEffect(state.selectedBlockId.value, state.isSelectionCollapsed.value, state.html, draggedBlockId) {
        val selectedId = state.selectedBlockId.value
        activeLinkTarget = if (draggedBlockId == null && selectedId != null && state.isSelectionCollapsed.value) {
            state.resolveLinkTarget(selectedId)
        } else {
            activeLinkTarget?.takeIf { it.blockId == selectedId }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuataPortableRichTextToolbar(
                state = state,
                onOpenHeadingDialog = { showHeadingDialog = true },
                onOpenLinkDialog = {
                    linkTarget = state.selectedBlockId.value?.let { state.resolveLinkTarget(it) }
                    showLinkDialog = true
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val selectedCount = state.selectedBlockIds.size
            if (selectedCount > 1) {
                QuataPortableSelectionHeader(
                    selectedCount = selectedCount,
                    onCancel = state::clearSelection,
                    onDelete = state::removeSelectedBlocks,
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { listBounds = it.boundsInRoot() }
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(
                    items = state.blocks,
                    key = { _, block -> block.id },
                ) { index, block ->
                    val activeSlashSession = state.activeSlashSession?.takeIf { it.blockId == block.id }
                    var slashSelection by remember(block.id) { mutableStateOf(0) }
                    val slashCommands = activeSlashSession?.let { slashRegistry.filter(it.query) }.orEmpty()
                    LaunchedEffect(slashCommands.size) {
                        if (slashSelection > slashCommands.lastIndex) {
                            slashSelection = slashCommands.lastIndex.coerceAtLeast(0)
                        }
                    }
                    QuataPortableRichTextBlockField(
                        state = state,
                        block = block,
                        selected = state.selectedBlockIds.contains(block.id),
                        orderedIndex = if (block.type == RichTextBlockType.Numbered) {
                            portableNumberedIndex(state.blocks, index)
                        } else {
                            null
                        },
                        placeholder = placeholder,
                        onSelected = { useShift, useCtrlOrCmd ->
                            state.selectBlock(
                                blockId = block.id,
                                clearSelection = !(useShift || useCtrlOrCmd),
                                useShift = useShift,
                                useCtrlOrCmd = useCtrlOrCmd,
                            )
                        },
                        onTodoCheckedChange = { state.toggleTodoChecked(block.id) },
                        onValueChange = { value -> state.updateBlockText(block.id, value) },
                        onMoveUp = { state.moveBlockUp(block.id) },
                        onMoveDown = { state.moveBlockDown(block.id) },
                        onDuplicate = { state.duplicateSelectedBlocks(block.id) },
                        onDelete = { state.removeBlock(block.id) },
                        onOutdent = { state.toggleIndent(block.id, -1) },
                        onIndent = { state.toggleIndent(block.id, 1) },
                        onPositioned = { bounds -> blockBounds[block.id] = bounds },
                        onDragStart = { pointerY ->
                            val pointerYInRoot = (blockBounds[block.id]?.top ?: 0f) + pointerY
                            if (!state.isBlockSelected(block.id)) {
                                state.selectBlock(block.id)
                            }
                            if (state.startDragSession(block.id)) {
                                draggedBlockId = block.id
                                dragPointerYInRoot = pointerYInRoot
                                dragAccumulatorX.floatValue = 0f
                                val targetIndex = resolveTargetIndex(pointerYInRoot)
                                state.updateDragSession(
                                    targetIndex = targetIndex,
                                    futureRootIndent = resolveFutureIndent(targetIndex),
                                    horizontalDragDeltaPx = dragAccumulatorX.floatValue,
                                    indentUnitPx = indentUnitPx,
                                    anchorBlockId = block.id,
                                )
                            }
                        },
                        onDrag = { pointerY, dragDeltaX ->
                            val activeDragId = draggedBlockId ?: block.id
                            val pointerYInRoot = (blockBounds[activeDragId]?.top ?: 0f) + pointerY
                            dragAccumulatorX.floatValue += dragDeltaX
                            dragPointerYInRoot = pointerYInRoot
                            val targetIndex = resolveTargetIndex(pointerYInRoot)
                            state.updateDragSession(
                                targetIndex = targetIndex,
                                futureRootIndent = resolveFutureIndent(targetIndex),
                                horizontalDragDeltaPx = dragAccumulatorX.floatValue,
                                indentUnitPx = indentUnitPx,
                                anchorBlockId = activeDragId,
                            )
                        },
                        onDragEnd = {
                            state.completeDragSession()
                            draggedBlockId = null
                            dragPointerYInRoot = null
                            dragAccumulatorX.floatValue = 0f
                        },
                        onDragCancel = {
                            state.cancelDragSession()
                            draggedBlockId = null
                            dragPointerYInRoot = null
                            dragAccumulatorX.floatValue = 0f
                        },
                        onSlashSelectionChange = { slashSelection = it },
                        slashSelection = slashSelection,
                        slashCommands = slashCommands,
                        onSlashCommand = { command ->
                            if (!slashExecutor.execute(block.id, command, fromSlashSession = activeSlashSession != null)) {
                                state.clearSlashCommandSession()
                            }
                        },
                    )
                    val rowLinkTarget = if (draggedBlockId == null && state.selectedBlockIds.contains(block.id) && block.type != RichTextBlockType.Code) {
                        state.resolveLinkTarget(block.id)
                    } else {
                        null
                    }
                    if (rowLinkTarget != null && rowLinkTarget == activeLinkTarget) {
                        QuataPortableLinkPopup(
                            target = rowLinkTarget,
                            onOpen = {
                                if (onOpenLink != null) {
                                    onOpenLink(rowLinkTarget.url)
                                } else {
                                    runCatching { uriHandler.openUri(rowLinkTarget.url) }
                                }
                            },
                            onEdit = {
                                linkTarget = rowLinkTarget
                                showLinkDialog = true
                            },
                            onRemove = {
                                state.removeLinkForTarget(rowLinkTarget)
                                activeLinkTarget = null
                            },
                            modifier = Modifier.padding(start = 44.dp, end = 12.dp, bottom = 4.dp),
                        )
                    }
                    if (activeSlashSession != null) {
                        QuataPortableSlashCommandMenu(
                            commands = slashCommands,
                            selectedIndex = slashSelection,
                            onCommand = { command ->
                                if (!slashExecutor.execute(block.id, command, fromSlashSession = true)) {
                                    state.clearSlashCommandSession()
                                }
                            },
                        )
                    }
                }
                item {
                    Text(
                        text = "+ Bloque",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { state.addBlock(state.selectedBlockId.value) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }

    if (showHeadingDialog) {
        QuataRichTextHeadingDialogContent(
            current = state.selectedHeadingLevel.value,
            title = "Titulos",
            normalTextLabel = { "Texto normal" },
            closeLabel = "Cerrar",
            onSelect = { level ->
                if (level == 0) {
                    state.selectedBlockId.value?.let { state.setBlockType(it, RichTextBlockType.Paragraph) }
                } else {
                    state.toggleHeading(level)
                }
                showHeadingDialog = false
            },
            onDismiss = { showHeadingDialog = false },
        )
    }

    if (showLinkDialog) {
        val existingTarget = linkTarget
        QuataRichTextLinkDialogContent(
            initialUrl = existingTarget?.url.orEmpty(),
            title = "Enlace",
            placeholder = "https://",
            confirmLabel = "Aplicar",
            dismissLabel = "Cancelar",
            onConfirm = { value ->
                if (existingTarget != null) {
                    state.setLinkForTarget(existingTarget, value)
                    activeLinkTarget = state.resolveLinkTarget(existingTarget.blockId)
                } else {
                    state.setLinkForSelection(value)
                }
                linkTarget = null
                showLinkDialog = false
            },
            onDismiss = {
                linkTarget = null
                showLinkDialog = false
            },
        )
    }
}

@Composable
private fun QuataPortableLinkPopup(
    target: QuataLinkTarget,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = target.url,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
            TextButton(onClick = onOpen) {
                Text("Abrir")
            }
            TextButton(onClick = onEdit) {
                Text("Editar")
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Quitar enlace",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun QuataPortableRichTextToolbar(
    state: QuataRichTextEditorState,
    onOpenHeadingDialog: () -> Unit,
    onOpenLinkDialog: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        QuataPortableToolbarButton(Icons.AutoMirrored.Filled.Undo, state.canUndo, false, state::undo, "Undo")
        QuataPortableToolbarButton(Icons.AutoMirrored.Filled.Redo, state.canRedo, false, state::redo, "Redo")
        Spacer(Modifier.width(6.dp))
        QuataPortableToolbarButton(Icons.Filled.FormatBold, true, state.isBold.value, state::toggleBold, "Bold")
        QuataPortableToolbarButton(Icons.Filled.FormatItalic, true, state.isItalic.value, state::toggleItalic, "Italic")
        QuataPortableToolbarButton(Icons.Filled.FormatUnderlined, true, state.isUnderline.value, state::toggleUnderline, "Underline")
        QuataPortableToolbarButton(Icons.Filled.FormatStrikethrough, true, state.isStrikethrough.value, state::toggleStrikethrough, "Strikethrough")
        QuataPortableToolbarButton(Icons.Filled.Code, true, state.isInlineCode.value, state::toggleInlineCode, "Inline code")
        QuataPortableToolbarButton(Icons.Filled.Highlight, true, state.isHighlight.value, state::toggleHighlight, "Highlight")
        QuataPortableToolbarButton(Icons.Filled.Link, !state.isSelectionCollapsed.value || state.isLinked.value, state.isLinked.value, onOpenLinkDialog, "Link")
        Spacer(Modifier.width(6.dp))
        QuataPortableToolbarTextButton(
            label = if (state.selectedHeadingLevel.value == 0) "H" else "H${state.selectedHeadingLevel.value}",
            enabled = true,
            selected = state.isHeading.value,
            onClick = onOpenHeadingDialog,
            contentDescription = "Heading",
        )
        QuataPortableToolbarButton(Icons.AutoMirrored.Filled.FormatListBulleted, true, state.isBulletedList.value, { state.toggleList("bullet") }, "Bullet list")
        QuataPortableToolbarButton(Icons.Filled.FormatListNumbered, true, state.isNumberedList.value, { state.toggleList("ordered") }, "Numbered list")
        QuataPortableToolbarButton(Icons.Filled.MoreVert, true, state.isTodo.value, { state.toggleList("todo") }, "Todo")
        QuataPortableToolbarButton(Icons.Filled.FormatQuote, true, state.isQuote.value, state::setQuote, "Quote")
        QuataPortableToolbarButton(Icons.Filled.Info, true, state.isInfo.value, state::setInfo, "Info")
        QuataPortableToolbarButton(Icons.Filled.Code, true, state.isCode.value, state::setCode, "Code block")
        QuataPortableToolbarButton(Icons.Filled.Title, true, state.isDivider.value, state::setDivider, "Divider")
        Spacer(Modifier.width(6.dp))
        QuataPortableToolbarButton(Icons.Filled.KeyboardArrowUp, true, false, state::movePrimaryBlockUp, "Move block up")
        QuataPortableToolbarButton(Icons.Filled.KeyboardArrowDown, true, false, state::movePrimaryBlockDown, "Move block down")
        QuataPortableToolbarButton(Icons.Filled.ContentCopy, true, false, { state.duplicateSelectedBlocks() }, "Duplicate block")
        QuataPortableToolbarButton(Icons.Filled.Delete, true, false, state::removeSelectedBlocks, "Delete block")
    }
}

@Composable
private fun QuataPortableToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(36.dp)
            .background(
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
                shape = MaterialTheme.shapes.small,
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
                selected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
            },
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun QuataPortableToolbarTextButton(
    label: String,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(36.dp)
            .background(
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
                shape = MaterialTheme.shapes.small,
            )
            .testTag("portable-rich-text-toolbar-$contentDescription"),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
                selected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
            },
        )
    }
}

@Composable
private fun QuataPortableSlashCommandMenu(
    commands: List<RichTextBlockCommand>,
    selectedIndex: Int,
    onCommand: (RichTextBlockCommand) -> Unit,
) {
    if (commands.isEmpty()) return
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 36.dp, end = 8.dp)
            .testTag("portable-rich-text-slash-menu"),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            commands.forEachIndexed { index, command ->
                Text(
                    text = command.label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (index == selectedIndex) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f) else Color.Transparent,
                            MaterialTheme.shapes.small,
                        )
                        .clickable { onCommand(command) }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
private fun QuataPortableSelectionHeader(
    selectedCount: Int,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Cancelar",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onCancel)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
        Text(
            text = "$selectedCount seleccionados",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete selected blocks",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun QuataPortableRichTextBlockField(
    state: QuataRichTextEditorState,
    block: QuataRichTextBlock,
    selected: Boolean,
    orderedIndex: Int?,
    placeholder: String,
    onSelected: (useShift: Boolean, useCtrlOrCmd: Boolean) -> Unit,
    onTodoCheckedChange: () -> Unit,
    onValueChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onOutdent: () -> Unit,
    onIndent: () -> Unit,
    onPositioned: (Rect) -> Unit,
    onDragStart: (pointerY: Float) -> Unit,
    onDrag: (pointerY: Float, dragDeltaX: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onSlashSelectionChange: (Int) -> Unit,
    slashSelection: Int,
    slashCommands: List<RichTextBlockCommand>,
    onSlashCommand: (RichTextBlockCommand) -> Unit,
) {
    val textStyle = portableStyleForBlock(block.type)
    val visualTransformation = rememberPortableRichTextVisualTransformation(block.spans)
    val focusRequester = remember(block.id) { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val interactionSource = remember(block.id) { MutableInteractionSource() }
    LaunchedEffect(state.selectedBlockId.value) {
        if (state.selectedBlockId.value == block.id && block.type != RichTextBlockType.Divider) {
            focusRequester.requestFocus()
        }
    }
    if (block.type == RichTextBlockType.Divider) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { onPositioned(it.boundsInRoot()) }
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
                    MaterialTheme.shapes.small,
                )
                .testTag(QuataPortableRichTextFieldFocusTargetTestTag)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) { onSelected(state.isShiftPressed.value, state.isCtrlPressed.value) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuataPortableBlockRail(
                selected = selected,
                canOutdent = false,
                onSelect = { onSelected(false, false) },
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onDuplicate = onDuplicate,
                onDelete = onDelete,
                onOutdent = onOutdent,
                onIndent = onIndent,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PortableIndentUnit * block.indentLevel)
            .onGloballyPositioned { onPositioned(it.boundsInRoot()) }
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else portableBackgroundForBlock(block.type),
                MaterialTheme.shapes.small,
            )
            .testTag(QuataPortableRichTextFieldFocusTargetTestTag)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                onSelected(state.isShiftPressed.value, state.isCtrlPressed.value)
                focusRequester.requestFocus()
                keyboardController?.show()
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        QuataPortableBlockRail(
            selected = selected,
            canOutdent = block.indentLevel > 0,
            onSelect = { onSelected(false, false) },
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onDuplicate = onDuplicate,
            onDelete = onDelete,
            onOutdent = onOutdent,
            onIndent = onIndent,
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            onDragCancel = onDragCancel,
        )
        when (block.type) {
            RichTextBlockType.Todo -> Checkbox(
                checked = block.isChecked,
                onCheckedChange = { onTodoCheckedChange() },
                modifier = Modifier.size(28.dp),
            )

            RichTextBlockType.Numbered -> Text(
                text = "${orderedIndex ?: 1}.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(28.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )

            RichTextBlockType.Bullet -> Text(
                text = "\u2022",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(28.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )

            else -> Unit
        }
        BasicTextField(
            value = block.text,
            onValueChange = onValueChange,
            textStyle = textStyle.copy(color = textStyle.color.takeOrElse { MaterialTheme.colorScheme.onSurface }),
            visualTransformation = visualTransformation,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .testTag(QuataPortableRichTextFieldTestTag)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    if (it.isFocused && state.selectedBlockId.value != block.id) {
                        onSelected(false, false)
                    }
                }
                .onKeyEvent { event ->
                    val hasCtrlOrCmd = event.isCtrlPressed || event.isMetaPressed
                    state.setModifierKeys(
                        shiftPressed = event.isShiftPressed,
                        ctrlPressed = hasCtrlOrCmd,
                    )
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.A -> {
                            if (hasCtrlOrCmd) {
                                state.selectAllBlocks()
                                true
                            } else {
                                false
                            }
                        }
                        Key.D -> {
                            if (hasCtrlOrCmd) {
                                state.duplicateSelectedBlocks(block.id)
                                true
                            } else {
                                false
                            }
                        }
                        Key.Z -> {
                            if (hasCtrlOrCmd) {
                                if (event.isShiftPressed) state.redo() else state.undo()
                                true
                            } else {
                                false
                            }
                        }
                        Key.Y -> {
                            if (hasCtrlOrCmd) {
                                state.redo()
                                true
                            } else {
                                false
                            }
                        }
                        Key.DirectionUp -> {
                            if (slashCommands.isNotEmpty() && !event.isShiftPressed && !hasCtrlOrCmd) {
                                onSlashSelectionChange((slashSelection - 1).coerceAtLeast(0))
                                true
                            } else if (event.isShiftPressed) {
                                state.selectAdjacentBlock(-1, useShift = true)
                                true
                            } else if (hasCtrlOrCmd) {
                                state.movePrimaryBlockUp()
                                true
                            } else {
                                false
                            }
                        }
                        Key.DirectionDown -> {
                            if (slashCommands.isNotEmpty() && !event.isShiftPressed && !hasCtrlOrCmd) {
                                onSlashSelectionChange((slashSelection + 1).coerceAtMost(slashCommands.lastIndex))
                                true
                            } else if (event.isShiftPressed) {
                                state.selectAdjacentBlock(1, useShift = true)
                                true
                            } else if (hasCtrlOrCmd) {
                                state.movePrimaryBlockDown()
                                true
                            } else {
                                false
                            }
                        }
                        Key.Tab -> {
                            if (event.isShiftPressed) onOutdent() else onIndent()
                            true
                        }
                        Key.Enter -> {
                            if (slashCommands.isNotEmpty()) {
                                val command = slashCommands.getOrNull(slashSelection) ?: slashCommands.first()
                                onSlashCommand(command)
                                true
                            } else if (state.handleListOrTodoEnter(block.id)) {
                                true
                            } else {
                                state.splitBlockAtSelection(block.id)
                            }
                        }
                        Key.Backspace -> state.handleListOrTodoBackspaceAtStart(block.id)
                        Key.Escape -> {
                            state.clearSlashCommandSession()
                            true
                        }
                        else -> false
                    }
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) {
                    onSelected(state.isShiftPressed.value, state.isCtrlPressed.value)
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
                .padding(start = if (block.type == RichTextBlockType.Paragraph) 0.dp else 4.dp),
            decorationBox = { inner ->
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                    if (block.text.text.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun QuataPortableBlockRail(
    selected: Boolean,
    canOutdent: Boolean,
    onSelect: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onOutdent: () -> Unit,
    onIndent: () -> Unit,
    onDragStart: (pointerY: Float) -> Unit,
    onDrag: (pointerY: Float, dragDeltaX: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(32.dp)
            .padding(end = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        QuataPortableRailIconButton(
            icon = Icons.Filled.DragIndicator,
            selected = selected,
            onClick = onSelect,
            contentDescription = "Select block",
            modifier = Modifier.pointerInput(Unit) {
                var pointerY = 0f
                detectDragGestures(
                    onDragStart = { offset ->
                        pointerY = offset.y
                        onDragStart(pointerY)
                    },
                    onDrag = { change, dragAmount ->
                        pointerY += dragAmount.y
                        onDrag(pointerY, dragAmount.x)
                        change.consume()
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                )
            },
        )
        QuataPortableRailIconButton(Icons.Filled.KeyboardArrowUp, false, onMoveUp, "Move block up")
        QuataPortableRailIconButton(Icons.Filled.KeyboardArrowDown, false, onMoveDown, "Move block down")
        QuataPortableRailButton("⇥", false, onIndent, "Indent block")
        QuataPortableRailButton("⇤", false, onOutdent, "Outdent block", enabled = canOutdent)
        QuataPortableRailIconButton(Icons.Filled.ContentCopy, false, onDuplicate, "Duplicate block")
        QuataPortableRailIconButton(Icons.Filled.Delete, false, onDelete, "Delete block")
    }
}

@Composable
private fun QuataPortableRailIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(26.dp)
            .background(
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
                shape = MaterialTheme.shapes.small,
            )
            .clickable(enabled = enabled, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
                selected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
            },
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun QuataPortableRailButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .background(
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
                shape = MaterialTheme.shapes.small,
            )
            .clickable(enabled = enabled, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
                selected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
            },
        )
    }
}

@Composable
private fun portableStyleForBlock(type: RichTextBlockType): TextStyle = when (type) {
    RichTextBlockType.Heading1 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
    RichTextBlockType.Heading2 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    RichTextBlockType.Heading3 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    RichTextBlockType.Heading4 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
    RichTextBlockType.Heading5 -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    RichTextBlockType.Heading6 -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
    RichTextBlockType.Quote -> MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic)
    RichTextBlockType.Info -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
    RichTextBlockType.Code -> MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    else -> MaterialTheme.typography.bodyMedium
}

@Composable
private fun portableBackgroundForBlock(type: RichTextBlockType): Color = when (type) {
    RichTextBlockType.Code -> MaterialTheme.colorScheme.surfaceVariant
    RichTextBlockType.Quote -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f)
    RichTextBlockType.Info -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.24f)
    else -> Color.Transparent
}

@Composable
private fun rememberPortableRichTextVisualTransformation(spans: List<QuataTextSpan>): VisualTransformation {
    val primary = MaterialTheme.colorScheme.primary
    val highlight = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f)
    val inlineCodeBackground = MaterialTheme.colorScheme.surfaceVariant
    return remember(spans, primary, highlight, inlineCodeBackground) {
        VisualTransformation { text ->
            val builder = AnnotatedString.Builder(text.text)
            for (span in QuataSpanAlgorithms.normalize(spans, text.text.length)) {
                builder.addStyle(
                    span.style.portableSpanStyle(primary, highlight, inlineCodeBackground),
                    span.start,
                    span.end,
                )
            }
            TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
        }
    }
}

private fun QuataSpanStyle.portableSpanStyle(
    linkColor: Color,
    highlightColor: Color,
    inlineCodeBackground: Color,
): SpanStyle = when (this) {
    QuataSpanStyle.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
    QuataSpanStyle.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
    QuataSpanStyle.Underline -> SpanStyle(textDecoration = TextDecoration.Underline)
    QuataSpanStyle.Strike -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    QuataSpanStyle.Highlight -> SpanStyle(background = highlightColor)
    QuataSpanStyle.InlineCode -> SpanStyle(fontFamily = FontFamily.Monospace, background = inlineCodeBackground)
    is QuataSpanStyle.Link -> SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
}

private fun portableNumberedIndex(blocks: List<QuataRichTextBlock>, targetIndex: Int): Int {
    if (targetIndex !in blocks.indices || blocks[targetIndex].type != RichTextBlockType.Numbered) return 1
    var index = 1
    for (cursor in targetIndex - 1 downTo 0) {
        if (blocks[cursor].type == RichTextBlockType.Numbered) {
            index++
        } else {
            break
        }
    }
    return index
}
