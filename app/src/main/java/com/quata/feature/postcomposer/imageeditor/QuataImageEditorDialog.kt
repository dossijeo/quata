package com.quata.feature.postcomposer.imageeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.exifinterface.media.ExifInterface
import com.quata.R
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

const val QuataEditedImageFilePrefix = "quata-edited-image-"

@Composable
fun QuataImageEditorDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onEdited: (Uri) -> Unit
) {
    val context = LocalContext.current
    val template = quataTheme()
    val scope = rememberCoroutineScope()
    var bitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(imageUri) { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var isCropPanelOpen by remember(imageUri) { mutableStateOf(true) }
    var zoom by remember(imageUri) { mutableFloatStateOf(1f) }
    var pan by remember(imageUri) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(imageUri) {
        isLoading = true
        bitmap = withContext(Dispatchers.IO) { context.loadEditorBitmap(imageUri) }
        isLoading = false
    }

    BackHandler(enabled = !isSaving, onBack = onDismiss)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = template.colors.background,
        contentColor = template.colors.textPrimary
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = ImageEditorBottomAir)
        ) {
            ImageEditorTopBar(
                isCropPanelOpen = isCropPanelOpen,
                isSaving = isSaving,
                canSave = bitmap != null,
                onBack = onDismiss,
                onToggleCrop = { isCropPanelOpen = !isCropPanelOpen },
                onSave = {
                    val source = bitmap ?: return@ImageEditorTopBar
                    isSaving = true
                    isCropPanelOpen = false
                    scope.launch {
                        val edited = withContext(Dispatchers.IO) {
                            context.exportEditedImage(sourceUri = imageUri, source = source, zoom = zoom, pan = pan)
                        }
                        isSaving = false
                        onEdited(edited)
                    }
                }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> CircularProgressIndicator(color = template.colors.accent)
                    bitmap == null -> Text(
                        text = stringResource(R.string.composer_image_preview_title),
                        color = template.colors.textSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    else -> bitmap?.let { source ->
                        ImagePreviewPane(
                            bitmap = source,
                            zoom = zoom,
                            pan = pan,
                            isCropVisible = isCropPanelOpen,
                            onPanChange = { pan = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
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
private fun ImageEditorTopBar(
    isCropPanelOpen: Boolean,
    isSaving: Boolean,
    canSave: Boolean,
    onBack: () -> Unit,
    onToggleCrop: () -> Unit,
    onSave: () -> Unit
) {
    val template = quataTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(template.colors.surfaceRaised)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactIconButton(onClick = onBack, enabled = !isSaving) {
            CompactIcon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.video_editor_back),
                tint = template.colors.textPrimary
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.video_editor_title),
            color = template.colors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!isSaving) {
            ImageToolButton(
                label = stringResource(if (isCropPanelOpen) R.string.video_editor_crop_done else R.string.video_editor_crop),
                enabled = canSave,
                selected = isCropPanelOpen,
                onClick = onToggleCrop
            ) {
                CompactIcon(
                    if (isCropPanelOpen) Icons.Filled.Check else Icons.Filled.Crop,
                    contentDescription = null
                )
            }
            ImageToolButton(
                label = stringResource(R.string.video_editor_export),
                enabled = canSave,
                onClick = onSave
            ) {
                CompactIcon(Icons.Filled.Save, contentDescription = null)
            }
        }
    }
}

@Composable
private fun ImageToolButton(
    label: String,
    enabled: Boolean,
    selected: Boolean = false,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val template = quataTheme()
    val contentAlpha = if (enabled) 1f else 0.42f
    val iconColor = if (selected) template.colors.accentContent else template.colors.textPrimary.copy(alpha = contentAlpha)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(min = 66.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) template.colors.accent else template.colors.surfaceAlt.copy(alpha = if (enabled) 1f else 0.54f))
                .border(1.dp, if (selected) template.colors.accent else template.colors.divider, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            CompactIconButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.matchParentSize()
            ) {
                CompositionLocalProvider(LocalContentColor provides iconColor) {
                    Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                        icon()
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = template.colors.textSecondary.copy(alpha = contentAlpha), fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun ImagePreviewPane(
    bitmap: Bitmap,
    zoom: Float,
    pan: Offset,
    isCropVisible: Boolean,
    onPanChange: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val widthFromHeight = maxHeight * ImageEditorOutputAspectRatio
        val previewWidth: Dp
        val previewHeight: Dp
        if (widthFromHeight <= maxWidth) {
            previewWidth = widthFromHeight
            previewHeight = maxHeight
        } else {
            previewWidth = maxWidth
            previewHeight = maxWidth / ImageEditorOutputAspectRatio
        }
        Box(
            modifier = Modifier
                .size(previewWidth, previewHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
        ) {
            if (isCropVisible) {
                ImageCropAdjustmentCanvas(
                    bitmap = bitmap,
                    zoom = zoom,
                    pan = pan,
                    onPanChange = onPanChange,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                ImageCroppedPreviewCanvas(
                    bitmap = bitmap,
                    zoom = zoom,
                    pan = pan,
                    modifier = Modifier.fillMaxSize()
                )
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
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val src = bitmap.cropRectForNineSixteen(zoom = zoom, pan = pan)
        val dst = Rect(0, 0, size.width.roundToInt(), size.height.roundToInt())
        drawContext.canvas.nativeCanvas.drawBitmap(bitmap, src, dst, PreviewPaint)
    }
}

@Composable
private fun ImageCropAdjustmentCanvas(
    bitmap: Bitmap,
    zoom: Float,
    pan: Offset,
    onPanChange: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPan by rememberUpdatedState(pan)
    val currentOnPanChange by rememberUpdatedState(onPanChange)
    Canvas(
        modifier = modifier.pointerInput(bitmap, zoom) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                val geometry = bitmap.cropGeometryForNineSixteen(
                    zoom = zoom,
                    pan = currentPan,
                    canvasWidth = size.width.toFloat().coerceAtLeast(1f),
                    canvasHeight = size.height.toFloat().coerceAtLeast(1f)
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
        val geometry = bitmap.cropGeometryForNineSixteen(
            zoom = zoom,
            pan = pan,
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
        drawRect(
            color = QuataOrange,
            topLeft = Offset(crop.left, crop.top),
            size = Size(crop.width(), crop.height()),
            style = Stroke(width = 4.dp.toPx())
        )
        val radius = 6.5f.dp.toPx()
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

private fun Bitmap.cropGeometryForNineSixteen(
    zoom: Float,
    pan: Offset,
    canvasWidth: Float,
    canvasHeight: Float
): ImageCropGeometry {
    val crop = cropRectFForNineSixteen(zoom, pan)
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

private fun Context.loadEditorBitmap(uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inSampleSize = calculateImageSampleSize(bounds.outWidth, bounds.outHeight, ImageEditorDecodeMaxSize)
    }
    return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

private fun calculateImageSampleSize(width: Int, height: Int, maxSize: Int): Int {
    var sample = 1
    while (width / sample > maxSize || height / sample > maxSize) {
        sample *= 2
    }
    return sample
}

private fun Context.exportEditedImage(
    sourceUri: Uri,
    source: Bitmap,
    zoom: Float,
    pan: Offset
): Uri {
    val outputFile = File(cacheDir, "$QuataEditedImageFilePrefix${System.currentTimeMillis()}.jpg")
    val output = Bitmap.createBitmap(ImageEditorOutputWidth, ImageEditorOutputHeight, Bitmap.Config.ARGB_8888)
    try {
        val canvas = android.graphics.Canvas(output)
        val src = source.cropRectForNineSixteen(zoom = zoom, pan = pan)
        val dst = Rect(0, 0, ImageEditorOutputWidth, ImageEditorOutputHeight)
        canvas.drawBitmap(source, src, dst, ExportPaint)
        outputFile.outputStream().use { output.compress(Bitmap.CompressFormat.JPEG, ImageEditorJpegQuality, it) }
        copyImageGpsMetadata(sourceUri, outputFile)
        return Uri.fromFile(outputFile)
    } finally {
        output.recycle()
    }
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

private fun Bitmap.cropRectForNineSixteen(zoom: Float, pan: Offset): Rect {
    val crop = cropRectFForNineSixteen(zoom, pan)
    return Rect(
        crop.left.roundToInt(),
        crop.top.roundToInt(),
        crop.right.roundToInt().coerceAtMost(width),
        crop.bottom.roundToInt().coerceAtMost(height)
    )
}

private fun Bitmap.cropRectFForNineSixteen(zoom: Float, pan: Offset): RectF {
    val safeZoom = zoom.coerceIn(1f, 3f)
    val targetAspect = ImageEditorOutputAspectRatio
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

private const val ImageEditorOutputWidth = 1080
private const val ImageEditorOutputHeight = 1920
private const val ImageEditorOutputAspectRatio = 9f / 16f
private const val ImageEditorDecodeMaxSize = 2160
private const val ImageEditorJpegQuality = 92
private val ImageEditorBottomAir = 56.dp
