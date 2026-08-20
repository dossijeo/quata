package com.quata.feature.postcomposer.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
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
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIView
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface IosPostVideoEditorNativeDriver {
    fun createPreview(reference: String): IosPostVideoEditorPreviewSurface
    fun export(source: PlatformFile, request: IosPostVideoEditorExportRequest, callback: IosPostVideoEditorExportCallback)
}

interface IosPostVideoEditorPreviewSurface {
    fun nativeView(): UIView
    fun configure(isPlaying: Boolean, isMuted: Boolean, positionMs: Long)
    fun dispose()
}

interface IosPostVideoEditorExportCallback {
    fun onSuccess(file: PlatformFile)
    fun onFailure(reason: String)
}

data class IosPostVideoEditorExportRequest(
    val trimStartMs: Long,
    val trimEndMs: Long,
    val sourceDurationMs: Long,
    val removeAudio: Boolean,
    val cropLeft: Float,
    val cropTop: Float,
    val cropRight: Float,
    val cropBottom: Float,
    val captionStyle: String?,
    val outputWidth: Int,
    val outputHeight: Int,
)

object UnsupportedIosPostVideoEditorNativeDriver : IosPostVideoEditorNativeDriver {
    override fun createPreview(reference: String): IosPostVideoEditorPreviewSurface = UnsupportedIosPostVideoEditorPreviewSurface
    override fun export(source: PlatformFile, request: IosPostVideoEditorExportRequest, callback: IosPostVideoEditorExportCallback) {
        callback.onFailure("ios_post_video_editor_native_export_required")
    }
}

private object UnsupportedIosPostVideoEditorPreviewSurface : IosPostVideoEditorPreviewSurface {
    override fun nativeView(): UIView = UIView()
    override fun configure(isPlaying: Boolean, isMuted: Boolean, positionMs: Long) = Unit
    override fun dispose() = Unit
}

@Composable
internal fun IosPostVideoEditor(
    source: PlatformFile,
    nativeDriver: IosPostVideoEditorNativeDriver,
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
            runCatching { iosPostVideoEditorExportEdited(source, spec, nativeDriver) }
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
            IosPostVideoEditorNativePreview(
                source = source,
                nativeDriver = nativeDriver,
                state = state,
                durationMs = durationMs,
                fallbackThumbnail = thumbnail,
                modifier = modifier,
            )
        },
    )
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun IosPostVideoEditorNativePreview(
    source: PlatformFile,
    nativeDriver: IosPostVideoEditorNativeDriver,
    state: PostVideoEditorUiState,
    durationMs: Long,
    fallbackThumbnail: PlatformFile?,
    modifier: Modifier,
) {
    val localReference = iosPostVideoEditorLocalUrl(source.reference)?.absoluteString
    if (localReference == null) {
        fallbackThumbnail?.let { IosComposerLocalImagePreview(it, modifier = modifier) }
            ?: IosComposerLocalImagePreview(source, modifier = modifier)
        return
    }
    val surface = remember(localReference, nativeDriver) { nativeDriver.createPreview(localReference) }
    androidx.compose.runtime.DisposableEffect(surface) { onDispose(surface::dispose) }
    UIKitView(
        factory = surface::nativeView,
        update = {
            surface.configure(
                isPlaying = state.isPlaying,
                isMuted = state.isMuted,
                positionMs = (state.currentPositionFraction.coerceIn(0f, 1f) * durationMs).toLong(),
            )
        },
        modifier = modifier,
    )
}

private suspend fun iosPostVideoEditorExportEdited(
    source: PlatformFile,
    spec: PostVideoEditorExportSpec,
    nativeDriver: IosPostVideoEditorNativeDriver,
): PlatformFile = suspendCancellableCoroutine { continuation ->
    val request = IosPostVideoEditorExportRequest(
        trimStartMs = spec.trimStartMs,
        trimEndMs = spec.trimEndMs,
        sourceDurationMs = spec.sourceDurationMs,
        removeAudio = spec.removeAudio,
        cropLeft = spec.cropRect.left,
        cropTop = spec.cropRect.top,
        cropRight = spec.cropRect.right,
        cropBottom = spec.cropRect.bottom,
        captionStyle = spec.captionStyle?.name,
        outputWidth = spec.outputWidth,
        outputHeight = spec.outputHeight,
    )
    nativeDriver.export(source, request, object : IosPostVideoEditorExportCallback {
        override fun onSuccess(file: PlatformFile) {
            if (continuation.isActive) continuation.resume(file)
        }

        override fun onFailure(reason: String) {
            if (continuation.isActive) continuation.resumeWithException(IllegalStateException(reason))
        }
    })
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
