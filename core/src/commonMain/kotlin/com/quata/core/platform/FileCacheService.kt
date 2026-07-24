package com.quata.core.platform

/**
 * Persistent cache boundary for platform files. Keys are logical identifiers, never paths, and
 * callers must tolerate eviction or a cache miss.
 */
interface FileCacheService {
    suspend fun store(cacheKey: String, file: PlatformFile): PlatformResult<PlatformFile>
    suspend fun get(cacheKey: String): PlatformResult<PlatformFile>
    suspend fun remove(cacheKey: String): PlatformResult<Unit>
}

/** Explicit fallback for targets and hosts that do not provide persistent binary storage. */
object UnsupportedFileCacheService : FileCacheService {
    override suspend fun store(cacheKey: String, file: PlatformFile): PlatformResult<PlatformFile> = PlatformResult.Unsupported
    override suspend fun get(cacheKey: String): PlatformResult<PlatformFile> = PlatformResult.Unsupported
    override suspend fun remove(cacheKey: String): PlatformResult<Unit> = PlatformResult.Unsupported
}
