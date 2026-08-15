package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

const val ChatPendingAttachmentOverlayTestTag = "chat.attachment.pending"
const val ChatPendingAttachmentClearTestTag = "chat.attachment.pending.clear"

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
        modifier = modifier.semantics {
            testTag = ChatPendingAttachmentOverlayTestTag
            contentDescription = "${ChatPendingAttachmentOverlayTestTag} $name"
        },
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                preview()
                Spacer(Modifier.height(14.dp))
                Text(name, color = textColor, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            clearAction(Modifier.align(Alignment.TopEnd).padding(8.dp).semantics { testTag = ChatPendingAttachmentClearTestTag })
        }
    }
}
