package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared quoted-message block rendered inside a chat bubble. */
@Composable
fun ChatReplyQuoteContent(
    senderName: String,
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(senderName, fontWeight = FontWeight.Bold, color = textColor, fontSize = 12.sp)
            Text(text, color = textColor.copy(alpha = 0.72f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
