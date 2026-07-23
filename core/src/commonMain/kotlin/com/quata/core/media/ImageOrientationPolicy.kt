package com.quata.core.media

enum class ImageOrientation { Normal, Rotate90, Rotate180, Rotate270, FlipHorizontal, FlipVertical, Transpose, Transverse }

data class ImageTransform(val rotationDegrees: Float = 0f, val flipHorizontally: Boolean = false, val flipVertically: Boolean = false)

fun ImageOrientation.transform(): ImageTransform = when (this) {
    ImageOrientation.Normal -> ImageTransform()
    ImageOrientation.Rotate90 -> ImageTransform(rotationDegrees = 90f)
    ImageOrientation.Rotate180 -> ImageTransform(rotationDegrees = 180f)
    ImageOrientation.Rotate270 -> ImageTransform(rotationDegrees = 270f)
    ImageOrientation.FlipHorizontal -> ImageTransform(flipHorizontally = true)
    ImageOrientation.FlipVertical -> ImageTransform(flipVertically = true)
    ImageOrientation.Transpose -> ImageTransform(rotationDegrees = 90f, flipHorizontally = true)
    ImageOrientation.Transverse -> ImageTransform(rotationDegrees = 270f, flipHorizontally = true)
}

fun imageSampleSize(sourceWidth: Int, sourceHeight: Int, maxSide: Int): Int {
    if (sourceWidth <= 0 || sourceHeight <= 0 || maxSide <= 0) return 1
    var sampleSize = 1
    while (maxOf(sourceWidth / sampleSize, sourceHeight / sampleSize) > maxSide) sampleSize *= 2
    return sampleSize
}
