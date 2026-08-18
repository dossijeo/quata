package com.quata.feature.chat.presentation.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.quata.core.ui.components.QuataEmojiLinkifiedText

@Composable
fun ChatLinkifiedTextContent(text: String, color: Color, linkColor: Color, onOpenLink: (String) -> Unit, modifier: Modifier = Modifier) {
    QuataEmojiLinkifiedText(
        value = text,
        color = color,
        linkColor = linkColor,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        maxLines = Int.MAX_VALUE,
        overflow = TextOverflow.Clip,
        fontWeight = null,
        onOpenLink = onOpenLink,
        modifier = modifier,
    )
}
