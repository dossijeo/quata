package com.quata.core.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

@Composable
actual fun QuataEmojiLinkifiedText(value: String, color: Color, linkColor: Color, fontSize: TextUnit, lineHeight: TextUnit, maxLines: Int, overflow: TextOverflow, fontWeight: FontWeight?, onOpenLink: (String) -> Unit, modifier: Modifier) {
    val annotated = remember(value, linkColor) { quataEmojiLinkifiedAnnotatedString(value, linkColor) }
    val inlineContent = rememberQuataEmojiInlineContent(value)
    var layoutResult by remember(annotated) { mutableStateOf<TextLayoutResult?>(null) }
    BasicText(
        text = annotated,
        modifier = modifier.pointerInput(annotated) {
            detectTapGestures { position ->
                val offset = layoutResult?.getOffsetForPosition(position) ?: return@detectTapGestures
                annotated.getStringAnnotations(QuataLinkAnnotationTag, offset, offset).firstOrNull()?.item?.let(onOpenLink)
            }
        },
        style = TextStyle(color = color, fontSize = fontSize, lineHeight = lineHeight, fontWeight = fontWeight),
        onTextLayout = { layoutResult = it },
        overflow = overflow,
        maxLines = maxLines,
        inlineContent = inlineContent,
    )
}
