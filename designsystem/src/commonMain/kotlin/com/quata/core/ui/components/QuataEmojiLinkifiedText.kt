package com.quata.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

@Composable
expect fun QuataEmojiLinkifiedText(
    value: String,
    color: Color,
    linkColor: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    maxLines: Int,
    overflow: TextOverflow,
    fontWeight: FontWeight?,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
)
