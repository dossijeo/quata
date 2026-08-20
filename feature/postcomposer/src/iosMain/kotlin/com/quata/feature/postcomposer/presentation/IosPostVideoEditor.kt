package com.quata.feature.postcomposer.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import com.quata.core.platform.PlatformFile
import com.quata.feature.postcomposer.videoeditor.PostVideoEditorDialogContent
import com.quata.feature.postcomposer.videoeditor.PostVideoEditorUiState
import kotlinx.coroutines.launch
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToFile

@Composable
internal fun IosPostVideoEditor(
    source: PlatformFile,
    onDismiss: () -> Unit,
    onEdited: (PlatformFile) -> Unit,
) {
    var state by remember(source.reference) { mutableStateOf(PostVideoEditorUiState()) }
    val scope = rememberCoroutineScope()
    fun export() {
        if (state.isExporting) return
        state = state.copy(isExporting = true, exportProgress = 0.35f, error = null)
        scope.launch {
            runCatching { iosPostVideoEditorExportCopy(source) }
                .onSuccess {
                    state = state.copy(isExporting = false, exportProgress = 1f)
                    onEdited(it)
                }
                .onFailure {
                    state = state.copy(isExporting = false, error = it.message ?: "ios_post_video_editor_export_failed")
                }
        }
    }

    PostVideoEditorDialogContent(
        state = state,
        onMutedChange = { state = state.copy(isMuted = it) },
        onPlayPause = { state = state.copy(isPlaying = !state.isPlaying) },
        onTrimStartChange = { state = state.copy(trimStartFraction = it) },
        onTrimEndChange = { state = state.copy(trimEndFraction = it) },
        onCropToggle = { state = state.copy(cropEnabled = !state.cropEnabled) },
        onCaptionsToggle = { state = state.copy(captionsEnabled = !state.captionsEnabled) },
        onReset = { state = PostVideoEditorUiState() },
        onDismiss = onDismiss,
        onExport = ::export,
        preview = { modifier: Modifier ->
            Text(source.displayName?.ifBlank { "Video seleccionado" } ?: "Video seleccionado", modifier = modifier)
        },
    )
}

private fun iosPostVideoEditorExportCopy(source: PlatformFile): PlatformFile {
    val input = iosPostVideoEditorLocalUrl(source.reference) ?: error("ios_post_video_editor_source_invalid")
    val data = NSData.dataWithContentsOfURL(input) ?: error("ios_post_video_editor_read_failed")
    val path = NSTemporaryDirectory().trimEnd('/') + "/quata-post-video-editor-${NSUUID.UUID().UUIDString}.mp4"
    if (!data.writeToFile(path, atomically = true)) error("ios_post_video_editor_write_failed")
    val url = NSURL.fileURLWithPath(path)
    return PlatformFile(
        reference = url.absoluteString ?: path,
        displayName = "post-video-editor.mp4",
        mimeType = source.mimeType?.ifBlank { "video/mp4" } ?: "video/mp4",
    )
}

private fun iosPostVideoEditorLocalUrl(reference: String): NSURL? {
    val value = reference.trim()
    return when {
        value.startsWith("file://") -> NSURL(string = value)
        value.startsWith("/") -> NSURL.fileURLWithPath(value)
        else -> null
    }?.takeIf { it.isFileURL() }
}
