package com.quata.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/** Platform text surface that paints catalog emoji through common Compose resources. */
@Composable
expect fun QuataEmojiStaticText(
    value: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    maxLines: Int,
    overflow: TextOverflow,
    fontWeight: FontWeight? = null,
    modifier: Modifier = Modifier,
)
