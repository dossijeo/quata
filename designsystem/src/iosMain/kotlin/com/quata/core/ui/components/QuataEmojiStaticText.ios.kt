package com.quata.core.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

@Composable
actual fun QuataEmojiStaticText(value: String, color: Color, fontSize: TextUnit, lineHeight: TextUnit, maxLines: Int, overflow: TextOverflow, fontWeight: FontWeight?, modifier: Modifier) {
    val inline = rememberQuataEmojiInlineText(value)
    Text(inline.text, inlineContent = inline.inlineContent, color = color, fontSize = fontSize, lineHeight = lineHeight, maxLines = maxLines, overflow = overflow, fontWeight = fontWeight, modifier = modifier)
}
