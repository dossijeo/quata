package com.quata.core.platform

/** Creates a portable image file from a decodable video; callers must tolerate unsupported codecs. */
interface VideoThumbnailService {
    suspend fun createThumbnail(video: PlatformFile, maxWidth: Int = DefaultMaxWidth): PlatformResult<PlatformFile>

    companion object {
        const val DefaultMaxWidth = 640
    }
}

/**
 * Shared admission policy for platform video thumbnail generators.
 *
 * Thumbnail extraction never implies a download: adapters that work with native files must reject
 * remote and provider-owned references instead of silently moving media across a platform
 * boundary. Browser adapters may still accept Blob URLs because the browser owns those files.
 */
object VideoThumbnailSupport {
    fun isVideo(video: PlatformFile): Boolean =
        video.mimeType?.startsWith("video/", ignoreCase = true) == true ||
            (video.displayName ?: video.reference.substringBefore('?'))
                .substringAfterLast('.', "")
                ?.lowercase()
                .orEmpty() in supportedExtensions

    fun hasLocalFileReference(video: PlatformFile): Boolean {
        val reference = video.reference.trim()
        return when {
            reference.startsWith("file://") -> reference.removePrefix("file://").isNotBlank()
            reference.startsWith("/") -> true
            else -> false
        }
    }

    /**
     * Browser thumbnailing is deliberately limited to an existing browser-owned Blob URL.
     *
     * Accepting arbitrary HTTP(S), data, or provider URLs here would make thumbnail rendering an
     * implicit cross-origin fetch/decoder request. Callers that receive remote media must first
     * obtain it through the explicit cache/download flow, which returns a Blob URL.
     */
    fun hasBrowserReadableReference(video: PlatformFile): Boolean =
        video.reference.trim().let { reference ->
            reference.startsWith("blob:", ignoreCase = true) && reference.length > "blob:".length
        }

    private val supportedExtensions = setOf("mp4", "m4v", "mov", "webm", "ogv")
}

object UnsupportedVideoThumbnailService : VideoThumbnailService {
    override suspend fun createThumbnail(video: PlatformFile, maxWidth: Int): PlatformResult<PlatformFile> = PlatformResult.Unsupported
}
