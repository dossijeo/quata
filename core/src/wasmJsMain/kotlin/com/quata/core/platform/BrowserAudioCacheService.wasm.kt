package com.quata.core.platform

/**
 * Audio-specific view over the real IndexedDB browser file cache.
 *
 * The cache keeps bytes in IndexedDB, while [PlatformFile] results contain short-lived Blob URLs.
 * Call [release] once a returned file is no longer rendered, uploaded or passed to a player.
 */
class BrowserAudioCacheService(
    private val files: BrowserFileCacheService = BrowserFileCacheService(),
) : AudioCacheService {
    override suspend fun store(cacheKey: String, file: PlatformFile): PlatformResult<PlatformFile> =
        files.store(cacheKey, file)

    override suspend fun get(cacheKey: String): PlatformResult<PlatformFile> = files.get(cacheKey)

    override suspend fun remove(cacheKey: String): PlatformResult<Unit> = files.remove(cacheKey)

    /** Releases only a Blob URL issued by this cache instance. */
    fun release(file: PlatformFile) = files.release(file)
}
