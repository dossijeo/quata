package com.quata.feature.chat.presentation.chat

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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

private const val UrlAnnotationTag = "url"
private val UrlRegex = Regex("""(https?://[^\s]+|www\.[^\s]+)""")

@Composable
fun ChatLinkifiedTextContent(text: String, color: Color, linkColor: Color, onOpenLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val annotatedText = remember(text, linkColor) { text.toChatLinkAnnotatedString(linkColor) }
    if (annotatedText.getStringAnnotations(UrlAnnotationTag, 0, annotatedText.length).isEmpty()) {
        Text(text, color = color, modifier = modifier)
    } else {
        var layoutResult by remember(annotatedText) { mutableStateOf<TextLayoutResult?>(null) }
        BasicText(annotatedText, style = TextStyle(color = color, fontSize = 14.sp), modifier = modifier.pointerInput(annotatedText) {
            detectTapGestures { position ->
                val offset = layoutResult?.getOffsetForPosition(position) ?: return@detectTapGestures
                annotatedText.getStringAnnotations(UrlAnnotationTag, offset, offset).firstOrNull()?.item?.let(onOpenLink)
            }
        }, onTextLayout = { layoutResult = it })
    }
}

fun String.toChatLinkAnnotatedString(linkColor: Color): AnnotatedString = buildAnnotatedString {
    var currentIndex = 0
    UrlRegex.findAll(this@toChatLinkAnnotatedString).forEach { match ->
        if (match.range.first > currentIndex) append(this@toChatLinkAnnotatedString.substring(currentIndex, match.range.first))
        val rawUrl = match.value.trimEnd('.', ',', ';', ')')
        val normalizedUrl = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) rawUrl else "https://$rawUrl"
        pushStringAnnotation(UrlAnnotationTag, normalizedUrl)
        pushStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Bold))
        append(rawUrl)
        pop(); pop()
        currentIndex = match.range.first + rawUrl.length
    }
    if (currentIndex < this@toChatLinkAnnotatedString.length) append(this@toChatLinkAnnotatedString.substring(currentIndex))
}
