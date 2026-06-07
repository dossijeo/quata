package com.quata.core.media

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import androidx.annotation.RequiresApi

data class VideoExportProfile(
    val width: Int,
    val height: Int,
    val maxFrameRate: Int,
    val label: String
) {
    val aspectRatio: Float get() = width.toFloat() / height.toFloat()
}

object VideoExportSystemProfile {
    private val hd720 = VideoExportProfile(
        width = 720,
        height = 1280,
        maxFrameRate = 30,
        label = "720p"
    )
    private val fullHd1080 = VideoExportProfile(
        width = 1080,
        height = 1920,
        maxFrameRate = 30,
        label = "1080p"
    )

    @Volatile
    private var cachedProfile: VideoExportProfile? = null

    fun current(): VideoExportProfile =
        cachedProfile ?: synchronized(this) {
            cachedProfile ?: detectProfile().also { cachedProfile = it }
        }

    private fun detectProfile(): VideoExportProfile {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return hd720
        return if (supportsH264PerformancePoint()) fullHd1080 else hd720
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun supportsH264PerformancePoint(): Boolean {
        val requiredPoint = MediaCodecInfo.VideoCapabilities.PerformancePoint(1920, 1080, 60)
        return runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS)
                .codecInfos
                .asSequence()
                .filter { it.isEncoder }
                .filter { codec -> codec.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) } }
                .mapNotNull { codec -> runCatching { codec.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC) }.getOrNull() }
                .any { capabilities ->
                    val videoCapabilities = capabilities.videoCapabilities
                    val performancePoints = videoCapabilities.supportedPerformancePoints
                    if (performancePoints == null) {
                        videoCapabilities.areSizeAndRateSupported(1920, 1080, 60.0)
                    } else {
                        performancePoints.any { it.covers(requiredPoint) }
                    }
                }
        }.getOrDefault(false)
    }
}
