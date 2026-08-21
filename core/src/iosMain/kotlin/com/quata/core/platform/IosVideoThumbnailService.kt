package com.quata.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVURLAsset
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSTemporaryDirectory
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import kotlin.random.Random

/**
 * AVFoundation thumbnail extractor for local, decodable video files.
 *
 * It deliberately does not fetch HTTP(S), content-provider, or Photos URLs. AVFoundation codec
 * errors are returned as [PlatformResult.Failure] so a shared caller can keep its visual fallback
 * rather than treating an unavailable decoder as a successful preview.
 */
@OptIn(ExperimentalForeignApi::class)
class IosVideoThumbnailService : VideoThumbnailService {
    override suspend fun createThumbnail(video: PlatformFile, maxWidth: Int): PlatformResult<PlatformFile> {
        return createThumbnailAt(video, maxWidth, requestedTimeSeconds = 0.0)
    }

    suspend fun createThumbnailAt(
        video: PlatformFile,
        maxWidth: Int,
        requestedTimeSeconds: Double,
    ): PlatformResult<PlatformFile> {
        val input = inspectIosVideoThumbnailInput(video, maxWidth, requestedTimeSeconds)
        input.rejectionResult()?.let { return it }
        val source = input.sourceUrl?.let { url -> NSURL(string = url) }
            ?: return PlatformResult.Failure("video_thumbnail_source_path_missing")

        return runCatching {
            val generator = AVAssetImageGenerator(AVURLAsset(uRL = source, options = null)).apply {
                appliesPreferredTrackTransform = true
                maximumSize = CGSizeMake(input.maxWidth.toDouble(), input.maxWidth.toDouble())
            }
            val image = generator.copyCGImageAtTime(
                requestedTime = CMTimeMakeWithSeconds(input.requestedTimeSeconds, input.requestedTimeScale),
                actualTime = null,
                error = null,
            )
            when {
                image == null -> PlatformResult.Failure("video_thumbnail_decode_failed")
                else -> UIImagePNGRepresentation(UIImage.imageWithCGImage(image))
                    ?.toTemporaryVideoThumbnailFile(video)
                    ?: PlatformResult.Failure("video_thumbnail_encode_failed")
            }
        }.getOrElse { throwable ->
            PlatformResult.Failure(throwable.message ?: "video_thumbnail_generation_failed")
        }
    }
}

/**
 * Deterministic admission result used before AVFoundation decodes a video.
 *
 * It is deliberately exposed to the iOS host so XCTest can verify local-file and fallback
 * decisions without pretending that simulator codec support or a media fixture is deterministic.
 */
enum class IosVideoThumbnailInputStatus {
    Ready,
    InvalidThumbnailWidth,
    UnsupportedVideo,
    UnsafeLocalReference,
    SourcePathMissing,
    SourceMissing,
}

/**
 * Native thumbnail request after local-file admission. A thumbnail is always requested from the
 * first frame, at a fixed timescale, and bounded on both axes by [maxWidth].
 */
data class IosVideoThumbnailInput(
    val status: IosVideoThumbnailInputStatus,
    val sourceUrl: String? = null,
    val sourcePath: String? = null,
    val maxWidth: Int = 0,
    val requestedTimeSeconds: Double = 0.0,
    val requestedTimeScale: Int = RequestedTimeScale,
) {
    companion object {
        const val RequestedTimeScale: Int = 600
    }
}

/**
 * Prepares the AVFoundation request without creating a generator or decoding media.
 *
 * This is both the service's admission boundary and a host-test seam. In particular, remote,
 * malformed and missing files have a stable fallback before AVFoundation is invoked.
 */
@OptIn(ExperimentalForeignApi::class)
fun inspectIosVideoThumbnailInput(
    video: PlatformFile,
    maxWidth: Int,
): IosVideoThumbnailInput = inspectIosVideoThumbnailInput(
    video = video,
    maxWidth = maxWidth,
    requestedTimeSeconds = 0.0,
)

@OptIn(ExperimentalForeignApi::class)
fun inspectIosVideoThumbnailInput(
    video: PlatformFile,
    maxWidth: Int,
    requestedTimeSeconds: Double,
): IosVideoThumbnailInput {
    if (maxWidth <= 0) return IosVideoThumbnailInput(IosVideoThumbnailInputStatus.InvalidThumbnailWidth)
    if (!VideoThumbnailSupport.isVideo(video)) {
        return IosVideoThumbnailInput(IosVideoThumbnailInputStatus.UnsupportedVideo)
    }
    if (!VideoThumbnailSupport.hasLocalFileReference(video)) {
        return IosVideoThumbnailInput(IosVideoThumbnailInputStatus.UnsafeLocalReference)
    }
    val source = video.localVideoFileUrlOrNull()
        ?: return IosVideoThumbnailInput(IosVideoThumbnailInputStatus.UnsafeLocalReference)
    val sourcePath = source.path
        ?: return IosVideoThumbnailInput(IosVideoThumbnailInputStatus.SourcePathMissing)
    if (!NSFileManager.defaultManager.fileExistsAtPath(sourcePath)) {
        return IosVideoThumbnailInput(
            status = IosVideoThumbnailInputStatus.SourceMissing,
            sourceUrl = source.absoluteString,
            sourcePath = sourcePath,
            maxWidth = maxWidth,
        )
    }
    return IosVideoThumbnailInput(
        status = IosVideoThumbnailInputStatus.Ready,
        sourceUrl = source.absoluteString,
        sourcePath = sourcePath,
        maxWidth = maxWidth,
        requestedTimeSeconds = requestedTimeSeconds.coerceAtLeast(0.0),
    )
}

private fun IosVideoThumbnailInput.rejectionResult(): PlatformResult<PlatformFile>? = when (status) {
    IosVideoThumbnailInputStatus.Ready -> null
    IosVideoThumbnailInputStatus.InvalidThumbnailWidth -> PlatformResult.Failure("invalid_thumbnail_width")
    IosVideoThumbnailInputStatus.UnsupportedVideo,
    IosVideoThumbnailInputStatus.UnsafeLocalReference,
    -> PlatformResult.Unsupported
    IosVideoThumbnailInputStatus.SourcePathMissing -> PlatformResult.Failure("video_thumbnail_source_path_missing")
    IosVideoThumbnailInputStatus.SourceMissing -> PlatformResult.Failure("video_thumbnail_source_missing")
}

@OptIn(ExperimentalForeignApi::class)
private fun PlatformFile.localVideoFileUrlOrNull(): NSURL? {
    val value = reference.trim()
    val url = when {
        value.startsWith("file://") -> NSURL(string = value)
        value.startsWith("/") -> NSURL.fileURLWithPath(value)
        else -> null
    }
    return url?.takeIf { it.isFileURL() }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toTemporaryVideoThumbnailFile(source: PlatformFile): PlatformResult<PlatformFile> {
    val stem = source.displayName
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?.substringBeforeLast('.', missingDelimiterValue = "video")
        ?.takeIf(String::isNotBlank)
        ?: "video"
    val safeStem = stem.map { character ->
        if (character.isLetterOrDigit() || character == '-' || character == '_') character else '_'
    }.joinToString("").take(64).ifBlank { "video" }
    val destination = NSURL.fileURLWithPath(
        NSTemporaryDirectory() + "quata_video_thumbnail_${safeStem}_${Random.nextLong().toString(16)}.png",
    )
    val path = destination.path ?: return PlatformResult.Failure("video_thumbnail_destination_path_missing")
    if (!NSFileManager.defaultManager.createFileAtPath(path, this, null)) {
        return PlatformResult.Failure("video_thumbnail_write_failed")
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
