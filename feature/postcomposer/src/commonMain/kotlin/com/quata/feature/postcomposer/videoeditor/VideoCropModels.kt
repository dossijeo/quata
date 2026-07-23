package com.quata.feature.postcomposer.videoeditor

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

enum class VideoCropMode(val targetAspect: Float?) {
    Original(null), Square(1f), FourFive(4f / 5f), Portrait(9f / 16f), Landscape(16f / 9f)
}

data class NormalizedCropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isFullFrame: Boolean get() = left <= 0.001f && top <= 0.001f && right >= 0.999f && bottom >= 0.999f
    companion object { val Full = NormalizedCropRect(0f, 0f, 1f, 1f) }
}

fun NormalizedCropRect.displayAspectRatio(sourceAspectRatio: Float): Float =
    (sourceAspectRatio * width.coerceAtLeast(0.01f) / height.coerceAtLeast(0.01f)).coerceAtLeast(0.1f)

fun NormalizedCropRect.centerCropToAspect(targetAspectRatio: Float, sourceAspectRatio: Float): NormalizedCropRect {
    val safeSourceAspect = sourceAspectRatio.coerceAtLeast(0.1f)
    val safeTargetAspect = targetAspectRatio.coerceAtLeast(0.1f)
    val cropAspect = displayAspectRatio(safeSourceAspect)
    var nextWidth = width
    var nextHeight = height
    if (cropAspect > safeTargetAspect) nextWidth = (safeTargetAspect * nextHeight / safeSourceAspect).coerceAtMost(width)
    else if (cropAspect < safeTargetAspect) nextHeight = (safeSourceAspect * nextWidth / safeTargetAspect).coerceAtMost(height)
    val center = clampCropCenter(nextWidth, nextHeight, Offset((left + right) / 2f, (top + bottom) / 2f))
    return NormalizedCropRect(center.x - nextWidth / 2f, center.y - nextHeight / 2f, center.x + nextWidth / 2f, center.y + nextHeight / 2f)
}

fun VideoCropMode.cropRect(videoAspect: Float, zoom: Float, center: Offset): NormalizedCropRect {
    val aspect = targetAspect ?: return NormalizedCropRect.Full
    val safeVideoAspect = videoAspect.coerceAtLeast(0.1f)
    var width = 1f
    var height = safeVideoAspect / aspect
    if (height > 1f) { height = 1f; width = aspect / safeVideoAspect }
    val safeZoom = zoom.coerceIn(1f, 3f)
    width = (width / safeZoom).coerceIn(0.12f, 1f)
    height = (height / safeZoom).coerceIn(0.12f, 1f)
    val clamped = clampCropCenter(width, height, center)
    return NormalizedCropRect(clamped.x - width / 2f, clamped.y - height / 2f, clamped.x + width / 2f, clamped.y + height / 2f)
}

fun VideoCropMode.clampCenter(videoAspect: Float, zoom: Float, center: Offset): Offset {
    val rect = cropRect(videoAspect, zoom, Offset(0.5f, 0.5f))
    return clampCropCenter(rect.width, rect.height, center)
}

fun clampCropCenter(width: Float, height: Float, center: Offset): Offset = Offset(
    x = center.x.coerceIn(width / 2f, 1f - width / 2f),
    y = center.y.coerceIn(height / 2f, 1f - height / 2f)
)

fun Int.normalizedVideoRotation(): Int {
    val normalized = ((this % 360) + 360) % 360
    return if (normalized == 90 || normalized == 180 || normalized == 270) normalized else 0
}

data class VideoEditorMetadata(
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val rotation: Int = 0,
    val bitrate: Long? = null,
    val frameRate: Float? = null
) {
    val displayWidth: Int get() = if (rotation.normalizedVideoRotation() in setOf(90, 270)) height else width
    val displayHeight: Int get() = if (rotation.normalizedVideoRotation() in setOf(90, 270)) width else height
    val aspectRatio: Float? get() = if (displayWidth <= 0 || displayHeight <= 0) null else displayWidth.toFloat() / displayHeight.toFloat()
    fun hasNineSixteenAspect(): Boolean = aspectRatio?.let { abs(it - 9f / 16f) <= 0.01f } == true
}
