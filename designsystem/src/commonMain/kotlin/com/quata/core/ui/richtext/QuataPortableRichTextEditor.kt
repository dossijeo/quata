package com.quata.core.ui.richtext

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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

const val QuataPortableRichTextFieldTestTag = "quata-portable-rich-text-field"

@Composable
fun QuataPortableRichTextEditorBox(
    initialHtml: String,
    placeholder: String,
    onHtmlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember(initialHtml) { QuataRichTextEditorState(initialHtml) }
    val html = state.html
    LaunchedEffect(html) {
        onHtmlChange(html)
    }
    QuataPortableRichTextEditor(
        state = state,
        placeholder = placeholder,
        modifier = modifier,
    )
}

@Composable
private fun QuataPortableRichTextEditor(
    state: QuataRichTextEditorState,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
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
            QuataPortableRichTextToolbar(state)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            state.blocks.forEachIndexed { index, block ->
                QuataPortableRichTextBlockField(
                    block = block,
                    orderedIndex = if (block.type == RichTextBlockType.Numbered) {
                        portableNumberedIndex(state.blocks, index)
                    } else {
                        null
                    },
                    placeholder = placeholder,
                    onSelected = { state.selectBlock(block.id) },
                    onTodoCheckedChange = { state.toggleTodoChecked(block.id) },
                    onValueChange = { value -> state.updateBlockText(block.id, value) },
                )
            }
        }
    }
}

@Composable
private fun QuataPortableRichTextToolbar(state: QuataRichTextEditorState) {
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
        QuataPortableToolbarButton(Icons.Filled.Highlight, true, state.isHighlight.value, state::toggleHighlight, "Highlight")
        Spacer(Modifier.width(6.dp))
        QuataPortableToolbarButton(Icons.Filled.Title, true, state.isHeading.value, { state.toggleHeading(2) }, "Heading")
        QuataPortableToolbarButton(Icons.AutoMirrored.Filled.FormatListBulleted, true, state.isBulletedList.value, { state.toggleList("bullet") }, "Bullet list")
        QuataPortableToolbarButton(Icons.Filled.FormatListNumbered, true, state.isNumberedList.value, { state.toggleList("numbered") }, "Numbered list")
        QuataPortableToolbarButton(Icons.Filled.FormatQuote, true, state.isQuote.value, state::setQuote, "Quote")
        QuataPortableToolbarButton(Icons.Filled.Info, true, state.isInfo.value, state::setInfo, "Info")
        QuataPortableToolbarButton(Icons.Filled.Code, true, state.isCode.value, state::setCode, "Code block")
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
private fun QuataPortableRichTextBlockField(
    block: QuataRichTextBlock,
    orderedIndex: Int?,
    placeholder: String,
    onSelected: () -> Unit,
    onTodoCheckedChange: () -> Unit,
    onValueChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
) {
    val textStyle = portableStyleForBlock(block.type)
    val visualTransformation = rememberPortableRichTextVisualTransformation(block.spans)
    val focusRequester = remember(block.id) { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val interactionSource = remember(block.id) { MutableInteractionSource() }
    if (block.type == RichTextBlockType.Divider) {
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outline,
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(portableBackgroundForBlock(block.type), MaterialTheme.shapes.small)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                onSelected()
                focusRequester.requestFocus()
                keyboardController?.show()
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
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
                .testTag(QuataPortableRichTextFieldTestTag)
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) onSelected() }
                .padding(start = if (block.type == RichTextBlockType.Paragraph) 0.dp else 4.dp),
            decorationBox = { inner ->
                Box(modifier = Modifier.fillMaxWidth()) {
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
