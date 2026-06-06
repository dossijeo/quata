package com.quata.core.media

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

data class UploadMediaPayload(
    val fileName: String,
    val mimeType: String,
    val extension: String,
    val bytes: ByteArray
)

class UploadVideoPayload internal constructor(
    val fileName: String,
    val mimeType: String,
    val extension: String,
    val sizeBytes: Long?,
    private val cleanupFile: File?,
    private val openStreamProvider: () -> InputStream
) {
    fun openStream(): InputStream = openStreamProvider()

    fun cleanup() {
        cleanupFile?.delete()
    }
}

data class ImageUploadOptions(
    val compressAboveBytes: Long = 850L * 1024L,
    val maxSide: Int = 1600,
    val jpegQuality: Int = 82,
    val cropToSquare: Boolean = false
) {
    companion object {
        val Default = ImageUploadOptions()
        val Avatar = ImageUploadOptions(maxSide = 900, cropToSquare = true)
    }
}

data class VideoUploadOptions(
    val autoOptimizeBytes: Long = 24L * 1024L * 1024L,
    val slowConnectionRecommendedBytes: Long = 8L * 1024L * 1024L,
    val targetHeight: Int = 720,
    val targetBitrate: Int = 2_000_000,
    val force: Boolean = false,
    val skipOptimize: Boolean = false
)

class MediaUploadOptimizer(private val appContext: Context) {
    suspend fun prepareImageUpload(
        uriString: String,
        fallbackMimeType: String = "image/jpeg",
        fallbackFileNameBase: String = "imagen",
        options: ImageUploadOptions = ImageUploadOptions.Default
    ): UploadMediaPayload {
        val source = readSource(
            uriString = uriString,
            fallbackMimeType = fallbackMimeType,
            fallbackFileNameBase = fallbackFileNameBase
        )
        val original = source.readPayload()
        return compressImageForUpload(original, options)
    }

    suspend fun prepareVideoUpload(
        uriString: String,
        fallbackMimeType: String = "video/mp4",
        fallbackFileNameBase: String = "video",
        options: VideoUploadOptions = VideoUploadOptions()
    ): UploadMediaPayload {
        val source = readSource(
            uriString = uriString,
            fallbackMimeType = fallbackMimeType,
            fallbackFileNameBase = fallbackFileNameBase
        )
        val optimized = optimizeVideoForUpload(source, options)
        return optimized ?: source.readPayload()
    }

    suspend fun prepareVideoUploadStream(
        uriString: String,
        fallbackMimeType: String = "video/mp4",
        fallbackFileNameBase: String = "video",
        options: VideoUploadOptions = VideoUploadOptions()
    ): UploadVideoPayload {
        val source = readSource(
            uriString = uriString,
            fallbackMimeType = fallbackMimeType,
            fallbackFileNameBase = fallbackFileNameBase
        )
        val optimized = optimizeVideoForUploadStream(source, options)
        return optimized ?: source.toStreamingPayload()
    }

    suspend fun prepareAttachmentUpload(
        uriString: String,
        attachmentName: String?,
        attachmentMimeType: String?
    ): UploadMediaPayload {
        val source = readSource(
            uriString = uriString,
            fallbackMimeType = attachmentMimeType ?: "application/octet-stream",
            fallbackFileNameBase = "upload",
            suppliedFileName = attachmentName,
            suppliedMimeType = attachmentMimeType
        )
        return when {
            source.mimeType.startsWith("image/", ignoreCase = true) -> {
                val original = source.readPayload()
                compressImageForUpload(original, ImageUploadOptions.Default)
            }
            source.mimeType.startsWith("video/", ignoreCase = true) -> {
                optimizeVideoForUpload(source, VideoUploadOptions()) ?: source.readPayload()
            }
            else -> source.readPayload()
        }
    }

    fun isLocalUploadUri(uriString: String?): Boolean {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        return scheme.isNullOrBlank() ||
            scheme == ContentResolver.SCHEME_CONTENT ||
            scheme == ContentResolver.SCHEME_FILE
    }

    private suspend fun readSource(
        uriString: String,
        fallbackMimeType: String,
        fallbackFileNameBase: String,
        suppliedFileName: String? = null,
        suppliedMimeType: String? = null
    ): MediaSource = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        val providerInfo = queryProviderInfo(uri)
        val mimeType = suppliedMimeType
            ?: appContext.contentResolver.getType(uri)
            ?: fallbackMimeType
        val fileName = suppliedFileName?.takeIf { it.isNotBlank() }
            ?: providerInfo.fileName
            ?: "$fallbackFileNameBase-${System.currentTimeMillis()}.${mimeType.substringAfter('/', "bin")}"
        MediaSource(
            uri = uri,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = providerInfo.sizeBytes
        )
    }

    private suspend fun MediaSource.readPayload(): UploadMediaPayload = withContext(Dispatchers.IO) {
        val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("No se pudo leer el archivo seleccionado")
        UploadMediaPayload(
            fileName = fileName,
            mimeType = mimeType,
            extension = fileName.uploadExtension(mimeType),
            bytes = bytes
        )
    }

    private fun compressImageForUpload(
        media: UploadMediaPayload,
        options: ImageUploadOptions
    ): UploadMediaPayload {
        if (!media.mimeType.startsWith("image/", ignoreCase = true)) return media
        if (!options.cropToSquare && media.bytes.size <= options.compressAboveBytes) return media

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(media.bytes, 0, media.bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return media

        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, options.maxSide)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(media.bytes, 0, media.bytes.size, decodeOptions) ?: return media
        val oriented = decoded.orientFromExif(media.bytes)
        if (oriented !== decoded) decoded.recycle()

        val cropped = if (options.cropToSquare) {
            oriented.centerCropSquare()
        } else {
            oriented
        }
        if (cropped !== oriented) oriented.recycle()

        val scale = (options.maxSide.toFloat() / max(cropped.width, cropped.height).coerceAtLeast(1))
            .coerceAtMost(1f)
        val targetWidth = max(1, (cropped.width * scale).roundToInt())
        val targetHeight = max(1, (cropped.height * scale).roundToInt())
        val scaled = if (targetWidth != cropped.width || targetHeight != cropped.height) {
            Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)
        } else {
            cropped
        }
        if (scaled !== cropped) cropped.recycle()

        val opaque = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.RGB_565)
        Canvas(opaque).apply {
            drawColor(Color.WHITE)
            drawBitmap(scaled, 0f, 0f, null)
        }
        if (opaque !== scaled) scaled.recycle()

        val output = ByteArrayOutputStream()
        opaque.compress(Bitmap.CompressFormat.JPEG, options.jpegQuality, output)
        opaque.recycle()

        val compressed = output.toByteArray()
        if (!options.cropToSquare && compressed.size >= media.bytes.size) return media

        val baseName = media.fileName.substringBeforeLast('.', media.fileName).ifBlank { "imagen" }
        return media.copy(
            fileName = "$baseName.jpg",
            mimeType = "image/jpeg",
            extension = "jpg",
            bytes = compressed
        )
    }

    @OptIn(UnstableApi::class)
    private suspend fun optimizeVideoForUpload(
        source: MediaSource,
        options: VideoUploadOptions
    ): UploadMediaPayload? {
        val optimized = optimizeVideoForUploadStream(source, options) ?: return null
        return try {
            val bytes = withContext(Dispatchers.IO) { optimized.openStream().use { it.readBytes() } }
            UploadMediaPayload(
                fileName = optimized.fileName,
                mimeType = optimized.mimeType,
                extension = optimized.extension,
                bytes = bytes
            )
        } finally {
            optimized.cleanup()
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun optimizeVideoForUploadStream(
        source: MediaSource,
        options: VideoUploadOptions
    ): UploadVideoPayload? {
        if (options.skipOptimize) return null
        if (!source.mimeType.startsWith("video/", ignoreCase = true)) return null
        val sourceSize = source.sizeBytes ?: 0L
        val shouldOptimize = options.force ||
            (isSlowConnectionMode() && sourceSize > options.slowConnectionRecommendedBytes) ||
            sourceSize > options.autoOptimizeBytes
        if (!shouldOptimize) return null

        val outputFile = File(
            appContext.cacheDir,
            "quata-video-${System.currentTimeMillis()}-${Random.nextInt(1_000, 9_999)}.mp4"
        )
        var keepOutputFile = false
        return try {
            val inputHeight = source.videoHeight()
            val videoEffects = if (inputHeight != null && inputHeight > options.targetHeight) {
                listOf(Presentation.createForHeight(options.targetHeight))
            } else {
                emptyList()
            }
            val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(source.uri))
                .setEffects(Effects(emptyList(), videoEffects))
                .build()
            withContext(Dispatchers.Main) {
                exportVideo(editedMediaItem, outputFile, options.targetBitrate)
            }
            val outputSize = withContext(Dispatchers.IO) { outputFile.length() }
            if (outputSize <= 0L) return null
            if (sourceSize > 0 && outputSize >= sourceSize) return null
            val baseName = source.fileName.substringBeforeLast('.', source.fileName).ifBlank { "video" }
            keepOutputFile = true
            UploadVideoPayload(
                fileName = "$baseName-quata.mp4",
                mimeType = "video/mp4",
                extension = "mp4",
                sizeBytes = outputSize,
                cleanupFile = outputFile,
                openStreamProvider = { FileInputStream(outputFile) }
            )
        } catch (_: Throwable) {
            null
        } finally {
            if (!keepOutputFile) runCatching { outputFile.delete() }
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun exportVideo(
        mediaItem: EditedMediaItem,
        outputFile: File,
        targetBitrate: Int
    ): ExportResult = suspendCancellableCoroutine { continuation ->
        val encoderFactory = DefaultEncoderFactory.Builder(appContext)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(targetBitrate)
                    .build()
            )
            .build()
        val transformer = Transformer.Builder(appContext)
            .setEncoderFactory(encoderFactory)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) continuation.resume(exportResult)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    if (continuation.isActive) continuation.resumeWithException(exportException)
                }
            })
            .build()
        continuation.invokeOnCancellation { runCatching { transformer.cancel() } }
        transformer.start(mediaItem, outputFile.absolutePath)
    }

    private fun MediaSource.videoHeight(): Int? =
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(appContext, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            } finally {
                retriever.release()
            }
        }.getOrNull()

    private fun isSlowConnectionMode(): Boolean {
        val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return true
        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) return false
        val downstream = capabilities.linkDownstreamBandwidthKbps
        return downstream in 1..2_500
    }

    private fun queryProviderInfo(uri: Uri): ProviderInfo {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return runCatching {
            appContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use ProviderInfo()
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                ProviderInfo(
                    fileName = nameIndex.takeIf { it >= 0 }?.let(cursor::getString)?.takeIf { it.isNotBlank() },
                    sizeBytes = sizeIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong)
                )
            }
        }.getOrNull() ?: ProviderInfo()
    }

    private fun calculateSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sampleSize = 1
        while (max(width / sampleSize, height / sampleSize) > maxSide * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun Bitmap.orientFromExif(bytes: ByteArray): Bitmap {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return this
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun Bitmap.centerCropSquare(): Bitmap {
        val side = minOf(width, height)
        val left = (width - side) / 2
        val top = (height - side) / 2
        return Bitmap.createBitmap(this, left, top, side, side)
    }

    private fun String.uploadExtension(mimeType: String): String =
        substringAfterLast('.', mimeType.substringAfter('/', "bin"))
            .lowercase()
            .takeIf { it.isNotBlank() && it.length <= 8 }
            ?: mimeType.substringAfter('/', "bin")

    private fun MediaSource.toStreamingPayload(): UploadVideoPayload =
        UploadVideoPayload(
            fileName = fileName,
            mimeType = mimeType,
            extension = fileName.uploadExtension(mimeType),
            sizeBytes = sizeBytes,
            cleanupFile = null,
            openStreamProvider = {
                when (uri.scheme?.lowercase()) {
                    ContentResolver.SCHEME_FILE -> FileInputStream(File(uri.path ?: error("Ruta de video no valida")))
                    else -> appContext.contentResolver.openInputStream(uri)
                        ?: error("No se pudo leer el archivo seleccionado")
                }
            }
        )

    private data class ProviderInfo(
        val fileName: String? = null,
        val sizeBytes: Long? = null
    )

    private data class MediaSource(
        val uri: Uri,
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long?
    )
}
