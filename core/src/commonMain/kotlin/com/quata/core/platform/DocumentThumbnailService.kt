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

/**
 * Shared admission policy for thumbnail adapters.
 *
 * A document may be previewable as text without being a suitable input for an image thumbnail.
 * Keeping this distinction in common code makes every platform agree on the Quick Look / renderer
 * contract: PDF, RTF and Office documents are accepted; text-only and unknown files are not.
 */
object DocumentThumbnailSupport {
    fun supports(document: PlatformFile): Boolean = when (
        DocumentSupport.describe(
            source = document.reference,
            fileName = document.displayName,
            mimeType = document.mimeType,
        ).kind
    ) {
        DocumentPreviewKind.Pdf,
        DocumentPreviewKind.RichText,
        DocumentPreviewKind.Office,
        -> true

        DocumentPreviewKind.PlainText,
        DocumentPreviewKind.Unsupported,
        -> false
    }

    /** A thumbnail decoder may only receive a local URL/path; it never downloads a remote URI. */
    fun hasLocalReference(document: PlatformFile): Boolean {
        val reference = document.reference.trim()
        return when {
            reference.startsWith("file://") -> reference.removePrefix("file://").isNotBlank()
            reference.startsWith("/") -> true
            else -> false
        }
    }
}

object UnsupportedDocumentThumbnailService : DocumentThumbnailService {
    override suspend fun createThumbnail(
        document: PlatformFile,
        maxWidth: Int,
    ): PlatformResult<PlatformFile> = PlatformResult.Unsupported
}
