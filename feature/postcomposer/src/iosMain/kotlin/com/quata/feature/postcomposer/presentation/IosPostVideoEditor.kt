package com.quata.feature.postcomposer.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import com.quata.core.captions.templates.CaptionTemplateStyle
import com.quata.core.platform.PlatformFile
import com.quata.feature.postcomposer.videoeditor.CaptionStyleOption
import com.quata.feature.postcomposer.videoeditor.MaximumPostVideoEditorDurationMs
import com.quata.feature.postcomposer.videoeditor.PostVideoEditorDialogContent
import com.quata.feature.postcomposer.videoeditor.PostVideoEditorExportSpec
import com.quata.feature.postcomposer.videoeditor.PostVideoEditorUiState
import com.quata.feature.postcomposer.videoeditor.postVideoEditorExportSpec
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterCaptionsToggle
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterCropToggle
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterTrimEnd
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterTrimStart
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
    val durationMs = MaximumPostVideoEditorDurationMs
    val videoAspectRatio = 9f / 16f
    fun export() {
        if (state.isExporting) return
        state = state.copy(isExporting = true, exportProgress = 0.35f, error = null)
        val spec = postVideoEditorExportSpec(state, videoAspectRatio, durationMs)
        scope.launch {
            runCatching { iosPostVideoEditorExportEdited(source, spec) }
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
        onTrimStartChange = { state = postVideoEditorStateAfterTrimStart(state, it) },
        onTrimEndChange = { state = postVideoEditorStateAfterTrimEnd(state, it) },
        onCropToggle = { state = postVideoEditorStateAfterCropToggle(state) },
        onCaptionsToggle = { state = postVideoEditorStateAfterCaptionsToggle(state) },
        onReset = { state = PostVideoEditorUiState() },
        onDismiss = onDismiss,
        onExport = ::export,
        captionOptions = CaptionTemplateStyle.entries.map { CaptionStyleOption(it.name, it.name) },
        onCropModeChange = { state = state.copy(cropMode = it, cropEnabled = true, cropZoom = 1f, cropCenterX = 0.5f, cropCenterY = 0.5f) },
        onCropZoomChange = { state = state.copy(cropZoom = it.coerceIn(1f, 3f), cropEnabled = true) },
        onCropPanChange = { dx, dy ->
            state = state.copy(
                cropEnabled = true,
                cropCenterX = (state.cropCenterX + dx).coerceIn(0f, 1f),
                cropCenterY = (state.cropCenterY + dy).coerceIn(0f, 1f),
            )
        },
        onCaptionStyleChange = { state = state.copy(selectedCaptionStyleId = it, captionsEnabled = it != null) },
        onSeekChange = { state = state.copy(currentPositionFraction = it.coerceIn(0f, 1f)) },
        preview = { modifier: Modifier ->
            Text(source.displayName?.ifBlank { "Video seleccionado" } ?: "Video seleccionado", modifier = modifier)
        },
    )
}

private fun iosPostVideoEditorExportEdited(source: PlatformFile, spec: PostVideoEditorExportSpec): PlatformFile {
    if (spec.hasCrop || spec.hasCaptions || spec.removeAudio || spec.trimStartMs > 0L || spec.trimEndMs < spec.sourceDurationMs) {
        error("ios_post_video_editor_native_export_required")
    }
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
