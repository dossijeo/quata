package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared bubble-header layout; delivery and favorite affordances remain injectable host slots. */
@Composable
fun ChatMessageHeaderContent(
    senderName: String,
    timestamp: String,
    isMine: Boolean,
    isEdited: Boolean,
    isFavorite: Boolean,
    editedLabel: String,
    textColor: Color,
    deliveryIndicator: (@Composable () -> Unit)?,
    favoriteMarker: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth().height(if (isEdited || isFavorite) 32.dp else 16.dp)) {
        androidx.compose.material3.Text(
            text = senderName,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(end = 104.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(104.dp)
                .height(16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Row(modifier = Modifier.align(Alignment.TopEnd)) {
                androidx.compose.material3.Text(
                    text = timestamp,
                    color = textColor.copy(alpha = 0.56f),
                    fontSize = 12.sp
                )
                if (isMine) {
                    androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
                    deliveryIndicator?.invoke()
                }
            }
            if (isEdited || isFavorite) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isEdited) {
                        androidx.compose.material3.Text(
                            text = editedLabel,
                            color = textColor.copy(alpha = 0.62f),
                            fontSize = 11.sp
                        )
                    }
                    if (isFavorite) {
                        if (isEdited) {
                            androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
                        }
                        favoriteMarker?.invoke()
                    }
                }
            }
        }
    }
}
