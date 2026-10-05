package com.quata.core.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.platform.MediaFileExportAction
import com.quata.core.platform.MediaFileExportDescriptor
import com.quata.core.platform.PlatformResult
import kotlinx.coroutines.launch

const val QuataMediaExportDownloadTestTag = "media-export.download"
const val QuataMediaExportShareTestTag = "media-export.share"
const val QuataMediaExportRetryTestTag = "media-export.retry"
const val QuataMediaExportFailureTestTag = "media-export.failure"

@Composable
fun QuataMediaExportActionsContent(
    descriptor: MediaFileExportDescriptor,
    downloadLabel: String,
    shareLabel: String,
    failureLabel: String,
    retryLabel: String,
    onExport: suspend (MediaFileExportDescriptor, MediaFileExportAction) -> PlatformResult<Unit>,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var running by remember(descriptor) { mutableStateOf<MediaFileExportAction?>(null) }
    var failed by remember(descriptor) { mutableStateOf<MediaFileExportAction?>(null) }
    fun launch(action: MediaFileExportAction) {
        if (running != null) return
        running = action
        failed = null
        scope.launch {
            when (onExport(descriptor, action)) {
                is PlatformResult.Success, PlatformResult.Cancelled -> Unit
                is PlatformResult.Failure, PlatformResult.Unsupported -> failed = action
            }
            running = null
        }
    }

    val template = quataTheme()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        if (failed != null) {
            Text(
                text = failureLabel,
                color = template.colors.error,
                maxLines = 1,
                modifier = Modifier
                    .testTag(QuataMediaExportFailureTestTag)
                    .semantics { contentDescription = failureLabel },
            )
            Spacer(Modifier.width(4.dp))
            CompactIconButton(
                onClick = { failed?.let(::launch) },
                enabled = running == null,
                modifier = Modifier
                    .testTag(QuataMediaExportRetryTestTag)
                    .semantics { contentDescription = retryLabel },
            ) {
                CompactIcon(Icons.Filled.Refresh, contentDescription = null, tint = template.colors.textPrimary)
            }
        } else {
            CompactIconButton(
                onClick = { launch(MediaFileExportAction.Download) },
                enabled = running == null,
                modifier = Modifier
                    .testTag(QuataMediaExportDownloadTestTag)
                    .semantics { contentDescription = downloadLabel },
            ) {
                if (running == MediaFileExportAction.Download) CircularProgressIndicator(strokeWidth = 2.dp)
                else CompactIcon(Icons.Filled.Download, contentDescription = null, tint = template.colors.textPrimary)
            }
            CompactIconButton(
                onClick = { launch(MediaFileExportAction.Share) },
                enabled = running == null,
                modifier = Modifier
                    .testTag(QuataMediaExportShareTestTag)
                    .semantics { contentDescription = shareLabel },
            ) {
                if (running == MediaFileExportAction.Share) CircularProgressIndicator(strokeWidth = 2.dp)
                else CompactIcon(Icons.Filled.Share, contentDescription = null, tint = template.colors.textPrimary)
            }
        }
    }
}
