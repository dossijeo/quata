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
