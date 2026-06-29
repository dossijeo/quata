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
    val label: String,
    val targetBitrate: Int,
    val intermediateBitrate: Int
) {
    val aspectRatio: Float get() = width.toFloat() / height.toFloat()
}

object VideoExportSystemProfile {
    private val sd480 = VideoExportProfile(
        width = 480,
        height = 854,
        maxFrameRate = 30,
        label = "480p",
        targetBitrate = 800_000,
        intermediateBitrate = 1_200_000
    )
    private val sd432Aligned = VideoExportProfile(
        width = 432,
        height = 768,
        maxFrameRate = 30,
        label = "432p",
        targetBitrate = 700_000,
        intermediateBitrate = 1_000_000
    )
    private val hd720 = VideoExportProfile(
        width = 720,
        height = 1280,
        maxFrameRate = 30,
        label = "720p",
        targetBitrate = 1_200_000,
        intermediateBitrate = 1_800_000
    )

    @Volatile
    private var cachedProfile: VideoExportProfile? = null

    fun current(): VideoExportProfile =
        cachedProfile ?: synchronized(this) {
            cachedProfile ?: detectProfile().also { cachedProfile = it }
    }

    private fun detectProfile(): VideoExportProfile {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) return sd432Aligned
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return sd480
        return if (supportsH264PerformancePoint()) hd720 else sd480
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun supportsH264PerformancePoint(): Boolean {
        val requiredPoint = MediaCodecInfo.VideoCapabilities.PerformancePoint(1280, 720, 30)
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
                        videoCapabilities.areSizeAndRateSupported(1280, 720, 30.0)
                    } else {
                        performancePoints.any { it.covers(requiredPoint) }
                    }
                }
        }.getOrDefault(false)
    }
}
