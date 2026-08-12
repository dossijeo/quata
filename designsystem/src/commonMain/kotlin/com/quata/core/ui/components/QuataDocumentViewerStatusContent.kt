package com.quata.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quata.core.platform.DocumentViewerFailureReason
import com.quata.core.platform.DocumentViewerState

const val QuataDocumentViewerStatusRootTestTag = "document-viewer-status-root"
const val QuataDocumentViewerStatusTitleTestTag = "document-viewer-status-title"
const val QuataDocumentViewerStatusMessageTestTag = "document-viewer-status-message"
const val QuataDocumentViewerStatusCloseTestTag = "document-viewer-status-close"

data class QuataDocumentViewerStatusStrings(
    val openingTitle: String,
    val openedTitle: String,
    val failedTitle: String,
    val openingMessage: String,
    val openedMessage: String,
    val cancelledMessage: String,
    val unsupportedFormatMessage: String,
    val platformUnsupportedMessage: String,
    val openFailedMessage: String,
    val closeLabel: String,
)

@Composable
fun QuataDocumentViewerStatusContent(
    state: DocumentViewerState?,
    strings: QuataDocumentViewerStatusStrings,
    onDismiss: () -> Unit,
) {
    val visibleState = state ?: return
    if (visibleState is DocumentViewerState.Idle) return

    AlertDialog(
        modifier = Modifier.testTag(QuataDocumentViewerStatusRootTestTag),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = visibleState.title(strings),
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.testTag(QuataDocumentViewerStatusTitleTestTag),
            )
        },
        text = {
            Column {
                Text(
                    text = visibleState.fileName,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (visibleState is DocumentViewerState.Opening) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        text = visibleState.message(strings),
                        modifier = Modifier
                            .weight(1f)
                            .testTag(QuataDocumentViewerStatusMessageTestTag),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(QuataDocumentViewerStatusCloseTestTag),
            ) {
                Text(strings.closeLabel)
            }
        },
    )
}

private val DocumentViewerState.fileName: String
    get() = when (this) {
        DocumentViewerState.Idle -> ""
        is DocumentViewerState.Opening -> file.displayName ?: file.reference.substringAfterLast('/')
        is DocumentViewerState.Opened -> file.displayName ?: file.reference.substringAfterLast('/')
        is DocumentViewerState.Failed -> file.displayName ?: file.reference.substringAfterLast('/')
    }.ifBlank { "document" }

private fun DocumentViewerState.title(strings: QuataDocumentViewerStatusStrings): String = when (this) {
    DocumentViewerState.Idle -> ""
    is DocumentViewerState.Opening -> strings.openingTitle
    is DocumentViewerState.Opened -> strings.openedTitle
    is DocumentViewerState.Failed -> strings.failedTitle
}

private fun DocumentViewerState.message(strings: QuataDocumentViewerStatusStrings): String = when (this) {
    DocumentViewerState.Idle -> ""
    is DocumentViewerState.Opening -> strings.openingMessage
    is DocumentViewerState.Opened -> strings.openedMessage
    is DocumentViewerState.Failed -> when (reason) {
        DocumentViewerFailureReason.Cancelled -> strings.cancelledMessage
        DocumentViewerFailureReason.UnsupportedFormat -> strings.unsupportedFormatMessage
        DocumentViewerFailureReason.PlatformUnsupported -> strings.platformUnsupportedMessage
        DocumentViewerFailureReason.OpenFailed -> detail ?: strings.openFailedMessage
    }
}
