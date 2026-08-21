package com.quata.feature.postcomposer.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.quata.core.captions.core.CaptionDocument
import com.quata.core.captions.core.CaptionDocumentWireCodec
import com.quata.core.captions.templates.CaptionTemplateStyle
import com.quata.core.media.QuataVideoExportPolicy
import com.quata.core.media.VideoExportProfile
import com.quata.core.platform.IosVideoThumbnailService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.feature.postcomposer.videoeditor.CaptionStyleOption
import com.quata.feature.postcomposer.videoeditor.DefaultPostVideoEditorExportProfile
import com.quata.feature.postcomposer.videoeditor.MaximumPostVideoEditorDurationMs
import com.quata.feature.postcomposer.videoeditor.PostVideoEditorDialogContent
import com.quata.feature.postcomposer.videoeditor.PostVideoEditorExportSpec
import com.quata.feature.postcomposer.videoeditor.PostVideoEditorUiState
import com.quata.feature.postcomposer.videoeditor.cropRect
import com.quata.feature.postcomposer.videoeditor.postVideoEditorExportSpec
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateForSourceDuration
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterCaptionsToggle
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterCropModeChange
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterCropPan
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterCropToggle
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterCropZoomChange
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterReset
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterTrimEnd
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterTrimStart
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIView
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface IosPostVideoEditorNativeDriver {
    fun createPreview(reference: String): IosPostVideoEditorPreviewSurface
    fun metadata(source: PlatformFile): IosPostVideoEditorMetadata?
    fun recommendedExportProfileLabel(): String?
    fun recordCaptionStyleChange(styleId: String?)
    fun transcribe(source: PlatformFile, callback: IosPostVideoEditorTranscriptCallback)
    fun export(source: PlatformFile, request: IosPostVideoEditorExportRequest, callback: IosPostVideoEditorExportCallback)
    fun cancelExport()
}

interface IosPostVideoEditorPreviewSurface {
    fun nativeView(): UIView
    fun configure(
        isPlaying: Boolean,
        isMuted: Boolean,
        positionMs: Long,
        trimStartMs: Long,
        trimEndMs: Long,
        durationMs: Long,
        videoAspectRatio: Float,
        cropLeft: Float,
        cropTop: Float,
        cropRight: Float,
        cropBottom: Float,
        cropVisible: Boolean,
        callback: IosPostVideoEditorPreviewCallback,
    )
    fun dispose()
}

interface IosPostVideoEditorPreviewCallback {
    fun onPositionMs(positionMs: Long)
}

interface IosPostVideoEditorExportCallback {
    fun onProgress(progress: Float)
    fun onSuccess(file: PlatformFile)
    fun onFailure(reason: String)
}

interface IosPostVideoEditorTranscriptCallback {
    fun onSuccess(text: String)
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
    val backgroundCropLeft: Float,
    val backgroundCropTop: Float,
    val backgroundCropRight: Float,
    val backgroundCropBottom: Float,
    val captionStyle: String?,
    val captionDocumentWire: String?,
    val outputWidth: Int,
    val outputHeight: Int,
    val outputMaxFrameRate: Int,
    val outputTargetBitrate: Int,
    val outputIntermediateBitrate: Int,
)

data class IosPostVideoEditorMetadata(
    val durationMs: Long,
    val width: Int,
    val height: Int,
) {
    val aspectRatio: Float get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 9f / 16f
}

object UnsupportedIosPostVideoEditorNativeDriver : IosPostVideoEditorNativeDriver {
    override fun createPreview(reference: String): IosPostVideoEditorPreviewSurface = UnsupportedIosPostVideoEditorPreviewSurface
    override fun metadata(source: PlatformFile): IosPostVideoEditorMetadata? = null
    override fun recommendedExportProfileLabel(): String? = null
    override fun recordCaptionStyleChange(styleId: String?) = Unit
    override fun transcribe(source: PlatformFile, callback: IosPostVideoEditorTranscriptCallback) {
        callback.onFailure("ios_post_video_editor_caption_transcript_missing")
    }

    override fun export(source: PlatformFile, request: IosPostVideoEditorExportRequest, callback: IosPostVideoEditorExportCallback) {
        callback.onFailure("ios_post_video_editor_native_export_required")
    }

    override fun cancelExport() = Unit
}

private object UnsupportedIosPostVideoEditorPreviewSurface : IosPostVideoEditorPreviewSurface {
    override fun nativeView(): UIView = UIView()
    override fun configure(
        isPlaying: Boolean,
        isMuted: Boolean,
        positionMs: Long,
        trimStartMs: Long,
        trimEndMs: Long,
        durationMs: Long,
        videoAspectRatio: Float,
        cropLeft: Float,
        cropTop: Float,
        cropRight: Float,
        cropBottom: Float,
        cropVisible: Boolean,
        callback: IosPostVideoEditorPreviewCallback,
    ) = Unit
    override fun dispose() = Unit
}

@Composable
internal fun IosPostVideoEditor(
    source: PlatformFile,
    nativeDriver: IosPostVideoEditorNativeDriver,
    isLandscapeLayout: Boolean,
    onDismiss: () -> Unit,
    onEdited: (PlatformFile) -> Unit,
) {
    var state by remember(source.reference) { mutableStateOf(PostVideoEditorUiState()) }
    var thumbnail by remember(source.reference) { mutableStateOf<PlatformFile?>(null) }
    var timelineFrames by remember(source.reference) { mutableStateOf<List<PlatformFile>>(emptyList()) }
    var metadata by remember(source.reference) { mutableStateOf<IosPostVideoEditorMetadata?>(null) }
    var metadataLoaded by remember(source.reference) { mutableStateOf(false) }
    var exportProfile by remember(source.reference) { mutableStateOf(DefaultPostVideoEditorExportProfile) }
    var exportJob by remember(source.reference) { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val durationMs = metadata?.durationMs?.takeIf { it > 0L } ?: MaximumPostVideoEditorDurationMs
    val videoAspectRatio = metadata?.aspectRatio ?: 9f / 16f
    LaunchedEffect(source.reference) {
        fun releaseGeneratedFrames() {
            iosComposerThumbnailToRelease(thumbnail)?.let(::releaseIosComposerVideoThumbnail)
            timelineFrames.forEach { frame ->
                iosComposerThumbnailToRelease(frame)?.let(::releaseIosComposerVideoThumbnail)
            }
            thumbnail = null
            timelineFrames = emptyList()
        }
        releaseGeneratedFrames()
        val loadedMetadata = nativeDriver.metadata(source)
        metadata = loadedMetadata
        metadataLoaded = true
        if (loadedMetadata == null) {
            state = state.copy(error = "ios_post_video_editor_metadata_unavailable")
        }
        loadedMetadata?.durationMs?.takeIf { it > 0L }?.let {
            state = postVideoEditorStateForSourceDuration(state, it)
        }
        loadedMetadata?.let {
            exportProfile = iosPostVideoEditorExportProfileFor(
                sourceProfile = QuataVideoExportPolicy.selectForSource(it.width, it.height),
                recommendedLabel = nativeDriver.recommendedExportProfileLabel(),
            )
        }
        val service = IosVideoThumbnailService()
        thumbnail = when (val result = service.createThumbnail(source, maxWidth = 720)) {
            is PlatformResult.Success -> result.value
            else -> null
        }
        val frameDurationSeconds = (loadedMetadata?.durationMs?.takeIf { it > 0L } ?: MaximumPostVideoEditorDurationMs).toDouble() / 1000.0
        timelineFrames = (0 until 6).mapNotNull { index ->
            val fraction = if (index == 0) 0.0 else index.toDouble() / 5.0
            val timeSeconds = (frameDurationSeconds * fraction).coerceAtMost((frameDurationSeconds - 0.05).coerceAtLeast(0.0))
            when (val result = service.createThumbnailAt(source, maxWidth = 240, requestedTimeSeconds = timeSeconds)) {
                is PlatformResult.Success -> result.value
                else -> null
            }
        }
    }
    DisposableEffect(source.reference) {
        onDispose {
            iosComposerThumbnailToRelease(thumbnail)?.let(::releaseIosComposerVideoThumbnail)
            timelineFrames.forEach { frame ->
                iosComposerThumbnailToRelease(frame)?.let(::releaseIosComposerVideoThumbnail)
            }
        }
    }
    fun export() {
        if (state.isExporting) return
        if (!metadataLoaded || metadata == null) {
            state = state.copy(
                error = if (metadataLoaded) {
                    "ios_post_video_editor_metadata_unavailable"
                } else {
                    "ios_post_video_editor_metadata_loading"
                },
            )
            return
        }
        state = state.copy(isExporting = true, exportProgress = 0.35f, error = null)
        val job = scope.launch {
            runCatching {
                val exportState = state
                val captionDocument = if (exportState.captionsEnabled && exportState.selectedCaptionStyleId != null) {
                    iosPostVideoEditorTranscribeCaptions(source, nativeDriver)
                } else {
                    null
                }
                val spec = postVideoEditorExportSpec(
                    exportState,
                    videoAspectRatio,
                    durationMs,
                    captionDocument,
                    exportProfile,
                )
                iosPostVideoEditorExportEdited(source, spec, nativeDriver) { progress ->
                    state = state.copy(exportProgress = progress.coerceIn(0.35f, 0.95f))
                }
            }
                .onSuccess {
                    state = state.copy(isExporting = false, exportProgress = 1f)
                    onEdited(it)
                }
                .onFailure {
                    if (it is CancellationException) return@onFailure
                    state = state.copy(isExporting = false, error = it.message ?: "ios_post_video_editor_export_failed")
                }
        }
        exportJob = job
        job.invokeOnCompletion { exportJob = null }
    }

    PostVideoEditorDialogContent(
        state = state,
        onMutedChange = { state = state.copy(isMuted = it) },
        onPlayPause = { state = state.copy(isPlaying = !state.isPlaying) },
        onTrimStartChange = { state = postVideoEditorStateAfterTrimStart(state, it, durationMs) },
        onTrimEndChange = { state = postVideoEditorStateAfterTrimEnd(state, it, durationMs) },
        onCropToggle = { state = postVideoEditorStateAfterCropToggle(state) },
        onCaptionsToggle = {
            val nextState = postVideoEditorStateAfterCaptionsToggle(state)
            if (!state.captionsEnabled && nextState.selectedCaptionStyleId != null) {
                nativeDriver.recordCaptionStyleChange(nextState.selectedCaptionStyleId)
            }
            state = nextState
        },
        onReset = { state = postVideoEditorStateAfterReset(state, durationMs) },
        onDismiss = onDismiss,
        onExport = ::export,
        onCancelExport = {
            exportJob?.cancel()
            nativeDriver.cancelExport()
            state = state.copy(isExporting = false, exportProgress = 0f)
        },
        isLandscapeLayout = isLandscapeLayout,
        durationMs = durationMs,
        captionOptions = CaptionTemplateStyle.entries.map { CaptionStyleOption(it.name, it.name) },
        onCropModeChange = { state = postVideoEditorStateAfterCropModeChange(state, it, videoAspectRatio) },
        onCropZoomChange = { state = postVideoEditorStateAfterCropZoomChange(state, it, videoAspectRatio) },
        onCropPanChange = { dx, dy -> state = postVideoEditorStateAfterCropPan(state, dx, dy, videoAspectRatio) },
        onCaptionStyleChange = {
            nativeDriver.recordCaptionStyleChange(it)
            state = state.copy(selectedCaptionStyleId = it, captionsEnabled = it != null)
        },
        onSeekChange = { state = state.copy(currentPositionFraction = it.coerceIn(0f, 1f)) },
        timelineFrameCount = timelineFrames.size,
        timelineFrameContent = { index, frameModifier ->
            timelineFrames.getOrNull(index)?.let { frame ->
                IosComposerLocalImagePreview(frame, modifier = frameModifier)
            } ?: androidx.compose.foundation.layout.Box(frameModifier)
        },
        preview = { modifier: Modifier ->
            IosPostVideoEditorNativePreview(
                source = source,
                nativeDriver = nativeDriver,
                state = state,
                durationMs = durationMs,
                videoAspectRatio = videoAspectRatio,
                fallbackThumbnail = thumbnail,
                onPositionFractionChange = { nextFraction ->
                    if (kotlin.math.abs(nextFraction - state.currentPositionFraction) > 0.002f) {
                        state = state.copy(currentPositionFraction = nextFraction)
                    }
                },
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
    videoAspectRatio: Float,
    fallbackThumbnail: PlatformFile?,
    onPositionFractionChange: (Float) -> Unit,
    modifier: Modifier,
) {
    val localReference = iosPostVideoEditorLocalUrl(source.reference)?.absoluteString
    if (localReference == null) {
        fallbackThumbnail?.let { IosComposerLocalImagePreview(it, modifier = modifier) }
            ?: IosComposerLocalImagePreview(source, modifier = modifier)
        return
    }
    val surface = remember(localReference, nativeDriver) { nativeDriver.createPreview(localReference) }
    val cropRect = state.cropMode.cropRect(
        videoAspectRatio,
        state.cropZoom,
        androidx.compose.ui.geometry.Offset(state.cropCenterX, state.cropCenterY),
    )
    androidx.compose.runtime.DisposableEffect(surface) { onDispose(surface::dispose) }
    UIKitView(
        factory = surface::nativeView,
        update = {
            surface.configure(
                isPlaying = state.isPlaying,
                isMuted = state.isMuted,
                positionMs = (state.currentPositionFraction.coerceIn(0f, 1f) * durationMs).toLong(),
                trimStartMs = (state.trimStartFraction.coerceIn(0f, 1f) * durationMs).toLong(),
                trimEndMs = (state.trimEndFraction.coerceIn(0f, 1f) * durationMs).toLong(),
                durationMs = durationMs,
                videoAspectRatio = videoAspectRatio,
                cropLeft = cropRect.left,
                cropTop = cropRect.top,
                cropRight = cropRect.right,
                cropBottom = cropRect.bottom,
                cropVisible = state.cropPanelOpen && state.cropMode != com.quata.feature.postcomposer.videoeditor.VideoCropMode.Original,
                callback = object : IosPostVideoEditorPreviewCallback {
                    override fun onPositionMs(positionMs: Long) {
                        val bounded = positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L))
                        val nextFraction = bounded.toFloat() / durationMs.coerceAtLeast(1L).toFloat()
                        if (kotlin.math.abs(nextFraction - state.currentPositionFraction) > 0.002f) {
                            onPositionFractionChange(nextFraction)
                        }
                    }
                },
            )
        },
        modifier = modifier,
    )
}

private fun iosPostVideoEditorExportProfileFor(
    sourceProfile: VideoExportProfile,
    recommendedLabel: String?,
): VideoExportProfile {
    val recommended = when (recommendedLabel) {
        QuataVideoExportPolicy.sd480Simulator.label -> QuataVideoExportPolicy.sd480Simulator
        QuataVideoExportPolicy.sd432Aligned.label -> QuataVideoExportPolicy.sd432Aligned
        QuataVideoExportPolicy.conservativeProfile.label -> QuataVideoExportPolicy.conservativeProfile
        QuataVideoExportPolicy.defaultProfile.label -> QuataVideoExportPolicy.defaultProfile
        else -> null
    } ?: return sourceProfile
    return if (recommended.width * recommended.height < sourceProfile.width * sourceProfile.height) {
        recommended
    } else {
        sourceProfile
    }
}

private suspend fun iosPostVideoEditorTranscribeCaptions(
    source: PlatformFile,
    nativeDriver: IosPostVideoEditorNativeDriver,
): CaptionDocument = suspendCancellableCoroutine { continuation ->
    nativeDriver.transcribe(source, object : IosPostVideoEditorTranscriptCallback {
        override fun onSuccess(text: String) {
            val document = CaptionDocument.fromWords(CaptionDocumentWireCodec.decodeWords(text))
            if (continuation.isActive) {
                if (!document.isEmpty) {
                    continuation.resume(document)
                } else {
                    continuation.resumeWithException(IllegalStateException("ios_post_video_editor_caption_transcript_missing"))
                }
            }
        }

        override fun onFailure(reason: String) {
            if (continuation.isActive) continuation.resumeWithException(IllegalStateException(reason))
        }
    })
}

private suspend fun iosPostVideoEditorExportEdited(
    source: PlatformFile,
    spec: PostVideoEditorExportSpec,
    nativeDriver: IosPostVideoEditorNativeDriver,
    onProgress: (Float) -> Unit = {},
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
        backgroundCropLeft = spec.backgroundCropRect?.left ?: 0f,
        backgroundCropTop = spec.backgroundCropRect?.top ?: 0f,
        backgroundCropRight = spec.backgroundCropRect?.right ?: 1f,
        backgroundCropBottom = spec.backgroundCropRect?.bottom ?: 1f,
        captionStyle = spec.captionStyle?.name,
        captionDocumentWire = spec.captionDocument?.let(CaptionDocumentWireCodec::encodeDocument),
        outputWidth = spec.outputWidth,
        outputHeight = spec.outputHeight,
        outputMaxFrameRate = spec.outputMaxFrameRate,
        outputTargetBitrate = spec.outputTargetBitrate,
        outputIntermediateBitrate = spec.outputIntermediateBitrate,
    )
    nativeDriver.export(source, request, object : IosPostVideoEditorExportCallback {
        override fun onProgress(progress: Float) {
            if (continuation.isActive) onProgress(progress)
        }

        override fun onSuccess(file: PlatformFile) {
            if (continuation.isActive) continuation.resume(file)
        }

        override fun onFailure(reason: String) {
            if (continuation.isActive) continuation.resumeWithException(IllegalStateException(reason))
        }
    })
    continuation.invokeOnCancellation { nativeDriver.cancelExport() }
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
