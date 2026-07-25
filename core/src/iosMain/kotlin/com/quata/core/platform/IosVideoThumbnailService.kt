package com.quata.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVURLAsset
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSData
import platform.Foundation.NSError
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
        if (maxWidth <= 0) return PlatformResult.Failure("invalid_thumbnail_width")
        if (!VideoThumbnailSupport.isVideo(video)) return PlatformResult.Unsupported
        if (!VideoThumbnailSupport.hasLocalFileReference(video)) return PlatformResult.Unsupported

        val source = video.localVideoFileUrlOrNull() ?: return PlatformResult.Unsupported
        val sourcePath = source.path ?: return PlatformResult.Failure("video_thumbnail_source_path_missing")
        if (!NSFileManager.defaultManager.fileExistsAtPath(sourcePath)) {
            return PlatformResult.Failure("video_thumbnail_source_missing")
        }

        return memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val generator = AVAssetImageGenerator(AVURLAsset(URL = source, options = null)).apply {
                appliesPreferredTrackTransform = true
                maximumSize = CGSizeMake(maxWidth.toDouble(), maxWidth.toDouble())
            }
            val image = generator.copyCGImageAtTime(
                requestedTime = CMTimeMakeWithSeconds(0.0, 600),
                actualTime = null,
                error = error.ptr,
            )
            when {
                image == null -> PlatformResult.Failure(
                    error.value?.localizedDescription ?: "video_thumbnail_decode_failed",
                )
                else -> UIImagePNGRepresentation(UIImage.imageWithCGImage(image))
                    ?.toTemporaryVideoThumbnailFile(video)
                    ?: PlatformResult.Failure("video_thumbnail_encode_failed")
            }
        }
    }
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
