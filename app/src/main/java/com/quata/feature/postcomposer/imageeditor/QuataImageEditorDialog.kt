package com.quata.feature.postcomposer.imageeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.exifinterface.media.ExifInterface
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.quata.R
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.core.ui.components.QuataEditorScaffold
import com.quata.core.ui.components.QuataEditorToolButton
import com.quata.core.ui.textCanvasBrush
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.math.roundToInt

const val QuataEditedImageFilePrefix = "quata-edited-image-"

@Composable
fun QuataImageEditorDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onEdited: (Uri) -> Unit,
    mode: QuataImageEditorMode = QuataImageEditorMode.Post
) {
    val context = LocalContext.current
    val isLandscapeLayout = rememberQuataWindowLayoutInfo().isLandscape
    val outputSpec = remember(mode) {
        when (mode) {
            QuataImageEditorMode.Post -> ImageEditorPostOutputSpec
            QuataImageEditorMode.Avatar -> ImageEditorAvatarOutputSpec
        }
    }
    val isCropLocked = mode == QuataImageEditorMode.Avatar
    val scope = rememberCoroutineScope()
    var originalBitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var bitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(imageUri) { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var isCropPanelOpen by remember(imageUri, mode) { mutableStateOf(isCropLocked) }
    var isCropApplied by remember(imageUri, mode) { mutableStateOf(isCropLocked) }
    var imageGradientSeed by remember(imageUri) { mutableStateOf(imageUri.toString()) }
    var zoom by remember(imageUri) { mutableFloatStateOf(1f) }
    var pan by remember(imageUri) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(imageUri) {
        isLoading = true
        val loaded = withContext(Dispatchers.IO) {
            val seed = context.imageHashSeed(imageUri)
            seed to context.loadEditorBitmap(imageUri)
        }
        imageGradientSeed = loaded.first ?: imageUri.toString()
        originalBitmap = loaded.second
        bitmap = loaded.second
        isLoading = false
    }

    BackHandler(enabled = !isSaving, onBack = onDismiss)

    QuataEditorScaffold(
        title = stringResource(R.string.video_editor_title),
        showTitle = !isLandscapeLayout,
        onBack = onDismiss,
        backContentDescription = stringResource(R.string.video_editor_back),
        backEnabled = !isSaving,
        bottomPadding = if (isLandscapeLayout) 0.dp else ImageEditorBottomAir,
        actions = {
            if (!isSaving) {
                val canSave = bitmap != null
                QuataEditorToolButton(
                    label = stringResource(R.string.image_editor_reset),
                    enabled = canSave,
                    onClick = {
                    originalBitmap?.let { original ->
                        bitmap = original
                        zoom = 1f
                        pan = Offset.Zero
                        isCropPanelOpen = isCropLocked
                        isCropApplied = isCropLocked
                    }
                    }
                ) {
                    CompactIcon(Icons.Filled.Replay, contentDescription = null)
                }
                QuataEditorToolButton(
                    label = stringResource(R.string.image_editor_rotate),
                    enabled = canSave,
                    onClick = {
                    bitmap = bitmap?.rotateClockwise()
                    zoom = 1f
                    pan = Offset.Zero
                    isCropApplied = isCropLocked
                    isCropPanelOpen = isCropLocked
                    }
                ) {
                    CompactIcon(Icons.Filled.Rotate90DegreesCw, contentDescription = null)
                }
                if (!isCropLocked) {
                    QuataEditorToolButton(
                        label = stringResource(if (isCropPanelOpen) R.string.video_editor_crop_done else R.string.video_editor_crop),
                        enabled = canSave,
                        selected = isCropPanelOpen,
                        onClick = {
                    if (!isCropLocked) {
                        if (isCropPanelOpen) {
                            isCropApplied = true
                            isCropPanelOpen = false
                        } else {
                            isCropPanelOpen = true
                        }
                    }
                        }
                    ) {
                        CompactIcon(
                            if (isCropPanelOpen) Icons.Filled.Check else Icons.Filled.Crop,
                            contentDescription = null
                        )
                    }
                }
                QuataEditorToolButton(
                    label = stringResource(R.string.video_editor_export),
                    enabled = canSave,
                    onClick = {
                    val source = bitmap ?: return@QuataEditorToolButton
                    val cropToOutputAspect = isCropLocked || isCropPanelOpen || isCropApplied
                    isSaving = true
                    isCropPanelOpen = isCropLocked
                    isCropApplied = cropToOutputAspect
                    scope.launch {
                        val edited = withContext(Dispatchers.IO) {
                            context.exportEditedImage(
                                sourceUri = imageUri,
                                source = source,
                                zoom = zoom,
                                pan = pan,
                                cropToOutputAspect = cropToOutputAspect,
                                outputSpec = outputSpec
                            )
                        }
                        isSaving = false
                        onEdited(edited)
                    }
                    }
                ) {
                    CompactIcon(Icons.Filled.Save, contentDescription = null)
                }
            }
        }
    ) {

            if (isLandscapeLayout && isCropPanelOpen) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ImageEditorPreviewArea(
                        bitmap = bitmap,
                        isLoading = isLoading,
                        isSaving = isSaving,
                        zoom = zoom,
                        pan = pan,
                        gradientSeed = imageGradientSeed,
                        isCropPanelOpen = isCropPanelOpen,
                        isCropApplied = isCropApplied,
                        outputSpec = outputSpec,
                        onPanChange = { pan = it },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    Spacer(Modifier.width(12.dp))
                    ImageCropControls(
                        zoom = zoom,
                        enabled = !isSaving && bitmap != null,
                        onZoomChange = { nextZoom ->
                            zoom = nextZoom
                            pan = pan.clampedPan()
                        },
                        modifier = Modifier.width(284.dp)
                    )
                }
            } else {
                ImageEditorPreviewArea(
                    bitmap = bitmap,
                    isLoading = isLoading,
                    isSaving = isSaving,
                    zoom = zoom,
                    pan = pan,
                    gradientSeed = imageGradientSeed,
                    isCropPanelOpen = isCropPanelOpen,
                    isCropApplied = isCropApplied,
                    outputSpec = outputSpec,
                    onPanChange = { pan = it },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )

                if (isCropPanelOpen) {
                    ImageCropControls(
                        zoom = zoom,
                        enabled = !isSaving && bitmap != null,
                        onZoomChange = { nextZoom ->
                            zoom = nextZoom
                            pan = pan.clampedPan()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }
    }
}

@Composable
private fun ImageEditorPreviewArea(
    bitmap: Bitmap?,
    isLoading: Boolean,
    isSaving: Boolean,
    zoom: Float,
    pan: Offset,
    gradientSeed: String,
    isCropPanelOpen: Boolean,
    isCropApplied: Boolean,
    outputSpec: ImageEditorOutputSpec,
    onPanChange: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> CircularProgressIndicator(color = template.colors.accent)
            bitmap == null -> Text(
                text = stringResource(R.string.composer_image_preview_title),
                color = template.colors.textSecondary,
                fontWeight = FontWeight.Bold
            )
            else -> ImagePreviewPane(
                bitmap = bitmap,
                zoom = zoom,
                pan = pan,
                gradientSeed = gradientSeed,
                isCropVisible = isCropPanelOpen,
                isCropApplied = isCropApplied,
                outputSpec = outputSpec,
                onPanChange = onPanChange,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isSaving) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(template.colors.mediaScrim),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = template.colors.accent)
            }
        }
    }
}

@Composable
private fun ImagePreviewPane(
    bitmap: Bitmap,
    zoom: Float,
    pan: Offset,
    gradientSeed: String,
    isCropVisible: Boolean,
    isCropApplied: Boolean,
    outputSpec: ImageEditorOutputSpec,
    onPanChange: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val widthFromHeight = maxHeight * outputSpec.aspectRatio
        val previewWidth: Dp
        val previewHeight: Dp
        if (widthFromHeight <= maxWidth) {
            previewWidth = widthFromHeight
            previewHeight = maxHeight
        } else {
            previewWidth = maxWidth
            previewHeight = maxWidth / outputSpec.aspectRatio
        }
        Box(
            modifier = Modifier
                .size(previewWidth, previewHeight)
        ) {
            if (isCropVisible) {
                ImageCropAdjustmentPane(
                    bitmap = bitmap,
                    zoom = zoom,
                    pan = pan,
                    outputSpec = outputSpec,
                    onPanChange = onPanChange,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(ImageEditorPreviewCornerRadius))
                        .background(textCanvasBrush(gradientSeed))
                ) {
                    if (isCropApplied) {
                        ImageCroppedPreviewCanvas(
                            bitmap = bitmap,
                            zoom = zoom,
                            pan = pan,
                            outputSpec = outputSpec,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        ImageFittedPreviewCanvas(
                            bitmap = bitmap,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageCropControls(
    zoom: Float,
    enabled: Boolean,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(template.colors.surface)
            .border(1.dp, template.colors.divider, RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.video_editor_zoom),
                color = template.colors.textSecondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(56.dp)
            )
            Slider(
                value = zoom,
                onValueChange = onZoomChange,
                valueRange = 1f..3f,
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ImageCroppedPreviewCanvas(
    bitmap: Bitmap,
    zoom: Float,
    pan: Offset,
    outputSpec: ImageEditorOutputSpec,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val src = bitmap.cropRectForAspect(zoom = zoom, pan = pan, targetAspect = outputSpec.aspectRatio)
        val dst = Rect(0, 0, size.width.roundToInt(), size.height.roundToInt())
        drawContext.canvas.nativeCanvas.drawBitmap(bitmap, src, dst, PreviewPaint)
    }
}

@Composable
private fun ImageFittedPreviewCanvas(
    bitmap: Bitmap,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val scale = minOf(size.width / bitmap.width.toFloat(), size.height / bitmap.height.toFloat())
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val left = (size.width - drawWidth) / 2f
        val top = (size.height - drawHeight) / 2f
        val dst = RectF(left, top, left + drawWidth, top + drawHeight)
        drawContext.canvas.nativeCanvas.drawBitmap(bitmap, null, dst, PreviewPaint)
    }
}

@Composable
private fun ImageCropAdjustmentPane(
    bitmap: Bitmap,
    zoom: Float,
    pan: Offset,
    outputSpec: ImageEditorOutputSpec,
    onPanChange: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(ImageCropAnchorInset)
                .clip(RoundedCornerShape(ImageEditorPreviewCornerRadius))
                .background(Color.Black)
        ) {
            ImageCropContentCanvas(
                bitmap = bitmap,
                zoom = zoom,
                pan = pan,
                outputSpec = outputSpec,
                modifier = Modifier.fillMaxSize()
            )
        }
        ImageCropOverlayCanvas(
            bitmap = bitmap,
            zoom = zoom,
            pan = pan,
            outputSpec = outputSpec,
            onPanChange = onPanChange,
            contentInset = ImageCropAnchorInset,
            modifier = Modifier.matchParentSize()
        )
    }
}

@Composable
private fun ImageCropContentCanvas(
    bitmap: Bitmap,
    zoom: Float,
    pan: Offset,
    outputSpec: ImageEditorOutputSpec,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val geometry = bitmap.cropGeometryForAspect(
            zoom = zoom,
            pan = pan,
            targetAspect = outputSpec.aspectRatio,
            canvasWidth = size.width,
            canvasHeight = size.height
        )
        val crop = geometry.cropDst
        drawContext.canvas.nativeCanvas.drawBitmap(bitmap, null, geometry.imageDst, PreviewPaint)
        val overlay = Color.Black.copy(alpha = 0.52f)
        drawRect(overlay, topLeft = Offset.Zero, size = Size(size.width, crop.top))
        drawRect(overlay, topLeft = Offset(0f, crop.bottom), size = Size(size.width, size.height - crop.bottom))
        drawRect(overlay, topLeft = Offset(0f, crop.top), size = Size(crop.left, crop.height()))
        drawRect(overlay, topLeft = Offset(crop.right, crop.top), size = Size(size.width - crop.right, crop.height()))
    }
}

@Composable
private fun ImageCropOverlayCanvas(
    bitmap: Bitmap,
    zoom: Float,
    pan: Offset,
    outputSpec: ImageEditorOutputSpec,
    onPanChange: (Offset) -> Unit,
    contentInset: Dp,
    modifier: Modifier = Modifier
) {
    val currentPan by rememberUpdatedState(pan)
    val currentOnPanChange by rememberUpdatedState(onPanChange)
    Canvas(
        modifier = modifier.pointerInput(bitmap, zoom, contentInset) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                val insetPx = contentInset.toPx()
                val geometry = bitmap.cropGeometryForAspect(
                    zoom = zoom,
                    pan = currentPan,
                    targetAspect = outputSpec.aspectRatio,
                    canvasWidth = (size.width.toFloat() - insetPx * 2f).coerceAtLeast(1f),
                    canvasHeight = (size.height.toFloat() - insetPx * 2f).coerceAtLeast(1f)
                )
                val nextX = if (geometry.maxShiftX > 0f) {
                    currentPan.x + dragAmount.x / (geometry.maxShiftX * geometry.scale)
                } else {
                    currentPan.x
                }
                val nextY = if (geometry.maxShiftY > 0f) {
                    currentPan.y + dragAmount.y / (geometry.maxShiftY * geometry.scale)
                } else {
                    currentPan.y
                }
                currentOnPanChange(Offset(nextX, nextY).clampedPan())
            }
        }
    ) {
        val insetPx = contentInset.toPx()
        val geometry = bitmap.cropGeometryForAspect(
            zoom = zoom,
            pan = pan,
            targetAspect = outputSpec.aspectRatio,
            canvasWidth = (size.width - insetPx * 2f).coerceAtLeast(1f),
            canvasHeight = (size.height - insetPx * 2f).coerceAtLeast(1f)
        )
        val crop = RectF(geometry.cropDst).apply { offset(insetPx, insetPx) }
        drawRect(
            color = QuataOrange,
            topLeft = Offset(crop.left, crop.top),
            size = Size(crop.width(), crop.height()),
            style = Stroke(width = 4.dp.toPx())
        )
        val radius = ImageCropAnchorRadius.toPx()
        drawCircle(QuataOrange, radius, Offset(crop.left, crop.top))
        drawCircle(QuataOrange, radius, Offset(crop.right, crop.top))
        drawCircle(QuataOrange, radius, Offset(crop.left, crop.bottom))
        drawCircle(QuataOrange, radius, Offset(crop.right, crop.bottom))
    }
}

private data class ImageCropGeometry(
    val imageDst: RectF,
    val cropDst: RectF,
    val scale: Float,
    val maxShiftX: Float,
    val maxShiftY: Float
)

private fun Bitmap.cropGeometryForAspect(
    zoom: Float,
    pan: Offset,
    targetAspect: Float,
    canvasWidth: Float,
    canvasHeight: Float
): ImageCropGeometry {
    val crop = cropRectFForAspect(zoom, pan, targetAspect)
    val scale = minOf(canvasWidth / width.toFloat(), canvasHeight / height.toFloat())
    val imageWidth = width * scale
    val imageHeight = height * scale
    val imageLeft = (canvasWidth - imageWidth) / 2f
    val imageTop = (canvasHeight - imageHeight) / 2f
    val imageDst = RectF(imageLeft, imageTop, imageLeft + imageWidth, imageTop + imageHeight)
    val cropDst = RectF(
        imageLeft + crop.left * scale,
        imageTop + crop.top * scale,
        imageLeft + crop.right * scale,
        imageTop + crop.bottom * scale
    )
    val maxShiftX = ((width - crop.width()) / 2f).coerceAtLeast(0f)
    val maxShiftY = ((height - crop.height()) / 2f).coerceAtLeast(0f)
    return ImageCropGeometry(
        imageDst = imageDst,
        cropDst = cropDst,
        scale = scale,
        maxShiftX = maxShiftX,
        maxShiftY = maxShiftY
    )
}

private suspend fun Context.loadEditorBitmap(uri: Uri): Bitmap? {
    val cacheKey = "image-editor:$uri:$ImageEditorDecodeMaxSize"
    val request = ImageRequest.Builder(this)
        .data(uri)
        .size(ImageEditorDecodeMaxSize, ImageEditorDecodeMaxSize)
        .allowHardware(false)
        .memoryCacheKey(cacheKey)
        .diskCacheKey(cacheKey)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .build()
    val result = runCatching { imageLoader.execute(request) }.getOrNull() as? SuccessResult
        ?: return null
    return result.drawable.toBitmap(config = Bitmap.Config.ARGB_8888)
}

private fun Context.exportEditedImage(
    sourceUri: Uri,
    source: Bitmap,
    zoom: Float,
    pan: Offset,
    cropToOutputAspect: Boolean,
    outputSpec: ImageEditorOutputSpec
): Uri {
    val outputFile = File(cacheDir, "$QuataEditedImageFilePrefix${System.currentTimeMillis()}.jpg")
    if (!cropToOutputAspect) {
        outputFile.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, ImageEditorJpegQuality, it) }
        copyImageGpsMetadata(sourceUri, outputFile)
        return Uri.fromFile(outputFile)
    }

    val output = Bitmap.createBitmap(outputSpec.width, outputSpec.height, Bitmap.Config.ARGB_8888)
    try {
        val canvas = android.graphics.Canvas(output)
        val src = source.cropRectForAspect(zoom = zoom, pan = pan, targetAspect = outputSpec.aspectRatio)
        val dst = Rect(0, 0, outputSpec.width, outputSpec.height)
        canvas.drawBitmap(source, src, dst, ExportPaint)
        outputFile.outputStream().use { output.compress(Bitmap.CompressFormat.JPEG, ImageEditorJpegQuality, it) }
        copyImageGpsMetadata(sourceUri, outputFile)
        return Uri.fromFile(outputFile)
    } finally {
        output.recycle()
    }
}

private fun Context.imageHashSeed(uri: Uri): String? =
    runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        } ?: return@runCatching null
        digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }.getOrNull()

private fun Bitmap.rotateClockwise(): Bitmap {
    val matrix = Matrix().apply { postRotate(90f) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private fun Context.copyImageGpsMetadata(sourceUri: Uri, outputFile: File) {
    runCatching {
        val sourceExif = contentResolver.openInputStream(sourceUri)?.use { ExifInterface(it) } ?: return@runCatching
        val targetExif = ExifInterface(outputFile.absolutePath)
        ImageGpsTags.forEach { tag ->
            sourceExif.getAttribute(tag)?.let { value -> targetExif.setAttribute(tag, value) }
        }
        targetExif.saveAttributes()
    }
}

private fun Bitmap.cropRectForAspect(zoom: Float, pan: Offset, targetAspect: Float): Rect {
    val crop = cropRectFForAspect(zoom, pan, targetAspect)
    return Rect(
        crop.left.roundToInt(),
        crop.top.roundToInt(),
        crop.right.roundToInt().coerceAtMost(width),
        crop.bottom.roundToInt().coerceAtMost(height)
    )
}

private fun Bitmap.cropRectFForAspect(zoom: Float, pan: Offset, targetAspect: Float): RectF {
    val safeZoom = zoom.coerceIn(1f, 3f)
    val bitmapAspect = width.toFloat() / height.toFloat()
    val baseCropWidth: Float
    val baseCropHeight: Float
    if (bitmapAspect > targetAspect) {
        baseCropHeight = height.toFloat()
        baseCropWidth = baseCropHeight * targetAspect
    } else {
        baseCropWidth = width.toFloat()
        baseCropHeight = baseCropWidth / targetAspect
    }

    val cropWidth = (baseCropWidth / safeZoom).coerceAtMost(width.toFloat())
    val cropHeight = (baseCropHeight / safeZoom).coerceAtMost(height.toFloat())
    val maxShiftX = ((width - cropWidth) / 2f).coerceAtLeast(0f)
    val maxShiftY = ((height - cropHeight) / 2f).coerceAtLeast(0f)
    val centerX = width / 2f + pan.x.coerceIn(-1f, 1f) * maxShiftX
    val centerY = height / 2f + pan.y.coerceIn(-1f, 1f) * maxShiftY
    val left = (centerX - cropWidth / 2f).coerceIn(0f, width - cropWidth)
    val top = (centerY - cropHeight / 2f).coerceIn(0f, height - cropHeight)
    return RectF(
        left,
        top,
        (left + cropWidth).coerceAtMost(width.toFloat()),
        (top + cropHeight).coerceAtMost(height.toFloat())
    )
}

private fun Offset.clampedPan(): Offset = Offset(x.coerceIn(-1f, 1f), y.coerceIn(-1f, 1f))

private val PreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
private val ExportPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
private val ImageGpsTags = listOf(
    ExifInterface.TAG_GPS_LATITUDE,
    ExifInterface.TAG_GPS_LATITUDE_REF,
    ExifInterface.TAG_GPS_LONGITUDE,
    ExifInterface.TAG_GPS_LONGITUDE_REF,
    ExifInterface.TAG_GPS_ALTITUDE,
    ExifInterface.TAG_GPS_ALTITUDE_REF,
    ExifInterface.TAG_GPS_PROCESSING_METHOD,
    ExifInterface.TAG_GPS_DATESTAMP,
    ExifInterface.TAG_GPS_TIMESTAMP
)

private const val ImageEditorDecodeMaxSize = 2160
private const val ImageEditorJpegQuality = 92
private val ImageEditorBottomAir = 56.dp
private val ImageEditorPreviewCornerRadius = 16.dp
private val ImageCropAnchorRadius = 6.5.dp
private val ImageCropAnchorInset = 8.dp
