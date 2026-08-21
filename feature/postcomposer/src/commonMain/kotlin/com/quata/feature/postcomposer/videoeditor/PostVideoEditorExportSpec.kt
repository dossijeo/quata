package com.quata.feature.postcomposer.videoeditor

import androidx.compose.ui.geometry.Offset
import com.quata.core.captions.core.CaptionDocument
import com.quata.core.captions.templates.CaptionTemplateStyle
import com.quata.core.media.QuataVideoExportPolicy
import com.quata.core.media.VideoExportProfile
import kotlin.math.roundToLong

data class PostVideoEditorExportSpec(
    val trimStartMs: Long,
    val trimEndMs: Long,
    val sourceDurationMs: Long,
    val removeAudio: Boolean,
    val cropRect: NormalizedCropRect,
    val backgroundCropRect: NormalizedCropRect?,
    val captionStyle: CaptionTemplateStyle?,
    val captionDocument: CaptionDocument?,
    val exportProfile: VideoExportProfile = QuataVideoExportPolicy.defaultProfile,
) {
    val trimDurationMs: Long get() = (trimEndMs - trimStartMs).coerceAtLeast(MinimumPostVideoEditorTrimMs)
    val hasCrop: Boolean get() = !cropRect.isFullFrame || backgroundCropRect != null
    val hasCaptions: Boolean get() = captionStyle != null && captionDocument?.isEmpty == false
    val outputWidth: Int get() = exportProfile.width
    val outputHeight: Int get() = exportProfile.height
    val outputMaxFrameRate: Int get() = exportProfile.maxFrameRate
    val outputTargetBitrate: Int get() = exportProfile.targetBitrate
    val outputIntermediateBitrate: Int get() = exportProfile.intermediateBitrate
}

fun postVideoEditorExportSpec(
    state: PostVideoEditorUiState,
    videoAspectRatio: Float,
    durationMs: Long,
    captionDocument: CaptionDocument? = null,
    exportProfile: VideoExportProfile = QuataVideoExportPolicy.defaultProfile,
): PostVideoEditorExportSpec {
    val safeDuration = durationMs.coerceAtLeast(1L)
    val requestedStartMs = (state.trimStartFraction.coerceIn(0f, 1f) * safeDuration).roundToLong()
        .coerceIn(0L, safeDuration)
    val requestedEndMs = (state.trimEndFraction.coerceIn(0f, 1f) * safeDuration).roundToLong()
        .coerceAtLeast(requestedStartMs + MinimumPostVideoEditorTrimMs)
        .coerceAtMost(safeDuration)
    val trimStartMs = if (requestedEndMs - requestedStartMs > MaximumPostVideoEditorDurationMs) {
        (requestedEndMs - MaximumPostVideoEditorDurationMs).coerceAtLeast(0L)
    } else {
        requestedStartMs
    }.coerceAtMost((requestedEndMs - MinimumPostVideoEditorTrimMs).coerceAtLeast(0L))
    val trimEndMs = requestedEndMs
        .coerceAtLeast(trimStartMs + MinimumPostVideoEditorTrimMs)
        .coerceAtMost(safeDuration)
    val cropCenter = Offset(state.cropCenterX, state.cropCenterY)
    val cropRect = state.cropMode.cropRect(videoAspectRatio, state.cropZoom, cropCenter)
    val foregroundCrop = cropRect.takeIf { state.cropEnabled && !it.isFullFrame } ?: NormalizedCropRect.Full
    val backgroundCrop = if (!state.cropEnabled && cropRect.isFullFrame && isNineSixteenAspect(videoAspectRatio)) {
        null
    } else {
        cropRect
            .centerCropToAspect(PostVideoEditorOutputAspectRatio, videoAspectRatio)
            .takeUnless { it.isFullFrame }
    }
    val selectedCaptionStyle = state.selectedCaptionStyleId
        ?.let { id -> CaptionTemplateStyle.entries.firstOrNull { it.name == id } }
        ?.takeIf { state.captionsEnabled }
    return PostVideoEditorExportSpec(
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        sourceDurationMs = safeDuration,
        removeAudio = state.isMuted,
        cropRect = foregroundCrop,
        backgroundCropRect = backgroundCrop,
        captionStyle = selectedCaptionStyle,
        captionDocument = captionDocument
            ?.trimTo(trimStartMs, trimEndMs)
            ?.takeIf { !it.isEmpty }
            ?.takeIf { selectedCaptionStyle != null },
        exportProfile = exportProfile,
    )
}

fun postVideoEditorStateAfterTrimStart(
    state: PostVideoEditorUiState,
    fraction: Float,
    durationMs: Long = MaximumPostVideoEditorDurationMs,
): PostVideoEditorUiState {
    val minimumFraction = postVideoEditorMinimumTrimFraction(durationMs)
    val maximumFraction = postVideoEditorMaximumTrimFraction(durationMs)
    val end = state.trimEndFraction.coerceIn(minimumFraction, 1f)
    val start = fraction.coerceIn(0f, end - minimumFraction)
    val normalizedEnd = end.coerceAtMost(start + maximumFraction).coerceAtLeast(start + minimumFraction)
    return state.copy(
        trimStartFraction = start,
        trimEndFraction = normalizedEnd.coerceAtMost(1f),
    )
}

fun postVideoEditorStateAfterTrimEnd(
    state: PostVideoEditorUiState,
    fraction: Float,
    durationMs: Long = MaximumPostVideoEditorDurationMs,
): PostVideoEditorUiState {
    val minimumFraction = postVideoEditorMinimumTrimFraction(durationMs)
    val maximumFraction = postVideoEditorMaximumTrimFraction(durationMs)
    val end = fraction.coerceIn(minimumFraction, 1f)
    val currentStart = state.trimStartFraction.coerceIn(0f, end - minimumFraction)
    val start = if (end - currentStart > maximumFraction) {
        (end - maximumFraction).coerceAtLeast(0f)
    } else {
        currentStart
    }.coerceAtMost(end - minimumFraction)
    return state.copy(
        trimStartFraction = start,
        trimEndFraction = end.coerceAtLeast(start + minimumFraction),
    )
}

fun postVideoEditorMinimumTrimFraction(durationMs: Long): Float =
    (MinimumPostVideoEditorTrimMs.toFloat() / durationMs.coerceAtLeast(MinimumPostVideoEditorTrimMs).toFloat())
        .coerceIn(0.001f, 1f)

fun postVideoEditorMaximumTrimFraction(durationMs: Long): Float =
    (MaximumPostVideoEditorDurationMs.toFloat() / durationMs.coerceAtLeast(MaximumPostVideoEditorDurationMs).toFloat())
        .coerceIn(postVideoEditorMinimumTrimFraction(durationMs), 1f)

fun postVideoEditorStateForSourceDuration(
    state: PostVideoEditorUiState,
    durationMs: Long,
): PostVideoEditorUiState {
    val minimumFraction = postVideoEditorMinimumTrimFraction(durationMs)
    val maximumFraction = postVideoEditorMaximumTrimFraction(durationMs)
    val start = state.trimStartFraction.coerceIn(0f, 1f - minimumFraction)
    val end = state.trimEndFraction
        .coerceAtLeast(start + minimumFraction)
        .coerceAtMost(start + maximumFraction)
        .coerceAtMost(1f)
    return state.copy(
        trimStartFraction = start,
        trimEndFraction = end,
        currentPositionFraction = state.currentPositionFraction.coerceIn(start, end),
    )
}

fun postVideoEditorStateAfterCropToggle(state: PostVideoEditorUiState): PostVideoEditorUiState =
    state.copy(
        cropEnabled = true,
        cropPanelOpen = !state.cropPanelOpen,
        captionsPanelOpen = false,
    )

fun postVideoEditorStateAfterCaptionsToggle(state: PostVideoEditorUiState): PostVideoEditorUiState =
    state.copy(
        captionsEnabled = true,
        captionsPanelOpen = !state.captionsPanelOpen,
        cropPanelOpen = false,
        selectedCaptionStyleId = state.selectedCaptionStyleId ?: DefaultPostVideoEditorCaptionStyleId,
    )

fun postVideoEditorStateAfterCropModeChange(
    state: PostVideoEditorUiState,
    mode: VideoCropMode,
    videoAspectRatio: Float,
): PostVideoEditorUiState {
    val center = mode.clampCenter(videoAspectRatio, 1f, Offset(0.5f, 0.5f))
    return state.copy(
        cropMode = mode,
        cropEnabled = mode != VideoCropMode.Original,
        cropZoom = 1f,
        cropCenterX = center.x,
        cropCenterY = center.y,
    )
}

fun postVideoEditorStateAfterCropZoomChange(
    state: PostVideoEditorUiState,
    zoom: Float,
    videoAspectRatio: Float,
): PostVideoEditorUiState {
    val nextZoom = zoom.coerceIn(1f, 3f)
    val center = state.cropMode.clampCenter(
        videoAspectRatio,
        nextZoom,
        Offset(state.cropCenterX, state.cropCenterY),
    )
    return state.copy(
        cropZoom = nextZoom,
        cropEnabled = state.cropMode != VideoCropMode.Original,
        cropCenterX = center.x,
        cropCenterY = center.y,
    )
}

fun postVideoEditorStateAfterCropPan(
    state: PostVideoEditorUiState,
    deltaX: Float,
    deltaY: Float,
    videoAspectRatio: Float,
): PostVideoEditorUiState {
    val center = state.cropMode.clampCenter(
        videoAspectRatio,
        state.cropZoom,
        Offset(state.cropCenterX + deltaX, state.cropCenterY + deltaY),
    )
    return state.copy(
        cropEnabled = state.cropMode != VideoCropMode.Original,
        cropCenterX = center.x,
        cropCenterY = center.y,
    )
}

fun postVideoEditorStateAfterReset(
    state: PostVideoEditorUiState,
    durationMs: Long = MaximumPostVideoEditorDurationMs,
): PostVideoEditorUiState {
    val maximumFraction = postVideoEditorMaximumTrimFraction(durationMs)
    return state.copy(
        isMuted = false,
        cropEnabled = false,
        captionsEnabled = false,
        cropPanelOpen = false,
        captionsPanelOpen = false,
        cropMode = VideoCropMode.Original,
        cropZoom = 1f,
        cropCenterX = 0.5f,
        cropCenterY = 0.5f,
        trimStartFraction = 0f,
        trimEndFraction = maximumFraction,
        currentPositionFraction = 0f,
        selectedCaptionStyleId = null,
        error = null,
    )
}

private fun isNineSixteenAspect(aspectRatio: Float): Boolean =
    kotlin.math.abs(aspectRatio - PostVideoEditorOutputAspectRatio) <= 0.01f

const val MinimumPostVideoEditorTrimMs = QuataVideoExportPolicy.MinimumTrimMs
const val MaximumPostVideoEditorDurationMs = QuataVideoExportPolicy.MaximumDurationMs
val DefaultPostVideoEditorExportProfile: VideoExportProfile = QuataVideoExportPolicy.defaultProfile
val ConservativePostVideoEditorExportProfile: VideoExportProfile = QuataVideoExportPolicy.conservativeProfile
val PostVideoEditorOutputAspectRatio: Float = DefaultPostVideoEditorExportProfile.aspectRatio

val DefaultPostVideoEditorCaptionStyleId: String = CaptionTemplateStyle.entries.first().name
