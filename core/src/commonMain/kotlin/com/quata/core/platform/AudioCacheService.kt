package com.quata.core.platform

/**
 * Local-file cache boundary for audio readable by a platform player.
 *
 * Cache keys are logical identifiers, not paths. Implementations must reject unsafe keys and may
 * support only local file references; downloading remote media remains a transport concern. Cache
 * lifetime is implementation-defined and callers must tolerate a cache miss.
 */
interface AudioCacheService {
    suspend fun store(cacheKey: String, file: PlatformFile): PlatformResult<PlatformFile>
    suspend fun get(cacheKey: String): PlatformResult<PlatformFile>
    suspend fun remove(cacheKey: String): PlatformResult<Unit>
}

/** Explicit fallback for targets/composition roots that have not installed a real cache. */
object UnsupportedAudioCacheService : AudioCacheService {
    override suspend fun store(cacheKey: String, file: PlatformFile): PlatformResult<PlatformFile> =
        PlatformResult.Unsupported

    override suspend fun get(cacheKey: String): PlatformResult<PlatformFile> = PlatformResult.Unsupported

    override suspend fun remove(cacheKey: String): PlatformResult<Unit> = PlatformResult.Unsupported
}
