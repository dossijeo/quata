package com.quata.feature.postcomposer.videoeditor

import com.quata.core.captions.templates.CaptionTemplateStyle
import kotlin.math.roundToLong

data class PostVideoEditorExportSpec(
    val trimStartMs: Long,
    val trimEndMs: Long,
    val sourceDurationMs: Long,
    val removeAudio: Boolean,
    val cropRect: NormalizedCropRect,
    val backgroundCropRect: NormalizedCropRect?,
    val captionStyle: CaptionTemplateStyle?,
    val outputWidth: Int = PostVideoEditorOutputWidth,
    val outputHeight: Int = PostVideoEditorOutputHeight,
) {
    val trimDurationMs: Long get() = (trimEndMs - trimStartMs).coerceAtLeast(MinimumPostVideoEditorTrimMs)
    val hasCrop: Boolean get() = !cropRect.isFullFrame || backgroundCropRect != null
    val hasCaptions: Boolean get() = captionStyle != null
}

fun postVideoEditorExportSpec(
    state: PostVideoEditorUiState,
    videoAspectRatio: Float,
    durationMs: Long,
): PostVideoEditorExportSpec {
    val safeDuration = durationMs.coerceAtLeast(1L)
    val trimStartMs = (state.trimStartFraction.coerceIn(0f, 1f) * safeDuration).roundToLong()
    val trimEndMs = (state.trimEndFraction.coerceIn(0f, 1f) * safeDuration).roundToLong()
        .coerceAtLeast(trimStartMs + MinimumPostVideoEditorTrimMs)
        .coerceAtMost(safeDuration)
    val cropCenter = androidx.compose.ui.geometry.Offset(state.cropCenterX, state.cropCenterY)
    val cropRect = state.cropMode.cropRect(videoAspectRatio, state.cropZoom, cropCenter)
    val foregroundCrop = cropRect.takeIf { state.cropEnabled && !it.isFullFrame } ?: NormalizedCropRect.Full
    val backgroundCrop = if (!state.cropEnabled && cropRect.isFullFrame && isNineSixteenAspect(videoAspectRatio)) {
        null
    } else {
        cropRect
            .centerCropToAspect(PostVideoEditorOutputAspectRatio, videoAspectRatio)
            .takeUnless { it.isFullFrame }
    }
    return PostVideoEditorExportSpec(
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        sourceDurationMs = safeDuration,
        removeAudio = state.isMuted,
        cropRect = foregroundCrop,
        backgroundCropRect = backgroundCrop,
        captionStyle = state.selectedCaptionStyleId
            ?.let { id -> CaptionTemplateStyle.entries.firstOrNull { it.name == id } }
            ?.takeIf { state.captionsEnabled },
    )
}

fun postVideoEditorStateAfterTrimStart(
    state: PostVideoEditorUiState,
    fraction: Float,
): PostVideoEditorUiState {
    val end = state.trimEndFraction.coerceIn(0.05f, 1f)
    return state.copy(trimStartFraction = fraction.coerceIn(0f, end - 0.05f))
}

fun postVideoEditorStateAfterTrimEnd(
    state: PostVideoEditorUiState,
    fraction: Float,
): PostVideoEditorUiState {
    val start = state.trimStartFraction.coerceIn(0f, 0.95f)
    return state.copy(trimEndFraction = fraction.coerceIn(start + 0.05f, 1f))
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
    )

private fun isNineSixteenAspect(aspectRatio: Float): Boolean =
    kotlin.math.abs(aspectRatio - PostVideoEditorOutputAspectRatio) <= 0.01f

const val MinimumPostVideoEditorTrimMs = 500L
const val MaximumPostVideoEditorDurationMs = 90_000L
const val PostVideoEditorOutputWidth = 1080
const val PostVideoEditorOutputHeight = 1920
const val PostVideoEditorOutputAspectRatio = 9f / 16f
