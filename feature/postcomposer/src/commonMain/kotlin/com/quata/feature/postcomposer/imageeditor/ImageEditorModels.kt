package com.quata.feature.postcomposer.imageeditor

enum class QuataImageEditorMode { Post, Avatar }

data class ImageEditorOutputSpec(val width: Int, val height: Int) {
    val aspectRatio: Float = width.toFloat() / height.toFloat()
}

val ImageEditorPostOutputSpec = ImageEditorOutputSpec(width = 1080, height = 1920)
val ImageEditorAvatarOutputSpec = ImageEditorOutputSpec(width = 1080, height = 1080)

/**
 * Platform-neutral editing state for the locked square avatar crop.  The actual bitmap decode
 * and export remain platform work, but keeping this state common makes the editor contract the
 * same on Android, iOS and Web/Wasm.
 *
 * [panX] and [panY] are normalized against the available overflow in the output frame: -1 and
 * 1 mean the edge of the enlarged image respectively.  [quarterTurns] is always normalized.
 */
data class AvatarImageEditorTransform(
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val quarterTurns: Int = 0,
) {
    init {
        require(zoom >= MinimumAvatarEditorZoom)
        require(panX in -1f..1f)
        require(panY in -1f..1f)
        require(quarterTurns in 0..3)
    }

    fun withZoom(value: Float) = copy(zoom = value.coerceIn(MinimumAvatarEditorZoom, MaximumAvatarEditorZoom))
    fun withPan(x: Float, y: Float) = copy(panX = x.coerceIn(-1f, 1f), panY = y.coerceIn(-1f, 1f))
    fun rotateClockwise() = copy(quarterTurns = (quarterTurns + 1) % 4)

    companion object { val Default = AvatarImageEditorTransform() }
}

const val MinimumAvatarEditorZoom = 1f
const val MaximumAvatarEditorZoom = 4f
