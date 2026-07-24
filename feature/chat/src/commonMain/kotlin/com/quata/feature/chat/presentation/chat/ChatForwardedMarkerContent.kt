package com.quata.feature.chat.presentation.chat

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp

/** Shared marker shown before the contents of a forwarded chat message. */
@Composable
fun ChatForwardedMarkerContent(label: String, textColor: Color) {
    Text(
        label,
        color = textColor.copy(alpha = 0.72f),
        fontStyle = FontStyle.Italic,
        fontSize = 12.sp,
    )
}
