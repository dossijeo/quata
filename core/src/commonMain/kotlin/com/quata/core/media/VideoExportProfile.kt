package com.quata.core.media

/** Platform-independent target selected by a platform capability probe. */
data class VideoExportProfile(
    val width: Int,
    val height: Int,
    val maxFrameRate: Int,
    val label: String,
    val targetBitrate: Int,
    val intermediateBitrate: Int
) {
    val aspectRatio: Float get() = width.toFloat() / height.toFloat()
}

/**
 * Shared export policy used by every post video editor edge. Native encoders can pick the
 * nearest supported backend, but the user-visible envelope stays equivalent.
 */
object QuataVideoExportPolicy {
    const val MinimumTrimMs: Long = 500L
    const val MaximumDurationMs: Long = 90_000L
    const val DefaultMaxFrameRate: Int = 30

    val sd480 = VideoExportProfile(
        width = 480,
        height = 854,
        maxFrameRate = DefaultMaxFrameRate,
        label = "480p",
        targetBitrate = 800_000,
        intermediateBitrate = 1_200_000,
    )
    val sd432Aligned = VideoExportProfile(
        width = 432,
        height = 768,
        maxFrameRate = DefaultMaxFrameRate,
        label = "432p",
        targetBitrate = 700_000,
        intermediateBitrate = 1_000_000,
    )
    val hd720 = VideoExportProfile(
        width = 720,
        height = 1280,
        maxFrameRate = DefaultMaxFrameRate,
        label = "720p",
        targetBitrate = 1_200_000,
        intermediateBitrate = 1_800_000,
    )

    val defaultProfile: VideoExportProfile = hd720
    val conservativeProfile: VideoExportProfile = sd480

    fun selectForSource(width: Int, height: Int): VideoExportProfile {
        val longSide = maxOf(width, height)
        val shortSide = minOf(width, height)
        return when {
            longSide >= 1_280 && shortSide >= 720 -> hd720
            longSide >= 854 && shortSide >= 480 -> sd480
            else -> sd432Aligned
        }
    }
}
