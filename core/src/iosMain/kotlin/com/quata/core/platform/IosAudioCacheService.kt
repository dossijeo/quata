package com.quata.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSTemporaryDirectory

/**
 * Temporary local-file cache for iOS audio handed to the shared platform boundary.
 *
 * It deliberately never downloads a remote URL: the authenticated transport/AVFoundation host
 * owns that concern. Each operation touches only a namespaced file derived from a validated key.
 */
@OptIn(ExperimentalForeignApi::class)
class IosAudioCacheService : AudioCacheService {
    override suspend fun store(cacheKey: String, file: PlatformFile): PlatformResult<PlatformFile> {
        val destination = cacheUrl(cacheKey) ?: return PlatformResult.Failure("audio_cache_key_invalid")
        val source = file.localFileUrlOrNull() ?: return PlatformResult.Unsupported
        val sourcePath = source.path ?: return PlatformResult.Failure("audio_cache_source_path_missing")
        if (!NSFileManager.defaultManager.fileExistsAtPath(sourcePath)) {
            return PlatformResult.Failure("audio_cache_source_missing")
        }
        val destinationPath = destination.path ?: return PlatformResult.Failure("audio_cache_destination_path_missing")
        val manager = NSFileManager.defaultManager
        if (manager.fileExistsAtPath(destinationPath) && !manager.removeItemAtURL(destination, null)) {
            return PlatformResult.Failure("audio_cache_replace_failed")
        }
        if (!manager.copyItemAtURL(source, destination, null)) {
            return PlatformResult.Failure("audio_cache_write_failed")
        }
        return PlatformResult.Success(destination.toPlatformFile(metadataFrom = file))
    }

    override suspend fun get(cacheKey: String): PlatformResult<PlatformFile> {
        val destination = cacheUrl(cacheKey) ?: return PlatformResult.Failure("audio_cache_key_invalid")
        val path = destination.path ?: return PlatformResult.Failure("audio_cache_destination_path_missing")
        return if (NSFileManager.defaultManager.fileExistsAtPath(path)) {
            PlatformResult.Success(destination.toPlatformFile())
        } else {
            PlatformResult.Failure("audio_cache_miss")
        }
    }

    override suspend fun remove(cacheKey: String): PlatformResult<Unit> {
        val destination = cacheUrl(cacheKey) ?: return PlatformResult.Failure("audio_cache_key_invalid")
        val path = destination.path ?: return PlatformResult.Failure("audio_cache_destination_path_missing")
        val manager = NSFileManager.defaultManager
        return if (!manager.fileExistsAtPath(path) || manager.removeItemAtURL(destination, null)) {
            PlatformResult.Success(Unit)
        } else {
            PlatformResult.Failure("audio_cache_remove_failed")
        }
    }

    private fun cacheUrl(cacheKey: String): NSURL? {
        val safeKey = cacheKey.trim().takeIf(::isSafeCacheKey) ?: return null
        return NSURL.fileURLWithPath(NSTemporaryDirectory() + "quata_audio_cache_$safeKey.bin")
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun PlatformFile.localFileUrlOrNull(): NSURL? {
    val reference = reference.trim()
    val url = when {
        reference.startsWith("file://") -> NSURL(string = reference)
        reference.startsWith("/") -> NSURL.fileURLWithPath(reference)
        else -> null
    }
    return url?.takeIf { it.isFileURL }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSURL.toPlatformFile(metadataFrom: PlatformFile? = null): PlatformFile = PlatformFile(
    reference = absoluteString ?: path.orEmpty(),
    displayName = metadataFrom?.displayName?.safeDisplayName() ?: lastPathComponent,
    mimeType = metadataFrom?.mimeType?.trim()?.takeIf(String::isNotEmpty),
    sizeBytes = metadataFrom?.sizeBytes?.takeIf { it >= 0L },
)

private fun String?.safeDisplayName(): String? = this
    ?.trim()
    ?.substringAfterLast('/')
    ?.substringAfterLast('\\')
    ?.takeIf(String::isNotEmpty)

private fun isSafeCacheKey(value: String): Boolean =
    value.isNotEmpty() && value.length <= 96 && value.all { char ->
        char.isLetterOrDigit() || char == '-' || char == '_'
    }
