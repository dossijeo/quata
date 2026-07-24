package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Shared pending-attachment surface; URI/media rendering and icon resources are supplied by the host. */
@Composable
fun ChatPendingAttachmentOverlayContent(
    name: String,
    surfaceColor: Color,
    textColor: Color,
    onOpen: () -> Unit,
    preview: @Composable () -> Unit,
    clearAction: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = surfaceColor,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 8.dp,
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().clickable(onClick = onOpen).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                preview()
                Spacer(Modifier.height(14.dp))
                Text(name, color = textColor, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            clearAction(Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
    }
}
