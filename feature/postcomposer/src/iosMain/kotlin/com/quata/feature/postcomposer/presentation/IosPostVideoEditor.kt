package com.quata.feature.postcomposer.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.quata.core.captions.templates.CaptionTemplateStyle
import com.quata.core.platform.IosVideoThumbnailService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.feature.postcomposer.videoeditor.CaptionStyleOption
import com.quata.feature.postcomposer.videoeditor.MaximumPostVideoEditorDurationMs
import com.quata.feature.postcomposer.videoeditor.PostVideoEditorDialogContent
import com.quata.feature.postcomposer.videoeditor.PostVideoEditorExportSpec
import com.quata.feature.postcomposer.videoeditor.PostVideoEditorUiState
import com.quata.feature.postcomposer.videoeditor.postVideoEditorExportSpec
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterCaptionsToggle
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterCropModeChange
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterCropPan
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterCropToggle
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterCropZoomChange
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterTrimEnd
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterTrimStart
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import platform.Foundation.NSURL

@Composable
internal fun IosPostVideoEditor(
    source: PlatformFile,
    onDismiss: () -> Unit,
    onEdited: (PlatformFile) -> Unit,
) {
    var state by remember(source.reference) { mutableStateOf(PostVideoEditorUiState()) }
    var thumbnail by remember(source.reference) { mutableStateOf<PlatformFile?>(null) }
    val scope = rememberCoroutineScope()
    val durationMs = MaximumPostVideoEditorDurationMs
    val videoAspectRatio = 9f / 16f
    LaunchedEffect(source.reference) {
        thumbnail = when (val result = IosVideoThumbnailService().createThumbnail(source, maxWidth = 720)) {
            is PlatformResult.Success -> result.value
            else -> null
        }
    }
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
        onCropModeChange = { state = postVideoEditorStateAfterCropModeChange(state, it, videoAspectRatio) },
        onCropZoomChange = { state = postVideoEditorStateAfterCropZoomChange(state, it, videoAspectRatio) },
        onCropPanChange = { dx, dy -> state = postVideoEditorStateAfterCropPan(state, dx, dy, videoAspectRatio) },
        onCaptionStyleChange = { state = state.copy(selectedCaptionStyleId = it, captionsEnabled = it != null) },
        onSeekChange = { state = state.copy(currentPositionFraction = it.coerceIn(0f, 1f)) },
        preview = { modifier: Modifier ->
            val previewFile = thumbnail
            if (previewFile != null) {
                IosComposerLocalImagePreview(previewFile, modifier = modifier)
            } else {
                IosComposerLocalImagePreview(source, modifier = modifier)
            }
        },
    )
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun iosPostVideoEditorExportEdited(source: PlatformFile, spec: PostVideoEditorExportSpec): PlatformFile {
    val hasTrim = spec.trimStartMs > 0L || spec.trimEndMs < spec.sourceDurationMs
    if (hasTrim || spec.removeAudio || spec.hasCrop || spec.hasCaptions) {
        error("ios_post_video_editor_native_export_required")
    }
    iosPostVideoEditorLocalUrl(source.reference) ?: error("ios_post_video_editor_source_invalid")
    return source
}

@OptIn(ExperimentalForeignApi::class)
private fun iosPostVideoEditorLocalUrl(reference: String): NSURL? {
    val value = reference.trim()
    return when {
        value.startsWith("file://") -> NSURL(string = value)
        value.startsWith("/") -> NSURL.fileURLWithPath(value)
        else -> null
    }?.takeIf { it.isFileURL() }
}
