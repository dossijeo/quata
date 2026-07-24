package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Shared non-media attachment card; hosts provide the file action and icon implementation. */
@Composable
fun ChatDocumentAttachmentContent(
    name: String,
    textColor: Color,
    onOpen: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(8.dp))
            Text(name, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
