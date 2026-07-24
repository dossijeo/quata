package com.quata.core.platform

/** Read-only dimensions and MIME metadata for a decodable image file. */
data class ImageMetadata(
    val width: Int,
    val height: Int,
    val mimeType: String? = null,
) {
    val aspectRatio: Float get() = width.toFloat() / height.toFloat()
}

interface ImageMetadataService {
    suspend fun read(file: PlatformFile): PlatformResult<ImageMetadata>
}

object UnsupportedImageMetadataService : ImageMetadataService {
    override suspend fun read(file: PlatformFile): PlatformResult<ImageMetadata> = PlatformResult.Unsupported
}
