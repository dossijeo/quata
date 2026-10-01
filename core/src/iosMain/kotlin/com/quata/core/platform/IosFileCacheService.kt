package com.quata.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSURL

/** Persistent, app-private binary storage for files that must survive an iOS process restart. */
@OptIn(ExperimentalForeignApi::class)
class IosFileCacheService : FileCacheService {
    private val manager = NSFileManager.defaultManager

    override suspend fun store(cacheKey: String, file: PlatformFile): PlatformResult<PlatformFile> {
        val destination = cacheUrl(cacheKey) ?: return PlatformResult.Failure("file_cache_key_invalid")
        val source = file.localFileUrlOrNull() ?: return PlatformResult.Unsupported
        val sourcePath = source.path ?: return PlatformResult.Failure("file_cache_source_path_missing")
        if (!manager.fileExistsAtPath(sourcePath)) return PlatformResult.Failure("file_cache_source_missing")
        val directory = destination.URLByDeletingLastPathComponent?.path
            ?: return PlatformResult.Failure("file_cache_directory_missing")
        if (!manager.fileExistsAtPath(directory) && !manager.createDirectoryAtPath(
                directory,
                withIntermediateDirectories = true,
                attributes = protectedFileAttributes(),
                error = null,
            )
        ) return PlatformResult.Failure("file_cache_directory_create_failed")
        val destinationPath = destination.path ?: return PlatformResult.Failure("file_cache_destination_path_missing")
        if (manager.fileExistsAtPath(destinationPath) && !manager.removeItemAtURL(destination, null)) {
            return PlatformResult.Failure("file_cache_replace_failed")
        }
        if (!manager.copyItemAtURL(source, destination, null)) return PlatformResult.Failure("file_cache_write_failed")
        return PlatformResult.Success(destination.toPlatformFile(file))
    }

    override suspend fun get(cacheKey: String): PlatformResult<PlatformFile> {
        val destination = cacheUrl(cacheKey) ?: return PlatformResult.Failure("file_cache_key_invalid")
        val path = destination.path ?: return PlatformResult.Failure("file_cache_destination_path_missing")
        return if (manager.fileExistsAtPath(path)) PlatformResult.Success(destination.toPlatformFile())
        else PlatformResult.Failure("file_cache_miss")
    }

    override suspend fun remove(cacheKey: String): PlatformResult<Unit> {
        val destination = cacheUrl(cacheKey) ?: return PlatformResult.Failure("file_cache_key_invalid")
        val path = destination.path ?: return PlatformResult.Failure("file_cache_destination_path_missing")
        return if (!manager.fileExistsAtPath(path) || manager.removeItemAtURL(destination, null)) {
            PlatformResult.Success(Unit)
        } else {
            PlatformResult.Failure("file_cache_remove_failed")
        }
    }

    private fun cacheUrl(cacheKey: String): NSURL? {
        val safeKey = cacheKey.trim().takeIf(::isSafeFileCacheKey) ?: return null
        val root = NSHomeDirectory().trimEnd('/') + "/Library/Application Support/Quata/ChatOutbox"
        return NSURL.fileURLWithPath("$root/$safeKey.bin")
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun PlatformFile.localFileUrlOrNull(): NSURL? {
    val value = reference.trim()
    val url = when {
        value.startsWith("file://") -> NSURL(string = value)
        value.startsWith("/") -> NSURL.fileURLWithPath(value)
        else -> null
    }
    return url?.takeIf { it.isFileURL() }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSURL.toPlatformFile(metadata: PlatformFile? = null): PlatformFile = PlatformFile(
    reference = absoluteString ?: path.orEmpty(),
    displayName = metadata?.displayName,
    mimeType = metadata?.mimeType,
    sizeBytes = metadata?.sizeBytes,
)

private fun protectedFileAttributes(): Map<Any?, *> =
    mapOf(NSFileProtectionKey to NSFileProtectionCompleteUntilFirstUserAuthentication)

private fun isSafeFileCacheKey(value: String): Boolean =
    value.isNotEmpty() && value.length <= 120 && value.all { char ->
        char.isLetterOrDigit() || char == '-' || char == '_' || char == '.' || char == ':'
    }
