package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

const val ChatDocumentAttachmentTestTag = "chat.attachment.document"
const val ChatDocumentAttachmentOpenTestTag = "chat.attachment.document.open"
const val ChatDocumentAttachmentDownloadTestTag = "chat.attachment.document.download"
const val ChatDocumentAttachmentShareTestTag = "chat.attachment.document.share"

/** Shared non-media attachment card; hosts provide the file action and icon implementation. */
@Composable
fun ChatDocumentAttachmentContent(
    name: String,
    textColor: Color,
    onOpen: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    openLabel: String = "Open attachment",
    downloadLabel: String? = null,
    shareLabel: String? = null,
    onDownload: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.semantics { testTag = ChatDocumentAttachmentTestTag },
    ) {
        Row(
            Modifier
                .clickable(onClick = onOpen)
                .semantics {
                    testTag = ChatDocumentAttachmentOpenTestTag
                    contentDescription = openLabel
                }
                .padding(start = 10.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icon()
            Spacer(Modifier.width(2.dp))
            Text(name, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (onDownload != null && downloadLabel != null) {
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier.semantics {
                        testTag = ChatDocumentAttachmentDownloadTestTag
                        contentDescription = downloadLabel
                    },
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, tint = textColor)
                }
            }
            if (onShare != null && shareLabel != null) {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.semantics {
                        testTag = ChatDocumentAttachmentShareTestTag
                        contentDescription = shareLabel
                    },
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, tint = textColor)
                }
            }
        }
    }
}
