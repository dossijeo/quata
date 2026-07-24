package com.quata.core.platform

/** Creates a portable image file from a decodable video; callers must tolerate unsupported codecs. */
interface VideoThumbnailService {
    suspend fun createThumbnail(video: PlatformFile, maxWidth: Int = DefaultMaxWidth): PlatformResult<PlatformFile>

    companion object {
        const val DefaultMaxWidth = 640
    }
}

object UnsupportedVideoThumbnailService : VideoThumbnailService {
    override suspend fun createThumbnail(video: PlatformFile, maxWidth: Int): PlatformResult<PlatformFile> = PlatformResult.Unsupported
}
