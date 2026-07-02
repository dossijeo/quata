package com.quata.core.ui.richtext

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val LinkAnnotationTag = "quata-rich-text-link"
private val RendererIndentUnit = 20.dp

@Composable
fun QuataRichTextRenderer(
    html: String,
    modifier: Modifier = Modifier,
    placeholder: String = "Sin contenido",
) {
    val blocks = remember(html) { parseHtmlToRichTextBlocks(html) }

    if (blocks.isEmpty()) {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEachIndexed { index, block ->
            QuataRichTextRenderedBlock(
                block = block,
                orderedIndex = if (block.type == RichTextBlockType.Numbered) {
                    computeRendererNumberedIndex(blocks, index)
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun QuataRichTextRenderedBlock(
    block: QuataRichTextBlock,
    orderedIndex: Int?,
) {
    when (block.type) {
        RichTextBlockType.Divider -> HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        RichTextBlockType.Code -> QuataRichTextCodeBlock(block.text.text)

        RichTextBlockType.Quote -> QuataRichTextCalloutBlock(
            block = block,
            markerColor = MaterialTheme.colorScheme.secondary,
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.24f),
            textStyle = rendererStyleForBlock(block.type),
        )

        RichTextBlockType.Info -> QuataRichTextCalloutBlock(
            block = block,
            markerColor = MaterialTheme.colorScheme.tertiary,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.28f),
            textStyle = rendererStyleForBlock(block.type),
        )

        RichTextBlockType.Bullet,
        RichTextBlockType.Numbered,
        RichTextBlockType.Todo -> QuataRichTextListBlock(block, orderedIndex)

        else -> QuataRichTextTextBlock(
            block = block,
            modifier = Modifier,
            textStyle = rendererStyleForBlock(block.type),
        )
    }
}

@Composable
private fun QuataRichTextListBlock(
    block: QuataRichTextBlock,
    orderedIndex: Int?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 0.dp),
        verticalAlignment = Alignment.Top,
    ) {
        when (block.type) {
            RichTextBlockType.Todo -> Checkbox(
                checked = block.isChecked,
                enabled = false,
                onCheckedChange = null,
                modifier = Modifier.width(28.dp),
            )

            RichTextBlockType.Numbered -> Text(
                text = "${orderedIndex ?: 1}.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(28.dp),
            )

            else -> Text(
                text = "\u2022",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(28.dp),
            )
        }
        QuataRichTextTextBlock(
            block = block,
            modifier = Modifier.weight(1f),
            textStyle = rendererStyleForBlock(block.type).let { style ->
                if (block.type == RichTextBlockType.Todo && block.isChecked) {
                    style.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    )
                } else {
                    style
                }
            },
        )
    }
}

@Composable
private fun QuataRichTextCalloutBlock(
    block: QuataRichTextBlock,
    markerColor: Color,
    containerColor: Color,
    textStyle: TextStyle,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp, end = 10.dp)
                    .width(3.dp)
                    .height(28.dp)
                    .background(markerColor, RoundedCornerShape(999.dp)),
            )
            QuataRichTextTextBlock(
                block = block,
                modifier = Modifier.weight(1f),
                textStyle = textStyle,
            )
        }
    }
}

@Composable
private fun QuataRichTextCodeBlock(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = text.ifEmpty { " " },
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 9.dp),
            softWrap = false,
        )
    }
}

@Composable
private fun QuataRichTextTextBlock(
    block: QuataRichTextBlock,
    modifier: Modifier,
    textStyle: TextStyle,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val highlightColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f)
    val inlineCodeBackground = MaterialTheme.colorScheme.surfaceVariant
    val annotated = remember(
        block.text.text,
        block.spans,
        linkColor,
        highlightColor,
        inlineCodeBackground,
    ) {
        block.text.text.toRendererAnnotatedString(
            spans = block.spans,
            linkColor = linkColor,
            highlightColor = highlightColor,
            inlineCodeBackground = inlineCodeBackground,
        )
    }
    val uriHandler = LocalUriHandler.current
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val hasLinks = annotated.getStringAnnotations(LinkAnnotationTag, 0, annotated.length).isNotEmpty()
    val linkModifier = if (hasLinks) {
        Modifier.pointerInput(annotated) {
            detectTapGestures { position ->
                val offset = layoutResult?.getOffsetForPosition(position) ?: return@detectTapGestures
                annotated
                    .getStringAnnotations(LinkAnnotationTag, offset, offset)
                    .firstOrNull()
                    ?.item
                    ?.let(uriHandler::openUri)
            }
        }
    } else {
        Modifier
    }

    Text(
        text = annotated,
        style = textStyle,
        color = textStyle.color.takeOrElse { MaterialTheme.colorScheme.onSurface },
        modifier = modifier.then(linkModifier),
        onTextLayout = { layoutResult = it },
    )
}

@Composable
private fun rendererStyleForBlock(type: RichTextBlockType): TextStyle = when (type) {
    RichTextBlockType.Heading1 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
    RichTextBlockType.Heading2 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    RichTextBlockType.Heading3 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    RichTextBlockType.Heading4 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
    RichTextBlockType.Heading5 -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    RichTextBlockType.Heading6 -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
    RichTextBlockType.Quote -> MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic)
    RichTextBlockType.Info -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
    else -> MaterialTheme.typography.bodyMedium
}

private fun computeRendererNumberedIndex(
    blocks: List<QuataRichTextBlock>,
    targetIndex: Int,
): Int {
    if (targetIndex !in blocks.indices || blocks[targetIndex].type != RichTextBlockType.Numbered) return 1
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

private fun String.toRendererAnnotatedString(
    spans: List<QuataTextSpan>,
    linkColor: Color,
    highlightColor: Color,
    inlineCodeBackground: Color,
): AnnotatedString {
    val source = this
    return buildAnnotatedString {
        append(source)
        for (span in QuataSpanAlgorithms.normalize(spans, source.length)) {
            addStyle(
                style = span.style.toRendererSpanStyle(
                    linkColor = linkColor,
                    highlightColor = highlightColor,
                    inlineCodeBackground = inlineCodeBackground,
                ),
                start = span.start,
                end = span.end,
            )
            if (span.style is QuataSpanStyle.Link) {
                addStringAnnotation(
                    tag = LinkAnnotationTag,
                    annotation = span.style.url,
                    start = span.start,
                    end = span.end,
                )
            }
        }
    }
}

private fun QuataSpanStyle.toRendererSpanStyle(
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
