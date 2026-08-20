package com.quata.feature.postcomposer.imageeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.withTranslation
import androidx.core.graphics.drawable.toBitmap
import androidx.exifinterface.media.ExifInterface
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.quata.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

const val QuataEditedImageFilePrefix = "quata-edited-image-"

@Composable
fun QuataImageEditorDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onEdited: (Uri) -> Unit,
    mode: QuataImageEditorMode = QuataImageEditorMode.Post,
) {
    val context = LocalContext.current
    val cropLocked = mode == QuataImageEditorMode.Avatar
    val outputSpec = remember(mode) {
        when (mode) {
            QuataImageEditorMode.Post -> ImageEditorPostOutputSpec
            QuataImageEditorMode.Avatar -> ImageEditorAvatarOutputSpec
        }
    }
    val scope = rememberCoroutineScope()
    var bitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var transform by remember(imageUri, mode) { mutableStateOf(PostImageEditorTransform.Default) }
    var isLoading by remember(imageUri) { mutableStateOf(true) }
    var isSaving by remember(imageUri) { mutableStateOf(false) }
    val geometry = remember(bitmap, transform, outputSpec) {
        bitmap?.let {
            postImageEditorGeometry(
                sourceWidth = it.width,
                sourceHeight = it.height,
                transform = transform,
                outputSpec = outputSpec,
            )
        }
    }

    LaunchedEffect(imageUri) {
        isLoading = true
        bitmap = withContext(Dispatchers.IO) { context.loadEditorBitmap(imageUri) }
        isLoading = false
    }

    BackHandler(enabled = !isSaving, onBack = onDismiss)

    PostImageEditorDialogContent(
        transform = transform,
        geometry = geometry,
        outputSpec = outputSpec,
        onTransformChange = { transform = it },
        onDismiss = onDismiss,
        cropLocked = cropLocked,
        onSave = { cropToOutputAspect ->
            val source = bitmap ?: return@PostImageEditorDialogContent
            if (isSaving) return@PostImageEditorDialogContent
            isSaving = true
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.exportEditedImage(
                            sourceUri = imageUri,
                            source = source,
                            transform = transform,
                            outputSpec = outputSpec,
                            cropToOutputAspect = cropToOutputAspect,
                        )
                    }
                }.onSuccess(onEdited)
                    .onFailure { isSaving = false }
            }
        },
        strings = PostImageEditorStrings(
            title = stringResource(R.string.video_editor_title),
            zoom = stringResource(R.string.video_editor_zoom),
            rotate = stringResource(R.string.image_editor_rotate),
            reset = stringResource(R.string.image_editor_reset),
            cancel = stringResource(android.R.string.cancel),
            save = stringResource(R.string.video_editor_export),
        ),
        preview = { currentTransform, currentGeometry, cropPanelOpen, cropApplied, modifier ->
            val currentBitmap = bitmap
            if (isLoading || isSaving || currentBitmap == null || currentGeometry == null) {
                CircularProgressIndicator(modifier = modifier)
            } else {
                AndroidPostImageEditorPreview(
                    bitmap = currentBitmap,
                    transform = currentTransform,
                    geometry = currentGeometry,
                    outputSpec = outputSpec,
                    cropToOutputAspect = cropPanelOpen || cropApplied || cropLocked,
                    modifier = modifier,
                )
            }
        },
    )
}

@Composable
private fun AndroidPostImageEditorPreview(
    bitmap: Bitmap,
    transform: PostImageEditorTransform,
    geometry: PostImageEditorGeometry,
    outputSpec: ImageEditorOutputSpec,
    cropToOutputAspect: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val turns = ((transform.quarterTurns % 4) + 4) % 4
        if (!cropToOutputAspect) {
            val rotatedWidth = if (turns % 2 == 0) bitmap.width else bitmap.height
            val rotatedHeight = if (turns % 2 == 0) bitmap.height else bitmap.width
            val scale = minOf(size.width / rotatedWidth.toFloat(), size.height / rotatedHeight.toFloat())
            val drawWidth = bitmap.width * scale
            val drawHeight = bitmap.height * scale
            val canvas = drawContext.canvas.nativeCanvas
            canvas.withTranslation(size.width / 2f, size.height / 2f) {
                rotate(turns * 90f)
                drawBitmap(
                    bitmap,
                    null,
                    android.graphics.RectF(-drawWidth / 2f, -drawHeight / 2f, drawWidth / 2f, drawHeight / 2f),
                    PreviewPaint,
                )
            }
            return@Canvas
        }
        val frameScale = minOf(size.width / outputSpec.width, size.height / outputSpec.height)
        val drawWidth = geometry.sourceDrawnWidth * frameScale
        val drawHeight = geometry.sourceDrawnHeight * frameScale
        val maxPanX = geometry.maxPanX * frameScale
        val maxPanY = geometry.maxPanY * frameScale
        val canvas = drawContext.canvas.nativeCanvas
        canvas.withTranslation(
            size.width / 2f + transform.panX * maxPanX,
            size.height / 2f + transform.panY * maxPanY,
        ) {
            rotate(transform.quarterTurns * 90f)
            drawBitmap(
                bitmap,
                null,
                android.graphics.RectF(-drawWidth / 2f, -drawHeight / 2f, drawWidth / 2f, drawHeight / 2f),
                PreviewPaint,
            )
        }
    }
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
    val result = runCatching { imageLoader.execute(request) }.getOrNull() as? SuccessResult ?: return null
    return result.drawable.toBitmap(config = Bitmap.Config.ARGB_8888)
}

private fun Context.exportEditedImage(
    sourceUri: Uri,
    source: Bitmap,
    transform: PostImageEditorTransform,
    outputSpec: ImageEditorOutputSpec,
    cropToOutputAspect: Boolean,
): Uri {
    val outputFile = File(cacheDir, "$QuataEditedImageFilePrefix${System.currentTimeMillis()}.jpg")
    if (!cropToOutputAspect) {
        val turns = ((transform.quarterTurns % 4) + 4) % 4
        val output = if (turns == 0) source else source.rotateClockwise(turns)
        try {
            outputFile.outputStream().use { output.compress(Bitmap.CompressFormat.JPEG, ImageEditorJpegQuality, it) }
            copyImageGpsMetadata(sourceUri, outputFile)
            return Uri.fromFile(outputFile)
        } finally {
            if (output !== source) output.recycle()
        }
    }
    val output = Bitmap.createBitmap(outputSpec.width, outputSpec.height, Bitmap.Config.ARGB_8888)
    try {
        val canvas = android.graphics.Canvas(output)
        val turns = ((transform.quarterTurns % 4) + 4) % 4
        val scale = maxOf(outputSpec.width.toFloat() / source.width, outputSpec.height.toFloat() / source.height) * transform.zoom
        val sourceDrawnWidth = source.width * scale
        val sourceDrawnHeight = source.height * scale
        val outputDrawnWidth = if (turns % 2 == 0) sourceDrawnWidth else sourceDrawnHeight
        val outputDrawnHeight = if (turns % 2 == 0) sourceDrawnHeight else sourceDrawnWidth
        val maxPanX = ((outputDrawnWidth - outputSpec.width) / 2f).coerceAtLeast(0f)
        val maxPanY = ((outputDrawnHeight - outputSpec.height) / 2f).coerceAtLeast(0f)
        canvas.drawColor(android.graphics.Color.BLACK)
        canvas.withTranslation(
            outputSpec.width / 2f + transform.panX.coerceIn(-1f, 1f) * maxPanX,
            outputSpec.height / 2f + transform.panY.coerceIn(-1f, 1f) * maxPanY,
        ) {
            rotate(turns * 90f)
            drawBitmap(
                source,
                null,
                android.graphics.RectF(-sourceDrawnWidth / 2f, -sourceDrawnHeight / 2f, sourceDrawnWidth / 2f, sourceDrawnHeight / 2f),
                ExportPaint,
            )
        }
        outputFile.outputStream().use { output.compress(Bitmap.CompressFormat.JPEG, ImageEditorJpegQuality, it) }
        copyImageGpsMetadata(sourceUri, outputFile)
        return Uri.fromFile(outputFile)
    } finally {
        output.recycle()
    }
}

private fun Bitmap.rotateClockwise(turns: Int): Bitmap {
    val matrix = Matrix().apply { postRotate((((turns % 4) + 4) % 4) * 90f) }
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
    ExifInterface.TAG_GPS_TIMESTAMP,
)

private const val ImageEditorDecodeMaxSize = 2160
private const val ImageEditorJpegQuality = 92
