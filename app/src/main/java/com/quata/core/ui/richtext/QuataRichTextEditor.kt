package com.quata.core.ui.richtext

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.Key.Companion.Backspace
import androidx.compose.ui.input.key.Key.Companion.DirectionDown
import androidx.compose.ui.input.key.Key.Companion.DirectionUp
import androidx.compose.ui.input.key.Key.Companion.Escape
import androidx.compose.ui.input.key.Key.Companion.Enter
import androidx.compose.ui.input.key.Key.Companion.Tab
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.quata.R
import com.quata.core.localization.QuataLanguageManager
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

private val DRAG_AUTO_SCROLL_HOT_ZONE = 80.dp
private const val DRAG_AUTO_SCROLL_FRAME_MS = 16L
private const val DRAG_AUTO_SCROLL_SPEED_VIEWPORTS_PER_SECOND = 1.5f
private const val DRAG_PREVIEW_ALPHA = 0.72f
private const val DRAG_PREVIEW_SHADOW_ELEVATION = 6f
private val DRAG_INDENT_UNIT = 24.dp
private val DRAG_INDICATOR_BASE_X = 8.dp
private val DRAG_BADGE_PADDING_HORIZONTAL = 6.dp
private val DRAG_BADGE_PADDING_VERTICAL = 2.dp
private val DRAG_ANIMATION_DURATION_MS = 150
private val SWIPE_DELETE_THRESHOLD = 96.dp

@Composable
private fun QuataLinkPopup(
    target: QuataLinkTarget,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
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
                color = MaterialTheme.colorScheme.primary,
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

private fun calculateDragAutoScrollAmount(
    dragY: Float,
    viewportHeight: Float,
    hotZonePx: Float,
    maxSpeedPxPerFrame: Float,
): Float {
    if (viewportHeight <= 0f || hotZonePx <= 0f) return 0f

    if (dragY < hotZonePx) {
        val depth = ((hotZonePx - dragY) / hotZonePx).coerceIn(0f, 1f)
        return -maxSpeedPxPerFrame * depth
    }

    val bottomBoundary = viewportHeight - hotZonePx
    if (dragY > bottomBoundary) {
        val depth = ((dragY - bottomBoundary) / hotZonePx).coerceIn(0f, 1f)
        return maxSpeedPxPerFrame * depth
    }

    return 0f
}

private fun calculateDragIndicatorOffset(
    layoutInfo: androidx.compose.foundation.lazy.LazyListLayoutInfo,
    targetIndex: Int,
    totalCount: Int,
): Float? {
    if (totalCount < 0 || targetIndex < 0) return null
    if (layoutInfo.visibleItemsInfo.isEmpty()) return null

    val visibleItems = layoutInfo.visibleItemsInfo
    val firstItem = visibleItems.firstOrNull() ?: return null
    val lastItem = visibleItems.lastOrNull() ?: return null

    if (targetIndex <= firstItem.index) {
        return firstItem.offset.toFloat()
    }

    if (targetIndex > lastItem.index) {
        return (lastItem.offset + lastItem.size).toFloat()
    }

    val itemAtGap = visibleItems.firstOrNull { it.index == targetIndex }
    if (itemAtGap != null) return itemAtGap.offset.toFloat()

    val firstAfterGap = visibleItems.firstOrNull { it.index > targetIndex }
    return firstAfterGap?.offset?.toFloat()
}

private fun calculateDragIndicatorGeometry(
    viewportWidthPx: Float,
    futureRootIndent: Float,
    blockHorizontalPaddingPx: Float,
    indentUnitPx: Float,
): Pair<Float, Float>? {
    if (viewportWidthPx <= 0f || blockHorizontalPaddingPx < 0f || indentUnitPx < 0f) {
        return null
    }

    val startX = blockHorizontalPaddingPx + indentUnitPx * futureRootIndent.coerceAtLeast(0f)
    val endX = viewportWidthPx - blockHorizontalPaddingPx
    if (startX >= endX) return null
    return Pair(startX, endX)
}

@Composable
internal fun rememberQuataRichTextEditorState(initialHtml: String = ""): QuataRichTextEditorState {
    return androidx.compose.runtime.remember { QuataRichTextEditorState(initialHtml) }
}

@Composable
fun QuataRichTextEditorBox(
    modifier: Modifier = Modifier,
    initialHtml: String = "",
    placeholder: String = "Escribe aqui",
    onHtmlChange: (String) -> Unit = {},
    onOpenLink: ((String) -> Unit)? = null,
) {
    val state = rememberQuataRichTextEditorState(initialHtml)
    QuataRichTextEditor(
        state = state,
        modifier = modifier,
        placeholder = placeholder,
        onHtmlChange = onHtmlChange,
        onOpenLink = onOpenLink,
    )
}

@Composable
private fun rememberLocalizedSlashCommandRegistry(): QuataSlashCommandRegistry {
    val context = LocalContext.current
    val languageTag = rememberEditorLanguageTag()
    val localizedContext = remember(context, languageTag) {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(languageTag))
        context.createConfigurationContext(config)
    }
    fun blockLabel(resourceId: Int, spanish: String, french: String): String {
        return when (languageTag) {
            "es" -> localizedContext.getString(resourceId).takeUnless { it == context.getString(resourceId) } ?: spanish
            "fr" -> localizedContext.getString(resourceId).takeUnless { it == context.getString(resourceId) } ?: french
            else -> localizedContext.getString(resourceId)
        }
    }

    val commands = listOf(
        RichTextBlockCommand(blockLabel(R.string.rich_text_block_paragraph, "P\u00e1rrafo", "Paragraphe"), RichTextBlockType.Paragraph, listOf("p", "paragraph", "texto", "parrafo")),
        RichTextBlockCommand(blockLabel(R.string.rich_text_block_heading_1, "T\u00edtulo 1", "Titre 1"), RichTextBlockType.Heading1, listOf("h1", "heading 1", "title 1", "titulo 1")),
        RichTextBlockCommand(blockLabel(R.string.rich_text_block_heading_2, "T\u00edtulo 2", "Titre 2"), RichTextBlockType.Heading2, listOf("h2", "heading 2", "title 2", "titulo 2")),
        RichTextBlockCommand(blockLabel(R.string.rich_text_block_heading_3, "T\u00edtulo 3", "Titre 3"), RichTextBlockType.Heading3, listOf("h3", "heading 3", "title 3", "titulo 3")),
        RichTextBlockCommand(blockLabel(R.string.rich_text_block_heading_4, "T\u00edtulo 4", "Titre 4"), RichTextBlockType.Heading4, listOf("h4", "heading 4", "title 4", "titulo 4")),
        RichTextBlockCommand(blockLabel(R.string.rich_text_block_heading_5, "T\u00edtulo 5", "Titre 5"), RichTextBlockType.Heading5, listOf("h5", "heading 5", "title 5", "titulo 5")),
        RichTextBlockCommand(blockLabel(R.string.rich_text_block_heading_6, "T\u00edtulo 6", "Titre 6"), RichTextBlockType.Heading6, listOf("h6", "heading 6", "title 6", "titulo 6")),
        RichTextBlockCommand(blockLabel(R.string.rich_text_block_bullet_list, "Lista", "Liste"), RichTextBlockType.Bullet, listOf("bullet", "list", "lista")),
        RichTextBlockCommand(blockLabel(R.string.rich_text_block_numbered_list, "Lista numerada", "Liste numerotee"), RichTextBlockType.Numbered, listOf("numbered", "ordered", "num", "numerada")),
        RichTextBlockCommand(blockLabel(R.string.rich_text_block_todo, "Tarea", "Tache"), RichTextBlockType.Todo, listOf("todo", "task", "check", "tarea")),
        RichTextBlockCommand(blockLabel(R.string.rich_text_block_quote, "Cita", "Citation"), RichTextBlockType.Quote, listOf("quote", "cita")),
        RichTextBlockCommand(blockLabel(R.string.rich_text_block_info, "Info", "Info"), RichTextBlockType.Info, listOf("info", "informacion")),
        RichTextBlockCommand(blockLabel(R.string.rich_text_block_code, "Bloque de c\u00f3digo", "Bloc de code"), RichTextBlockType.Code, listOf("code", "codigo")),
        RichTextBlockCommand(blockLabel(R.string.rich_text_block_divider, "Separador", "Separateur"), RichTextBlockType.Divider, listOf("divider", "separator", "hr", "separador")),
    )
    return remember(commands) { QuataSlashCommandRegistry(commands) }
}

@Composable
private fun rememberEditorLanguageTag(): String {
    val systemLanguage = remember {
        LocaleList.getDefault().get(0)?.language ?: Locale.getDefault().language
    }
    val appLanguage = QuataLanguageManager.currentLanguage.tag
    return when {
        appLanguage == "fr" || systemLanguage == "fr" -> "fr"
        appLanguage == "es" || systemLanguage == "es" -> "es"
        else -> "en"
    }
}

@Composable
internal fun QuataRichTextEditor(
    state: QuataRichTextEditorState,
    modifier: Modifier = Modifier,
    placeholder: String = "Escribe aqui",
    onHtmlChange: (String) -> Unit = {},
    onOpenLink: ((String) -> Unit)? = null,
) {
    var linkDraft by rememberSaveable { mutableStateOf("") }
    var showLinkDialog by remember { mutableStateOf(false) }
    var activeLinkTarget by remember { mutableStateOf<QuataLinkTarget?>(null) }
    var showGlobalTypeMenu by remember { mutableStateOf(false) }
    var showGlobalHeadingMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val blockBounds = remember { mutableStateMapOf<String, Rect>() }
    val listBounds = remember { mutableStateOf(Rect.Zero) }
    val draggedBlockId = remember { mutableStateOf<String?>(null) }
    val dragPointerYInRoot = remember { mutableFloatStateOf(0f) }
    val dragStartOffsetY = remember { mutableFloatStateOf(0f) }
    val dragPointerXInRoot = remember { mutableFloatStateOf(0f) }
    val dragStartOffsetX = remember { mutableFloatStateOf(0f) }
    val dragAccumulatorY = remember { mutableFloatStateOf(0f) }
    val dragAccumulatorX = remember { mutableFloatStateOf(0f) }
    val dragTargetIndex = remember { mutableIntStateOf(-1) }
    val dragVisualTargetIndex = remember { mutableIntStateOf(-1) }
    val dragFutureIndentLevel = remember { mutableIntStateOf(-1) }
    val dragVisualFutureIndentLevel = remember { mutableIntStateOf(-1) }
    val dragScrollScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dragEdgePx = with(density) { DRAG_AUTO_SCROLL_HOT_ZONE.toPx() }
    val dragIndentUnitPx = with(density) { DRAG_INDENT_UNIT.toPx() }
    val isDragging = draggedBlockId.value != null
    val dragPreviewBlock = state.getDragPreviewBlock(draggedBlockId.value ?: "")
    val dragPreviewPayloadSize = if (dragPreviewBlock != null) state.getDragPayloadSize() else 0
    val uriHandler = LocalUriHandler.current
    val slashRegistry = rememberLocalizedSlashCommandRegistry()
    val slashExecutor = remember(state) { QuataSlashCommandExecutor(state) }

    fun resolveTargetIndex(pointerYInRoot: Float): Int {
        if (state.blocks.isEmpty()) return 0
        val viewportBounds = listBounds.value
        val yInViewport = if (viewportBounds == Rect.Zero) {
            pointerYInRoot
        } else {
            pointerYInRoot - viewportBounds.top
        }
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return state.blocks.size

        val firstItem = visibleItems.firstOrNull() ?: return state.blocks.size
        val lastItem = visibleItems.lastOrNull() ?: return state.blocks.size

        if (yInViewport < firstItem.offset) return firstItem.index
        if (yInViewport > lastItem.offset + lastItem.size) {
            return (lastItem.index + 1).coerceAtMost(state.blocks.size)
        }

        for (item in visibleItems) {
            val itemMidpoint = item.offset + item.size / 2
            if (yInViewport < itemMidpoint) return item.index
        }
        return (lastItem.index + 1).coerceAtMost(state.blocks.size)
    }

    fun resolveFutureIndent(targetIndex: Int): Int? {
        val anchorId = draggedBlockId.value
        return state.resolveDragFutureRootIndentation(
            targetIndex = targetIndex,
            horizontalDragDeltaPx = dragAccumulatorX.floatValue,
            indentUnitPx = dragIndentUnitPx,
            anchorBlockId = anchorId,
        )
    }

    LaunchedEffect(isDragging) {
        if (!isDragging) return@LaunchedEffect

        val hotZonePx = dragEdgePx
        while (isActive) {
            delay(DRAG_AUTO_SCROLL_FRAME_MS)
            val bounds = listBounds.value
            if (bounds == Rect.Zero) continue

            val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
            if (viewportHeight <= 0f) continue

            val dragYInViewport = dragPointerYInRoot.floatValue - bounds.top
            val maxSpeedPxPerFrame =
                viewportHeight * DRAG_AUTO_SCROLL_SPEED_VIEWPORTS_PER_SECOND * DRAG_AUTO_SCROLL_FRAME_MS / 1000f
            val scrollAmount = calculateDragAutoScrollAmount(
                dragY = dragYInViewport,
                viewportHeight = viewportHeight,
                hotZonePx = hotZonePx,
                maxSpeedPxPerFrame = maxSpeedPxPerFrame,
            )
            if (scrollAmount != 0f) {
                dragScrollScope.launch {
                    listState.dispatchRawDelta(scrollAmount)
                }

                val targetIndex = resolveTargetIndex(dragPointerYInRoot.floatValue)
                val visualIndent = resolveFutureIndent(targetIndex)
                dragVisualTargetIndex.intValue = targetIndex
                dragVisualFutureIndentLevel.intValue = visualIndent ?: -1
                val moved = state.updateDragSession(
                    targetIndex = targetIndex,
                    futureRootIndent = visualIndent,
                    horizontalDragDeltaPx = dragAccumulatorX.floatValue,
                    indentUnitPx = dragIndentUnitPx,
                    anchorBlockId = draggedBlockId.value,
                )
                if (moved) {
                    dragTargetIndex.intValue = targetIndex
                    dragFutureIndentLevel.intValue = visualIndent ?: -1
                } else {
                    dragTargetIndex.intValue = -1
                    dragFutureIndentLevel.intValue = -1
                    dragVisualTargetIndex.intValue = -1
                    dragVisualFutureIndentLevel.intValue = -1
                }
            }
        }
    }

    LaunchedEffect(state.html) {
        onHtmlChange(state.html)
    }

    LaunchedEffect(state.selectedBlockId.value, state.isSelectionCollapsed.value, state.html, isDragging) {
        if (isDragging) {
            activeLinkTarget = null
            return@LaunchedEffect
        }
        val selectedId = state.selectedBlockId.value
        activeLinkTarget = if (selectedId != null && state.isSelectionCollapsed.value) {
            state.resolveLinkTarget(selectedId)
        } else {
            activeLinkTarget?.takeIf { it.blockId == selectedId }
        }
    }

    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
    ) {
        val multiSelectionCount = state.selectedBlockIds.size.takeIf { it > 1 } ?: 0
        if (multiSelectionCount > 0) {
            QuataRichTextSelectionHeader(
                selectedCount = multiSelectionCount,
                onCancel = state::clearSelection,
                onDelete = state::removeSelectedBlocks,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        listBounds.value = coordinates.boundsInRoot()
                    },
            ) {
                items(
                    count = state.blocks.size,
                    key = { index ->
                        state.blocks[index].id
                    },
                ) { blockIndex ->
                    val block = state.blocks[blockIndex]
                    var rowTypeMenu by remember(block.id) { mutableStateOf(false) }
                    var rowTypeQuery by remember(block.id) { mutableStateOf("") }
                    val activeSlashSession = state.activeSlashSession
                    val hasSlashSession = activeSlashSession?.blockId == block.id
                    val slashCommands = slashRegistry.filter(
                        query = if (hasSlashSession) activeSlashSession.query else rowTypeQuery,
                    )
                    var slashSelection by remember(block.id) { mutableIntStateOf(0) }
                    var rowTypeMenuQuery by remember(block.id) { mutableStateOf(rowTypeQuery) }
                    val selected = state.selectedBlockIds.contains(block.id)
                    val isPrimarySelected = block.id == state.selectedBlockId.value
                    val isMultiSelected = selected && state.selectedBlockIds.size > 1
                    val isDraggedPayloadBlock = isDragging && state.isBlockInDragPayload(block.id)
                    val indentModifier = Modifier
                        .padding(end = 8.dp)
                    val listPrefix = when (block.type) {
                        RichTextBlockType.Bullet -> "•"
                        RichTextBlockType.Numbered -> "${computeNumberedIndex(state.blocks, blockIndex)}."
                        else -> null
                    }
                    val blockBackground = when (block.type) {
                        RichTextBlockType.Quote -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f)
                        RichTextBlockType.Info -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.20f)
                        RichTextBlockType.Code -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surface
                    }
                    val rowTypeLabel = if (block.type == RichTextBlockType.Todo) "Todo" else blockTypeLabel(block.type)
                    var swipeOffsetPx by remember(block.id) { mutableFloatStateOf(0f) }
                    val swipeDeleteThresholdPx = with(density) { SWIPE_DELETE_THRESHOLD.toPx() }
                    val swipeDeleteProgress = (swipeOffsetPx / swipeDeleteThresholdPx).coerceIn(0f, 1f)
                    LaunchedEffect(activeSlashSession?.query, hasSlashSession, rowTypeMenu) {
                        if (hasSlashSession) {
                            rowTypeQuery = activeSlashSession?.query.orEmpty()
                            rowTypeMenuQuery = rowTypeQuery
                            rowTypeMenu = true
                            slashSelection = 0
                        }

                        if (!hasSlashSession && !rowTypeMenu) {
                            slashSelection = 0
                        }
                    }
                    LaunchedEffect(slashCommands) {
                        if (slashCommands.isEmpty()) {
                            slashSelection = 0
                        } else if (slashSelection > slashCommands.lastIndex) {
                            slashSelection = slashCommands.lastIndex
                        }
                    }

                    val rowInteractionSource = remember(block.id) { MutableInteractionSource() }
                    val focusRequester = remember(block.id) { FocusRequester() }
                    val isBlockHovered by rowInteractionSource.collectIsHoveredAsState()
                    val rowBackground by animateColorAsState(
                        targetValue = when {
                            swipeDeleteProgress > 0f -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f + 0.28f * swipeDeleteProgress)
                            isDraggedPayloadBlock -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            isMultiSelected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
                            isBlockHovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            else -> MaterialTheme.colorScheme.surface
                        },
                        label = "richtext-row-background",
                    )
                    val handleTint by animateColorAsState(
                        targetValue = when {
                            isDraggedPayloadBlock -> MaterialTheme.colorScheme.primary
                            isBlockHovered -> MaterialTheme.colorScheme.primary
                            selected -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        },
                        label = "richtext-handle-tint",
                    )
                    val handleScale by animateFloatAsState(
                        targetValue = if (selected || isBlockHovered) 1f else 0.98f,
                        label = "richtext-handle-scale",
                    )
                    LaunchedEffect(isPrimarySelected, block.id) {
                        if (isPrimarySelected && block.type != RichTextBlockType.Divider) {
                            runCatching { focusRequester.requestFocus() }
                            delay(80)
                            runCatching { focusRequester.requestFocus() }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBackground)
                            .hoverable(rowInteractionSource)
                            .graphicsLayer {
                                translationX = swipeOffsetPx
                                alpha = if (isDraggedPayloadBlock) 0.42f else 1f
                            }
                            .onGloballyPositioned { coordinates ->
                                blockBounds[block.id] = coordinates.boundsInRoot()
                            }
                            .pointerInput(block.id) {
                                var horizontalDrag = 0f
                                var verticalDrag = 0f
                                var trackingSwipe = false
                                detectDragGestures(
                                    onDragStart = {
                                        horizontalDrag = 0f
                                        verticalDrag = 0f
                                        trackingSwipe = false
                                    },
                                    onDragCancel = {
                                        swipeOffsetPx = 0f
                                    },
                                    onDragEnd = {
                                        if (trackingSwipe && swipeOffsetPx >= swipeDeleteThresholdPx) {
                                            state.removeBlock(block.id)
                                        }
                                        swipeOffsetPx = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        horizontalDrag += dragAmount.x
                                        verticalDrag += dragAmount.y
                                        if (!trackingSwipe) {
                                            trackingSwipe = horizontalDrag > 18f && horizontalDrag > abs(verticalDrag) * 1.4f
                                        }
                                        if (trackingSwipe) {
                                            swipeOffsetPx = horizontalDrag.coerceAtLeast(0f).coerceAtMost(swipeDeleteThresholdPx * 1.25f)
                                            change.consume()
                                        }
                                    },
                                )
                            }
                            .clickable {
                                state.selectBlock(
                                    blockId = block.id,
                                    clearSelection = !(state.isShiftPressed.value || state.isCtrlPressed.value),
                                    useShift = state.isShiftPressed.value,
                                    useCtrlOrCmd = state.isCtrlPressed.value,
                                )
                            }
                            .padding(top = if (selected) 0.dp else 0.dp),
                    ) {
                        Box(modifier = Modifier.width(34.dp)) {
                            Icon(
                                imageVector = Icons.Default.DragIndicator,
                                contentDescription = "Reordenar bloque o arrastrar a la derecha para eliminar",
                                tint = handleTint,
                                modifier = Modifier
                                    .padding(start = 2.dp, top = 14.dp)
                                    .graphicsLayer {
                                        scaleX = handleScale
                                        scaleY = handleScale
                                        alpha = if (selected || isBlockHovered || isDraggedPayloadBlock) 1f else 0.52f
                                    }
                                    .pointerInput(block.id) {
                                        var deletedByHandleDrag = false
                                        detectDragGestures(
                                            onDragStart = { dragOffset ->
                                                deletedByHandleDrag = false
                                                if (!state.isBlockSelected(block.id)) {
                                                    state.selectBlock(block.id)
                                                }
                                                if (!state.startDragSession(block.id)) {
                                                    return@detectDragGestures
                                                }

                                                draggedBlockId.value = block.id
                                                val bounds = blockBounds[block.id] ?: Rect.Zero
                                                dragPointerXInRoot.floatValue = bounds.left + dragOffset.x
                                                dragPointerYInRoot.floatValue = bounds.top + dragOffset.y
                                                dragStartOffsetX.floatValue = dragOffset.x
                                                dragStartOffsetY.floatValue = dragOffset.y
                                                dragAccumulatorY.floatValue = 0f
                                                dragAccumulatorX.floatValue = 0f
                                                dragTargetIndex.intValue = -1
                                                dragVisualTargetIndex.intValue = -1
                                                dragFutureIndentLevel.intValue = -1
                                                dragVisualFutureIndentLevel.intValue = -1
                                                val firstTarget = resolveTargetIndex(dragPointerYInRoot.floatValue)
                                                val firstFutureIndent = resolveFutureIndent(firstTarget)
                                                dragVisualTargetIndex.intValue = firstTarget
                                                val moved = state.updateDragSession(
                                                    targetIndex = firstTarget,
                                                    futureRootIndent = firstFutureIndent,
                                                    horizontalDragDeltaPx = dragAccumulatorX.floatValue,
                                                    indentUnitPx = dragIndentUnitPx,
                                                    anchorBlockId = block.id,
                                                )
                                                if (moved) {
                                                    dragTargetIndex.intValue = firstTarget
                                                    dragFutureIndentLevel.intValue = firstFutureIndent ?: -1
                                                }
                                            },
                                            onDrag = { change, dragAmount ->
                                                dragAccumulatorX.floatValue += dragAmount.x
                                                dragAccumulatorY.floatValue += dragAmount.y
                                                val activeDragId = draggedBlockId.value ?: block.id
                                                val shouldDeleteFromHandle =
                                                    dragAccumulatorX.floatValue >= swipeDeleteThresholdPx &&
                                                        dragAccumulatorX.floatValue > abs(dragAccumulatorY.floatValue) * 1.4f
                                                if (!deletedByHandleDrag && shouldDeleteFromHandle) {
                                                    deletedByHandleDrag = true
                                                    state.cancelDragSession()
                                                    state.removeBlock(activeDragId)
                                                    draggedBlockId.value = null
                                                    dragPointerYInRoot.floatValue = 0f
                                                    dragPointerXInRoot.floatValue = 0f
                                                    dragAccumulatorY.floatValue = 0f
                                                    dragAccumulatorX.floatValue = 0f
                                                    dragStartOffsetX.floatValue = 0f
                                                    dragStartOffsetY.floatValue = 0f
                                                    dragTargetIndex.intValue = -1
                                                    dragVisualTargetIndex.intValue = -1
                                                    dragFutureIndentLevel.intValue = -1
                                                    dragVisualFutureIndentLevel.intValue = -1
                                                } else if (!deletedByHandleDrag) {
                                                    val bounds = blockBounds[activeDragId]
                                                    if (bounds == null) {
                                                        change.consume()
                                                        return@detectDragGestures
                                                    }
                                                    val pointerX = bounds.left + dragStartOffsetX.floatValue + dragAccumulatorX.floatValue
                                                    val pointerY = bounds.top + dragStartOffsetY.floatValue + dragAccumulatorY.floatValue
                                                    dragPointerXInRoot.floatValue = pointerX
                                                    dragPointerYInRoot.floatValue = pointerY
                                                    val targetIndex = resolveTargetIndex(pointerY)
                                                    dragVisualTargetIndex.intValue = targetIndex
                                                    val futureIndent = resolveFutureIndent(targetIndex)
                                                    dragVisualFutureIndentLevel.intValue = futureIndent ?: -1
                                                    val moved = state.updateDragSession(
                                                        targetIndex = targetIndex,
                                                        futureRootIndent = futureIndent,
                                                        horizontalDragDeltaPx = dragAccumulatorX.floatValue,
                                                        indentUnitPx = dragIndentUnitPx,
                                                        anchorBlockId = activeDragId,
                                                    )
                                                    if (moved) {
                                                        dragTargetIndex.intValue = targetIndex
                                                        dragFutureIndentLevel.intValue = futureIndent ?: -1
                                                    } else {
                                                        dragTargetIndex.intValue = state.currentDragMachineTargetIndex() ?: -1
                                                        dragFutureIndentLevel.intValue = state.currentDragMachineFutureRootIndent() ?: -1
                                                    }
                                                }
                                                change.consume()
                                            },
                                            onDragEnd = {
                                                if (!deletedByHandleDrag) {
                                                    state.completeDragSession()
                                                }
                                                draggedBlockId.value = null
                                                dragPointerYInRoot.floatValue = 0f
                                                dragPointerXInRoot.floatValue = 0f
                                                dragAccumulatorY.floatValue = 0f
                                                dragAccumulatorX.floatValue = 0f
                                                dragStartOffsetX.floatValue = 0f
                                                dragStartOffsetY.floatValue = 0f
                                                dragTargetIndex.intValue = -1
                                                dragVisualTargetIndex.intValue = -1
                                                dragFutureIndentLevel.intValue = -1
                                                dragVisualFutureIndentLevel.intValue = -1
                                            },
                                            onDragCancel = {
                                                state.cancelDragSession()
                                                draggedBlockId.value = null
                                                dragPointerYInRoot.floatValue = 0f
                                                dragPointerXInRoot.floatValue = 0f
                                                dragAccumulatorY.floatValue = 0f
                                                dragAccumulatorX.floatValue = 0f
                                                dragStartOffsetX.floatValue = 0f
                                                dragStartOffsetY.floatValue = 0f
                                                dragTargetIndex.intValue = -1
                                                dragVisualTargetIndex.intValue = -1
                                                dragFutureIndentLevel.intValue = -1
                                                dragVisualFutureIndentLevel.intValue = -1
                                            },
                                        )
                                    },
                            )
                        }

                        if (block.type == RichTextBlockType.Divider) {
                            HorizontalDivider(
                                modifier = indentModifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 12.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        } else {
                            QuataRichTextBlockField(
                                block = block,
                                value = block.text,
                                valueStyle = styleForBlock(block.type),
                                focusRequester = focusRequester,
                                isEditable = isPrimarySelected,
                                placeholder = {
                                    Text(
                                        text = placeholder,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                    )
                                },
                                containerColor = blockBackground,
                                listPrefix = listPrefix,
                                modifier = indentModifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 0.dp, vertical = 4.dp),
                                onValueChange = { newValue ->
                                    val willSplitBlock = block.type != RichTextBlockType.Code && newValue.text.contains('\n')
                                    rowTypeQuery = ""
                                    state.updateBlockText(block.id, newValue)
                                    if (!willSplitBlock) {
                                        state.selectBlock(block.id)
                                    }
                                    if (activeLinkTarget?.blockId == block.id) {
                                        activeLinkTarget = state.resolveLinkTarget(block.id)
                                    }
                                },
                                onTodoCheckedChange = { checked ->
                                    state.setTodoChecked(block.id, checked)
                                    state.selectBlock(block.id)
                                },
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                ),
                                onPreviewKeyEvent = { keyEvent: KeyEvent ->
                                    var handled = false
                                    val hasCtrlOrCmd = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
                                    val useShift = keyEvent.isShiftPressed
                                    state.setModifierKeys(shiftPressed = useShift, ctrlPressed = hasCtrlOrCmd)

                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        when {
                                            hasCtrlOrCmd && keyEvent.key == Key.Z && !useShift -> {
                                                handled = state.undo()
                                            }
                                            hasCtrlOrCmd && keyEvent.key == Key.Z && useShift -> {
                                                handled = state.redo()
                                            }
                                            hasCtrlOrCmd && keyEvent.key == Key.Y -> {
                                                handled = state.redo()
                                            }
                                            hasCtrlOrCmd && keyEvent.key == Key.D -> {
                                                state.duplicateSelectedBlocks()
                                                handled = true
                                            }
                                            hasCtrlOrCmd && keyEvent.key == Key.A -> {
                                                state.selectAllBlocks()
                                                handled = true
                                            }
                                        }

                                        if (!handled && rowTypeMenu) {
                                            when (keyEvent.key) {
                                                Escape -> {
                                                    state.clearSlashCommandSession()
                                                    rowTypeMenu = false
                                                    handled = true
                                                }
                                                DirectionDown -> {
                                                    if (slashCommands.isNotEmpty()) {
                                                        slashSelection = (slashSelection + 1).coerceAtMost(slashCommands.lastIndex)
                                                    }
                                                    handled = true
                                                }
                                                DirectionUp -> {
                                                    if (slashCommands.isNotEmpty()) {
                                                        slashSelection = (slashSelection - 1).coerceAtLeast(0)
                                                    }
                                                    handled = true
                                                }
                                                Tab -> {
                                                    if (keyEvent.isShiftPressed) {
                                                        state.toggleIndent(block.id, -1)
                                                    } else {
                                                        state.toggleIndent(block.id, 1)
                                                    }
                                                    handled = true
                                                }
                                                else -> {}
                                            }
                                        }

                                        if (!handled) {
                                            when (keyEvent.key) {
                                                DirectionUp -> {
                                                    if (useShift) {
                                                        state.selectAdjacentBlock(-1, useShift = true)
                                                        handled = true
                                                    } else if (hasCtrlOrCmd) {
                                                        state.movePrimaryBlockUp()
                                                        handled = true
                                                    }
                                                }
                                                DirectionDown -> {
                                                    if (useShift) {
                                                        state.selectAdjacentBlock(1, useShift = true)
                                                        handled = true
                                                    } else if (hasCtrlOrCmd) {
                                                        state.movePrimaryBlockDown()
                                                        handled = true
                                                    }
                                                }
                                            }
                                        }

                                        if (!handled && !rowTypeMenu) {
                                            when (keyEvent.key) {
                                                Tab -> {
                                                    if (keyEvent.isShiftPressed) {
                                                        state.toggleIndent(block.id, -1)
                                                    } else {
                                                        state.toggleIndent(block.id, 1)
                                                    }
                                                    handled = true
                                                }
                                                Enter -> {
                                                    if ((rowTypeMenu || hasSlashSession) && slashCommands.isNotEmpty()) {
                                                        val target = if (slashSelection in slashCommands.indices) {
                                                            slashCommands[slashSelection]
                                                        } else {
                                                            slashCommands.first()
                                                        }
                                                        slashExecutor.execute(block.id, target, fromSlashSession = hasSlashSession)
                                                        rowTypeMenu = false
                                                        slashSelection = 0
                                                        rowTypeMenuQuery = ""
                                                        handled = true
                                                    } else if (state.handleListOrTodoEnter(block.id)) {
                                                        handled = true
                                                    } else if (hasSlashSession) {
                                                        handled = true
                                                    } else if (state.splitBlockAtSelection(block.id)) {
                                                        handled = true
                                                    }
                                                }
                                                Backspace -> {
                                                    if (block.text.selection.start == 0 &&
                                                        block.text.selection.end == 0 &&
                                                        state.handleListOrTodoBackspaceAtStart(block.id)
                                                    ) {
                                                        if (hasSlashSession) {
                                                            state.clearSlashCommandSession()
                                                        }
                                                        rowTypeMenu = false
                                                        handled = true
                                                    } else if (hasSlashSession) {
                                                        state.clearSlashCommandSession()
                                                        rowTypeMenu = false
                                                        handled = true
                                                    }
                                                }
                                                Escape -> {
                                                    if (hasSlashSession) {
                                                        state.clearSlashCommandSession()
                                                        rowTypeMenu = false
                                                        handled = true
                                                    }
                                                }
                                                else -> {}
                                            }
                                        }
                                    } else if (keyEvent.type == KeyEventType.KeyUp) {
                                        state.setModifierKeys(
                                            shiftPressed = keyEvent.isShiftPressed,
                                            ctrlPressed = hasCtrlOrCmd,
                                        )
                                    }

                                    handled
                                },
                            )
                        }
                    }

                    val linkTarget = if (!isDragging && selected && block.type != RichTextBlockType.Code) {
                        state.resolveLinkTarget(block.id)
                    } else {
                        null
                    }
                    if (linkTarget != null && linkTarget == activeLinkTarget) {
                        QuataLinkPopup(
                            target = linkTarget,
                            onOpen = {
                                if (onOpenLink == null) {
                                    runCatching { uriHandler.openUri(linkTarget.url) }
                                } else {
                                    onOpenLink(linkTarget.url)
                                }
                            },
                            onEdit = {
                                linkDraft = linkTarget.url
                                showLinkDialog = true
                            },
                            onRemove = {
                                state.removeLinkForTarget(linkTarget)
                                activeLinkTarget = null
                            },
                            modifier = Modifier.padding(start = 48.dp, end = 16.dp, bottom = 4.dp),
                        )
                    }

                    DropdownMenu(
                        expanded = rowTypeMenu,
                        onDismissRequest = {
                            rowTypeMenu = false
                            if (hasSlashSession) {
                                state.clearSlashCommandSession()
                            }
                        },
                    ) {
                        BlockTypePicker(
                            query = rowTypeMenuQuery,
                            blockId = block.id,
                            selectedIndex = slashSelection,
                            registry = slashRegistry,
                            onDismiss = { rowTypeMenu = false },
                        ) { targetBlock, type ->
                            val command = slashRegistry.filter(rowTypeMenuQuery)
                                .firstOrNull { it.type == type }
                                ?: RichTextBlockCommand(blockTypeLabel(type), type, emptyList())
                            slashExecutor.execute(targetBlock, command, fromSlashSession = hasSlashSession)
                            rowTypeMenu = false
                            slashSelection = 0
                            rowTypeMenuQuery = ""
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                }
                item {
                    OutlinedButton(
                        onClick = { state.addBlock(state.selectedBlockId.value) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(stringResource(R.string.rich_text_add_block))
                    }
                }
            }

            val indicatorOffset = calculateDragIndicatorOffset(
                layoutInfo = listState.layoutInfo,
                targetIndex = dragVisualTargetIndex.intValue,
                totalCount = state.blocks.size,
            )
            val visualIndent = dragVisualFutureIndentLevel.intValue
            val previewTargetIndent = if (visualIndent >= 0) {
                visualIndent
            } else {
                dragPreviewBlock?.indentLevel ?: 0
            }.coerceAtLeast(0)
            val previewIndentPx = with(density) { DRAG_INDICATOR_BASE_X.toPx() } +
                previewTargetIndent * dragIndentUnitPx
            val previewStartIndentPx = if (isDragging) previewIndentPx else 0f

            if (isDragging && indicatorOffset != null) {
                QuataRichTextDragIndicator(
                    modifier = Modifier.fillMaxSize(),
                    y = indicatorOffset,
                    futureRootIndent = previewTargetIndent,
                    blockHorizontalPaddingPx = with(density) { DRAG_INDICATOR_BASE_X.toPx() },
                    endPaddingPx = with(density) { DRAG_INDICATOR_BASE_X.toPx() },
                )
            }

            if (isDragging && dragPreviewBlock != null && listBounds.value != Rect.Zero) {
                QuataDragPreview(
                    block = dragPreviewBlock,
                    listPrefix = if (dragPreviewBlock.type == RichTextBlockType.Bullet) {
                        "•"
                    } else if (dragPreviewBlock.type == RichTextBlockType.Numbered) {
                        "1."
                    } else {
                        null
                    },
                    valueStyle = styleForBlock(dragPreviewBlock.type),
                    dragPointerYInParent = dragPointerYInRoot.floatValue - listBounds.value.top,
                    dragStartOffsetY = dragStartOffsetY.floatValue,
                    payloadCount = dragPreviewPayloadSize,
                    containerColor = when (dragPreviewBlock.type) {
                        RichTextBlockType.Quote -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f)
                        RichTextBlockType.Info -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.20f)
                        RichTextBlockType.Code -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surface
                    },
                    previewStartOffsetPx = previewStartIndentPx,
                    content = dragPreviewBlock.text.text,
                )
            }
        }

        if (showGlobalTypeMenu && state.selectedBlockId.value != null) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .heightIn(max = 320.dp),
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 4.dp,
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                BlockTypePicker(
                    query = "",
                    blockId = state.selectedBlockId.value.orEmpty(),
                    selectedIndex = 0,
                    registry = slashRegistry,
                    onDismiss = { showGlobalTypeMenu = false },
                    onSelect = { targetBlock, type ->
                        state.setBlockTypeFromSlash(targetBlock, type)
                        showGlobalTypeMenu = false
                    },
                )
            }
        }

        QuataRichTextToolbar(
            state = state,
            onToggleBold = state::toggleBold,
            onToggleItalic = state::toggleItalic,
            onToggleUnderline = state::toggleUnderline,
            onToggleStrike = state::toggleStrikethrough,
            onToggleCode = state::toggleInlineCode,
            onToggleHighlight = state::toggleHighlight,
            onToggleHeading2 = { state.toggleHeading(2) },
            onToggleBullets = { state.toggleList("bullet") },
            onToggleOrdered = { state.toggleList("ordered") },
            onToggleTodo = { state.toggleList("todo") },
            onToggleQuote = state::setQuote,
            onToggleInfo = state::setInfo,
            onToggleCodeBlock = state::setCode,
            onToggleDivider = state::setDivider,
            onDeleteBlock = state::removeSelectedBlocks,
            onUndo = state::undo,
            onRedo = state::redo,
            canUndo = state.canUndo,
            canRedo = state.canRedo,
            onOpenHeadingMenu = { showGlobalHeadingMenu = true },
            currentHeading = state.selectedHeadingLevel.value,
            onToggleLink = {
                if (!state.isSelectionCollapsed.value) {
                    linkDraft = ""
                    showLinkDialog = true
                } else {
                    val target = state.selectedBlockId.value?.let { state.resolveLinkTarget(it) }
                    if (target != null) {
                        activeLinkTarget = target
                        linkDraft = target.url
                    }
                }
            },
            onOpenTypeMenu = { showGlobalTypeMenu = !showGlobalTypeMenu },
            stateHasSelection = state.selectedBlockId.value != null,
        )
    }

    if (showGlobalHeadingMenu && state.selectedBlockId.value != null) {
        QuataRichTextHeadingMenu(
            current = state.selectedHeadingLevel.value,
            onDismiss = { showGlobalHeadingMenu = false },
            onSelect = { level ->
                val targetId = state.selectedBlockId.value!!
                if (level == 0) {
                    state.setBlockType(targetId, RichTextBlockType.Paragraph)
                } else {
                    state.toggleHeading(level)
                }
                showGlobalHeadingMenu = false
            },
        )
    }

    if (showLinkDialog) {
        QuataRichTextLinkDialog(
            initialUrl = linkDraft,
            onConfirm = { value ->
                val target = activeLinkTarget
                if (target != null) {
                    state.setLinkForTarget(target, value)
                    activeLinkTarget = state.resolveLinkTarget(target.blockId)
                } else {
                    state.setLinkForSelection(value)
                }
                showLinkDialog = false
            },
            onDismiss = { showLinkDialog = false },
        )
    }
}

@Composable
private fun QuataRichTextSelectionHeader(
    selectedCount: Int,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel) {
            Text("Cancelar")
        }
        Text(
            text = "$selectedCount seleccionados",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Eliminar seleccionados",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 18.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun QuataRichTextToolbar(
    state: QuataRichTextEditorState,
    onToggleBold: () -> Unit,
    onToggleItalic: () -> Unit,
    onToggleUnderline: () -> Unit,
    onToggleStrike: () -> Unit,
    onToggleCode: () -> Unit,
    onToggleHighlight: () -> Unit,
    onToggleHeading2: () -> Unit,
    onToggleBullets: () -> Unit,
    onToggleOrdered: () -> Unit,
    onToggleTodo: () -> Unit,
    onToggleQuote: () -> Unit,
    onToggleInfo: () -> Unit,
    onToggleCodeBlock: () -> Unit,
    onToggleDivider: () -> Unit,
    onDeleteBlock: () -> Unit,
    onOpenHeadingMenu: () -> Unit,
    currentHeading: Int,
    onToggleLink: () -> Unit,
    onOpenTypeMenu: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    stateHasSelection: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuataRichTextToolbarTextButton("/", false, onOpenTypeMenu, "Comandos")
            QuataRichTextToolbarButton(Icons.AutoMirrored.Filled.Undo, false, onUndo, "Deshacer", enabled = canUndo)
            QuataRichTextToolbarButton(Icons.AutoMirrored.Filled.Redo, false, onRedo, "Rehacer", enabled = canRedo)
            QuataRichTextToolbarTextButton("B", state.isBold.value, onToggleBold, "Negrita", fontWeight = FontWeight.Bold)
            QuataRichTextToolbarTextButton("I", state.isItalic.value, onToggleItalic, "Cursiva", fontStyle = FontStyle.Italic)
            QuataRichTextToolbarTextButton("U", state.isUnderline.value, onToggleUnderline, "Subrayado", textDecoration = TextDecoration.Underline)
            QuataRichTextToolbarTextButton("S", state.isStrikethrough.value, onToggleStrike, "Tachado", textDecoration = TextDecoration.LineThrough)
            QuataRichTextToolbarTextButton("Aa", state.isInlineCode.value, onToggleCode, "Texto destacado gris", fontFamily = FontFamily.Monospace)
            QuataRichTextToolbarButton(Icons.Default.Highlight, state.isHighlight.value, onToggleHighlight, "Resaltado")
            QuataRichTextToolbarTextButton(if (currentHeading == 0) "H" else "H$currentHeading", state.isHeading.value, onOpenHeadingMenu, "Titulos")
            QuataRichTextToolbarButton(Icons.AutoMirrored.Filled.FormatListBulleted, state.isBulletedList.value, onToggleBullets, "Lista")
            QuataRichTextToolbarButton(Icons.Default.FormatListNumbered, state.isNumberedList.value, onToggleOrdered, "Lista numerada")
            QuataRichTextToolbarTextButton("☑", state.isTodo.value, onToggleTodo, "Tareas")
            QuataRichTextToolbarButton(Icons.Default.FormatQuote, state.isQuote.value, onToggleQuote, "Cita")
            QuataRichTextToolbarButton(Icons.Default.Info, state.isInfo.value, onToggleInfo, "Info")
            QuataRichTextToolbarButton(Icons.Default.Code, state.isCode.value, onToggleCodeBlock, "Bloque de codigo")
            QuataRichTextToolbarTextButton("—", state.isDivider.value, onToggleDivider, "Separador")
            QuataRichTextToolbarButton(Icons.Default.Link, state.isLinked.value, onToggleLink, "Enlace", enabled = stateHasSelection && !state.isSelectionCollapsed.value)
            QuataRichTextToolbarButton(Icons.Default.Delete, false, onDeleteBlock, "Eliminar bloque", enabled = stateHasSelection)
        }
    }
}

@Composable
private fun QuataRichTextToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(34.dp)
            .background(
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                shape = CircleShape,
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(21.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.72f else 0.32f),
        )
    }
}

@Composable
private fun QuataRichTextToolbarTextButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    fontFamily: FontFamily? = null,
    textDecoration: TextDecoration? = null,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            fontSize = 15.sp,
            fontWeight = fontWeight ?: FontWeight.SemiBold,
            fontStyle = fontStyle,
            fontFamily = fontFamily,
            textDecoration = textDecoration,
        )
    }
}

@Composable
private fun QuataRichTextHeadingMenu(
    current: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nivel de titulo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (level in 1..6) {
                    DropdownMenuItem(
                        text = { Text("H$level") },
                        onClick = { onSelect(level) },
                    )
                }
                DropdownMenuItem(
                    text = {
                        val label = if (current == 0) "Texto normal" else "Texto normal (actual: H$current)"
                        Text(label)
                    },
                    onClick = { onSelect(0) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
        dismissButton = {},
    )
}

@Composable
private fun BlockTypePicker(
    blockId: String,
    query: String,
    selectedIndex: Int,
    registry: QuataSlashCommandRegistry,
    onDismiss: () -> Unit,
    onSelect: (String, RichTextBlockType) -> Unit,
) {
    Surface(
        modifier = Modifier
            .widthIn(min = 236.dp, max = 320.dp)
            .heightIn(max = 360.dp),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
        val items = registry.filter(query)

        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.rich_text_no_block_matches),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            items.forEachIndexed { index, item ->
                BlockTypePickerItem(
                    label = item.label,
                    type = item.type,
                    blockId = blockId,
                    onDismiss = onDismiss,
                    isSelected = index == selectedIndex,
                    onSelect = onSelect,
                )
            }
        }
        }
    }
}

@Composable
private fun BlockTypePickerItem(
    label: String,
    type: RichTextBlockType,
    blockId: String,
    onDismiss: () -> Unit,
    isSelected: Boolean,
    onSelect: (String, RichTextBlockType) -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        },
        modifier = if (isSelected) {
            Modifier
                .padding(horizontal = 6.dp)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f), RoundedCornerShape(6.dp))
        } else {
            Modifier.padding(horizontal = 6.dp)
        },
        onClick = {
            onSelect(blockId, type)
            onDismiss()
        },
    )
}

@Composable
private fun QuataRichTextLinkDialog(
    initialUrl: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anadir enlace") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = { Text("https://dominio.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(url) }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun QuataRichTextBlockField(
    block: QuataRichTextBlock,
    value: TextFieldValue,
    valueStyle: androidx.compose.ui.text.TextStyle,
    focusRequester: FocusRequester,
    isEditable: Boolean,
    placeholder: @Composable () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color,
    listPrefix: String?,
    modifier: Modifier,
    keyboardOptions: KeyboardOptions,
    onValueChange: (TextFieldValue) -> Unit,
    onTodoCheckedChange: (Boolean) -> Unit,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
) {
    val textStyle = valueStyle
    val visualTransformation = rememberRichTextVisualTransformation(block.spans)
    val fieldBackground = when (block.type) {
        RichTextBlockType.Code -> containerColor
        RichTextBlockType.Quote,
        RichTextBlockType.Info -> containerColor
        else -> Color.Transparent
    }
    val fieldShape = when (block.type) {
        RichTextBlockType.Code,
        RichTextBlockType.Quote,
        RichTextBlockType.Info -> RoundedCornerShape(3.dp)
        else -> RoundedCornerShape(0.dp)
    }

    Row(
        modifier = modifier
            .background(fieldBackground, fieldShape)
            .padding(
                start = if (block.type == RichTextBlockType.Code || block.type == RichTextBlockType.Quote || block.type == RichTextBlockType.Info) 10.dp else 0.dp,
                end = if (block.type == RichTextBlockType.Code || block.type == RichTextBlockType.Quote || block.type == RichTextBlockType.Info) 10.dp else 0.dp,
                top = if (block.type == RichTextBlockType.Code || block.type == RichTextBlockType.Quote || block.type == RichTextBlockType.Info) 8.dp else 2.dp,
                bottom = if (block.type == RichTextBlockType.Code || block.type == RichTextBlockType.Quote || block.type == RichTextBlockType.Info) 8.dp else 2.dp,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        if (block.type == RichTextBlockType.Todo) {
            Checkbox(
                checked = block.isChecked,
                onCheckedChange = onTodoCheckedChange,
                modifier = Modifier
                    .padding(top = 1.dp, end = 6.dp)
                    .size(28.dp),
            )
        } else if (listPrefix != null && block.type != RichTextBlockType.Code) {
            Text(
                text = listPrefix,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                style = textStyle,
                modifier = Modifier
                    .width(34.dp)
                    .padding(top = 1.dp),
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = !isEditable,
            textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onKeyEvent { keyEvent -> onPreviewKeyEvent(keyEvent) },
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (value.text.isEmpty()) {
                        placeholder()
                    }
                    innerTextField()
                }
            },
        )
    }
}

private fun computeNumberedIndex(
    blocks: List<QuataRichTextBlock>,
    targetIndex: Int,
): Int {
    if (targetIndex < 0 || targetIndex >= blocks.size) return 1
    if (blocks[targetIndex].type != RichTextBlockType.Numbered) return 1
    var index = 1
    for (cursor in targetIndex - 1 downTo 0) {
        val candidate = blocks[cursor]
        if (candidate.type == RichTextBlockType.Numbered) {
            index++
            continue
        }
        break
    }
    return index
}

@Composable
private fun rememberRichTextVisualTransformation(spans: List<QuataTextSpan>): VisualTransformation {
    val primary = MaterialTheme.colorScheme.primary
    val highlight = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f)
    val inlineCodeBackground = MaterialTheme.colorScheme.surfaceVariant
    val normalized = remember(spans) { spans }

    return remember(normalized, primary, highlight, inlineCodeBackground) {
        VisualTransformation { text ->
            val builder = AnnotatedString.Builder(text.text)
            for (span in QuataSpanAlgorithms.normalize(normalized, text.text.length)) {
                builder.addStyle(
                    style = span.style.toComposeSpanStyle(
                        linkColor = primary,
                        highlightColor = highlight,
                        inlineCodeBackground = inlineCodeBackground,
                    ),
                    start = span.start,
                    end = span.end,
                )
            }
            TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
        }
    }
}

private fun QuataSpanStyle.toComposeSpanStyle(
    linkColor: Color,
    highlightColor: Color,
    inlineCodeBackground: Color,
): SpanStyle = when (this) {
    QuataSpanStyle.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
    QuataSpanStyle.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
    QuataSpanStyle.Underline -> SpanStyle(textDecoration = TextDecoration.Underline)
    QuataSpanStyle.Strike -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    QuataSpanStyle.Highlight -> SpanStyle(background = highlightColor)
    QuataSpanStyle.InlineCode -> SpanStyle(
        fontFamily = FontFamily.Monospace,
        background = inlineCodeBackground,
    )
    is QuataSpanStyle.Link -> SpanStyle(
        color = linkColor,
        textDecoration = TextDecoration.Underline,
    )
}

@Composable
private fun styleForBlock(type: RichTextBlockType) = when (type) {
    RichTextBlockType.Heading1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    RichTextBlockType.Heading2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    RichTextBlockType.Heading3 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
    RichTextBlockType.Heading4 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
    RichTextBlockType.Heading5 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)
    RichTextBlockType.Heading6 -> MaterialTheme.typography.titleSmall
    RichTextBlockType.Quote -> MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic)
    RichTextBlockType.Info -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
    RichTextBlockType.Code -> MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    else -> MaterialTheme.typography.bodyMedium
}

private fun blockTypeLabel(type: RichTextBlockType): String = when (type) {
    RichTextBlockType.Paragraph -> "Texto"
    RichTextBlockType.Heading1 -> "H1"
    RichTextBlockType.Heading2 -> "H2"
    RichTextBlockType.Heading3 -> "H3"
    RichTextBlockType.Bullet -> "Lista"
    RichTextBlockType.Numbered -> "Num."
    RichTextBlockType.Quote -> "Cita"
    RichTextBlockType.Info -> "Info"
    RichTextBlockType.Divider -> "Div"
    RichTextBlockType.Todo -> "Tarea"
    RichTextBlockType.Code -> "Codigo"
    RichTextBlockType.Heading4 -> "H4"
    RichTextBlockType.Heading5 -> "H5"
    RichTextBlockType.Heading6 -> "H6"
}

@Composable
private fun QuataRichTextDragIndicator(
    modifier: Modifier = Modifier,
    y: Float,
    futureRootIndent: Int = 0,
    blockHorizontalPaddingPx: Float = 0f,
    endPaddingPx: Float = 10f,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Float = 2f,
) {
    val animatedY by animateFloatAsState(
        targetValue = y,
        animationSpec = tween(durationMillis = DRAG_ANIMATION_DURATION_MS),
    )
    val animatedIndent by animateFloatAsState(
        targetValue = futureRootIndent.toFloat(),
        animationSpec = tween(durationMillis = DRAG_ANIMATION_DURATION_MS),
    )

    Canvas(modifier = modifier) {
        val geometry = calculateDragIndicatorGeometry(
            viewportWidthPx = size.width,
            futureRootIndent = animatedIndent,
            blockHorizontalPaddingPx = blockHorizontalPaddingPx,
            indentUnitPx = DRAG_INDENT_UNIT.toPx(),
        ) ?: return@Canvas
        val clampedY = animatedY.coerceIn(0f, size.height.toFloat())
        val clampedEndPadding = endPaddingPx.coerceAtLeast(0f)
        val start = geometry.first.coerceAtLeast(0f)
        val end = geometry.second.coerceAtMost(size.width - clampedEndPadding).coerceAtLeast(start)
        drawLine(
            color = color,
            start = Offset(start, clampedY),
            end = Offset(end, clampedY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun QuataDragPreview(
    block: QuataRichTextBlock,
    listPrefix: String?,
    valueStyle: androidx.compose.ui.text.TextStyle,
    dragPointerYInParent: Float,
    dragStartOffsetY: Float,
    payloadCount: Int,
    containerColor: Color,
    previewStartOffsetPx: Float,
    content: String,
) {
    val lineText = content.ifBlank { " " }
    val badge = if (payloadCount > 1) "+${payloadCount - 1}" else null
    val animatedOffsetPx by animateFloatAsState(
        targetValue = previewStartOffsetPx.coerceAtLeast(0f),
        animationSpec = tween(durationMillis = DRAG_ANIMATION_DURATION_MS),
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .graphicsLayer {
                translationX = animatedOffsetPx
                translationY = dragPointerYInParent - dragStartOffsetY
                alpha = DRAG_PREVIEW_ALPHA
            },
        color = containerColor,
        shadowElevation = DRAG_PREVIEW_SHADOW_ELEVATION.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (listPrefix != null) {
                Text(
                    text = listPrefix,
                    style = valueStyle,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            Text(
                text = lineText,
                style = valueStyle,
                maxLines = 2,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
            )
            if (badge != null) {
                Text(
                    text = badge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        )
                        .padding(
                            horizontal = DRAG_BADGE_PADDING_HORIZONTAL,
                            vertical = DRAG_BADGE_PADDING_VERTICAL,
                        ),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun QuataRichTextEditorPreview() {
    var html by rememberSaveable { mutableStateOf("") }
    val state = rememberQuataRichTextEditorState(
        """
            <h1>Documento</h1>
            <p>Prueba <b>negrita</b>, <i>cursiva</i> y <u>subrayado</u>.</p>
            <ul><li>Elemento 1</li><li>Elemento 2</li></ul>
            <blockquote>Cita de ejemplo.</blockquote>
            <pre><code>print("hola")</code></pre>
        """.trimIndent(),
    )
    Column(modifier = Modifier.padding(8.dp)) {
        QuataRichTextEditor(
            state = state,
            onHtmlChange = { html = it },
        )
        Spacer(modifier = Modifier.height(8.dp))
        QuataRichTextRenderer(
            html = html,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}



