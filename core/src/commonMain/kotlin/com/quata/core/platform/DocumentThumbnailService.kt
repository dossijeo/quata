package com.quata.core.platform

/**
 * Produces a portable image file for a document preview.
 *
 * The output is deliberately a [PlatformFile], rather than a platform image object, so a shared
 * presentation can hand it to its platform image slot (for example, the `preview` slot of
 * `DocumentPreviewFrameContent`) without importing UIKit, Android graphics, or browser APIs.
 */
interface DocumentThumbnailService {
    suspend fun createThumbnail(
        document: PlatformFile,
        maxWidth: Int = DefaultMaxWidth,
    ): PlatformResult<PlatformFile>

    companion object {
        const val DefaultMaxWidth = 640
    }
}

object UnsupportedDocumentThumbnailService : DocumentThumbnailService {
    override suspend fun createThumbnail(
        document: PlatformFile,
        maxWidth: Int,
    ): PlatformResult<PlatformFile> = PlatformResult.Unsupported
}
