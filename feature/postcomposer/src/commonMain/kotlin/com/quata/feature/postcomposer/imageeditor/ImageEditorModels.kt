package com.quata.feature.postcomposer.imageeditor

enum class QuataImageEditorMode { Post, Avatar }

data class ImageEditorOutputSpec(val width: Int, val height: Int) {
    val aspectRatio: Float = width.toFloat() / height.toFloat()
}

val ImageEditorPostOutputSpec = ImageEditorOutputSpec(width = 1080, height = 1920)
val ImageEditorAvatarOutputSpec = ImageEditorOutputSpec(width = 1080, height = 1080)

const val PostImageEditorRootTestTag = "post-image-editor.root"
const val PostImageEditorPreviewTestTag = "post-image-editor.preview"
const val PostImageEditorCancelTestTag = "post-image-editor.cancel"
const val PostImageEditorResetTestTag = "post-image-editor.reset"
const val PostImageEditorRotateTestTag = "post-image-editor.rotate"
const val PostImageEditorCropTestTag = "post-image-editor.crop"
const val PostImageEditorSaveTestTag = "post-image-editor.save"

/**
 * Platform-neutral editing state for post images. Bitmap decode/export remain platform work, but
 * this transform and geometry contract keep crop, zoom and rotation decisions aligned.
 */
data class PostImageEditorTransform(
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val quarterTurns: Int = 0,
) {
    init {
        require(zoom >= MinimumPostImageEditorZoom)
        require(panX in -1f..1f)
        require(panY in -1f..1f)
        require(quarterTurns in 0..3)
    }

    fun withZoom(value: Float) = copy(zoom = value.coerceIn(MinimumPostImageEditorZoom, MaximumPostImageEditorZoom))
    fun withPan(x: Float, y: Float) = copy(panX = x.coerceIn(-1f, 1f), panY = y.coerceIn(-1f, 1f))
    fun rotateClockwise() = copy(quarterTurns = (quarterTurns + 1) % 4)

    companion object { val Default = PostImageEditorTransform() }
}

data class PostImageEditorGeometry(
    val scale: Float,
    val sourceDrawnWidth: Float,
    val sourceDrawnHeight: Float,
    val outputDrawnWidth: Float,
    val outputDrawnHeight: Float,
    val maxPanX: Float,
    val maxPanY: Float,
)

fun postImageEditorGeometry(
    sourceWidth: Int,
    sourceHeight: Int,
    transform: PostImageEditorTransform,
    outputSpec: ImageEditorOutputSpec = ImageEditorPostOutputSpec,
): PostImageEditorGeometry {
    require(sourceWidth > 0 && sourceHeight > 0)
    require(outputSpec.width > 0 && outputSpec.height > 0)
    val scale = maxOf(outputSpec.width.toFloat() / sourceWidth, outputSpec.height.toFloat() / sourceHeight) * transform.zoom
    val sourceDrawnWidth = sourceWidth * scale
    val sourceDrawnHeight = sourceHeight * scale
    val isQuarterTurn = transform.quarterTurns % 2 != 0
    val outputDrawnWidth = if (isQuarterTurn) sourceDrawnHeight else sourceDrawnWidth
    val outputDrawnHeight = if (isQuarterTurn) sourceDrawnWidth else sourceDrawnHeight
    return PostImageEditorGeometry(
        scale = scale,
        sourceDrawnWidth = sourceDrawnWidth,
        sourceDrawnHeight = sourceDrawnHeight,
        outputDrawnWidth = outputDrawnWidth,
        outputDrawnHeight = outputDrawnHeight,
        maxPanX = ((outputDrawnWidth - outputSpec.width) / 2f).coerceAtLeast(0f),
        maxPanY = ((outputDrawnHeight - outputSpec.height) / 2f).coerceAtLeast(0f),
    )
}

fun postImageEditorPanAfterDrag(
    transform: PostImageEditorTransform,
    geometry: PostImageEditorGeometry,
    dragX: Float,
    dragY: Float,
): PostImageEditorTransform = transform.withPan(
    transform.panX + if (geometry.maxPanX > 0f) dragX / geometry.maxPanX else 0f,
    transform.panY + if (geometry.maxPanY > 0f) dragY / geometry.maxPanY else 0f,
)

const val MinimumPostImageEditorZoom = 1f
const val MaximumPostImageEditorZoom = 4f

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
