package com.quata.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSTemporaryDirectory
import platform.QuickLookThumbnailing.QLThumbnailGenerationRequest
import platform.QuickLookThumbnailing.QLThumbnailGenerationRequestRepresentationTypeThumbnail
import platform.QuickLookThumbnailing.QLThumbnailGenerator
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIScreen
import kotlin.coroutines.resume

/**
 * Quick Look Thumbnailing adapter for local PDF, RTF and Office files.
 *
 * Quick Look owns format decoding; this adapter only materializes its image as a temporary PNG
 * [PlatformFile]. The caller owns the resulting temporary file lifecycle. Remote URLs are not
 * downloaded implicitly because their authenticated transport belongs to the feature host.
 */
@OptIn(ExperimentalForeignApi::class)
class IosDocumentThumbnailService : DocumentThumbnailService {
    private val generator: QLThumbnailGenerator = QLThumbnailGenerator.sharedGenerator()

    override suspend fun createThumbnail(
        document: PlatformFile,
        maxWidth: Int,
    ): PlatformResult<PlatformFile> {
        if (maxWidth <= 0) return PlatformResult.Failure("invalid_thumbnail_width")
        if (!DocumentThumbnailSupport.supports(document)) {
            return PlatformResult.Unsupported
        }
        if (!DocumentThumbnailSupport.hasLocalReference(document)) return PlatformResult.Unsupported
        val source = iosDocumentLocalUrlOrNull(document.reference) ?: return PlatformResult.Unsupported
        val sourcePath = source.path ?: return PlatformResult.Failure("document_thumbnail_source_path_missing")
        if (!NSFileManager.defaultManager.fileExistsAtPath(sourcePath)) {
            return PlatformResult.Failure("document_thumbnail_source_missing")
        }

        val request = QLThumbnailGenerationRequest(
            fileAtURL = source,
            size = CGSizeMake(maxWidth.toDouble(), (maxWidth * 1.414f).toDouble()),
            scale = UIScreen.mainScreen.scale,
            representationTypes = QLThumbnailGenerationRequestRepresentationTypeThumbnail,
        )
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { generator.cancelRequest(request) }
            generator.generateBestRepresentationForRequest(request) { representation, error ->
                if (!continuation.isActive) return@generateBestRepresentationForRequest
                // Kotlin/Native exposes Quick Look's Objective-C `CGImage` getter verbatim.
                // `uiImage` is a Swift-only spelling and is not part of the generated bindings.
                val png = representation?.CGImage
                    ?.let { image -> UIImagePNGRepresentation(UIImage.imageWithCGImage(image)) }
                val result = when {
                    error != null -> PlatformResult.Failure(error.localizedDescription)
                    png == null -> PlatformResult.Failure("document_thumbnail_generation_failed")
                    else -> png.toTemporaryThumbnailFile(document)
                }
                continuation.resume(result)
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun platform.Foundation.NSData.toTemporaryThumbnailFile(
    source: PlatformFile,
): PlatformResult<PlatformFile> {
    val sourceStem = source.displayName
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?.substringBeforeLast('.', missingDelimiterValue = "document")
        ?.takeIf(String::isNotBlank)
        ?: "document"
    val safeStem = sourceStem.map { character ->
        if (character.isLetterOrDigit() || character == '-' || character == '_') character else '_'
    }.joinToString("").take(64).ifBlank { "document" }
    val destination = NSURL.fileURLWithPath(
        NSTemporaryDirectory() + "quata_document_thumbnail_${safeStem}_${kotlin.random.Random.nextLong().toString(16)}.png",
    )
    val path = destination.path ?: return PlatformResult.Failure("document_thumbnail_destination_path_missing")
    if (!NSFileManager.defaultManager.createFileAtPath(path, this, null)) {
        return PlatformResult.Failure("document_thumbnail_write_failed")
    }
    return PlatformResult.Success(
        PlatformFile(
            reference = destination.absoluteString ?: path,
            displayName = destination.lastPathComponent,
            mimeType = "image/png",
            sizeBytes = length.toLong(),
        ),
    )
}
