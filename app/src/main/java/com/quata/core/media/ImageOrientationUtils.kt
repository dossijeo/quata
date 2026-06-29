package com.quata.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File

private const val DefaultImageOrientationMaxSide = 2560
private const val DefaultImageOrientationJpegQuality = 95

fun Context.normalizeImageOrientationInPlace(
    uri: Uri,
    maxSide: Int = DefaultImageOrientationMaxSide,
    jpegQuality: Int = DefaultImageOrientationJpegQuality
): Uri = runCatching {
    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching uri
    val orientation = bytes.readImageExifOrientation()
    if (orientation == ExifInterface.ORIENTATION_UNDEFINED || orientation == ExifInterface.ORIENTATION_NORMAL) {
        return@runCatching uri
    }

    val decoded = bytes.decodeSampledImage(maxSide) ?: return@runCatching uri
    val oriented = decoded.applyImageExifOrientation(orientation)
    if (oriented !== decoded) decoded.recycle()

    contentResolver.openOutputStream(uri, "wt")?.use { output ->
        oriented.compress(Bitmap.CompressFormat.JPEG, jpegQuality, output)
    }
    if (!oriented.isRecycled) oriented.recycle()
    uri
}.getOrDefault(uri)

fun Context.copyImageToFileNormalizingOrientation(
    sourceUri: Uri,
    outputFile: File,
    maxSide: Int = 4096,
    jpegQuality: Int = DefaultImageOrientationJpegQuality
): Uri {
    val bytes = contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
        ?: error("Could not open image source")
    val orientation = bytes.readImageExifOrientation()
    if (orientation == ExifInterface.ORIENTATION_UNDEFINED || orientation == ExifInterface.ORIENTATION_NORMAL) {
        outputFile.outputStream().use { output -> output.write(bytes) }
        return Uri.fromFile(outputFile)
    }

    val preservedExif = bytes.readPreservedImageExif()
    val decoded = bytes.decodeSampledImage(maxSide) ?: error("Prepared image is not decodable")
    val oriented = decoded.applyImageExifOrientation(orientation)
    if (oriented !== decoded) decoded.recycle()

    outputFile.outputStream().use { output ->
        oriented.compress(Bitmap.CompressFormat.JPEG, jpegQuality, output)
    }
    if (!oriented.isRecycled) oriented.recycle()
    outputFile.writePreservedImageExif(preservedExif)
    return Uri.fromFile(outputFile)
}

private fun ByteArray.readImageExifOrientation(): Int =
    runCatching {
        ExifInterface(ByteArrayInputStream(this))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

private fun ByteArray.decodeSampledImage(maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(this, 0, size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (maxOf(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > maxSide) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeByteArray(this, 0, size, options)
}

private fun Bitmap.applyImageExifOrientation(orientation: Int): Bitmap {
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

private fun ByteArray.readPreservedImageExif(): Map<String, String> =
    runCatching {
        val exif = ExifInterface(ByteArrayInputStream(this))
        PreservedImageExifTags.mapNotNull { tag ->
            exif.getAttribute(tag)?.let { value -> tag to value }
        }.toMap()
    }.getOrDefault(emptyMap())

private fun File.writePreservedImageExif(attributes: Map<String, String>) {
    runCatching {
        val exif = ExifInterface(absolutePath)
        attributes.forEach { (tag, value) -> exif.setAttribute(tag, value) }
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        exif.saveAttributes()
    }
}

private val PreservedImageExifTags = listOf(
    ExifInterface.TAG_DATETIME,
    ExifInterface.TAG_MAKE,
    ExifInterface.TAG_MODEL,
    ExifInterface.TAG_GPS_LATITUDE,
    ExifInterface.TAG_GPS_LATITUDE_REF,
    ExifInterface.TAG_GPS_LONGITUDE,
    ExifInterface.TAG_GPS_LONGITUDE_REF,
    ExifInterface.TAG_GPS_ALTITUDE,
    ExifInterface.TAG_GPS_ALTITUDE_REF,
    ExifInterface.TAG_GPS_TIMESTAMP,
    ExifInterface.TAG_GPS_DATESTAMP,
    ExifInterface.TAG_GPS_PROCESSING_METHOD
)
