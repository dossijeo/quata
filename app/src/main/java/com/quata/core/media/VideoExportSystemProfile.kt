package com.quata.core.media

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import androidx.annotation.RequiresApi

object VideoExportSystemProfile {
    @Volatile
    private var cachedProfile: VideoExportProfile? = null

    fun current(): VideoExportProfile =
        cachedProfile ?: synchronized(this) {
            cachedProfile ?: detectProfile().also { cachedProfile = it }
    }

    fun forSource(width: Int, height: Int): VideoExportProfile {
        val systemProfile = current()
        val sourceProfile = QuataVideoExportPolicy.selectForSource(width, height)
        return if (systemProfile.width * systemProfile.height < sourceProfile.width * sourceProfile.height) {
            systemProfile
        } else {
            sourceProfile
        }
    }

    private fun detectProfile(): VideoExportProfile {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) return QuataVideoExportPolicy.sd432Aligned
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return QuataVideoExportPolicy.conservativeProfile
        return if (supportsH264PerformancePoint()) QuataVideoExportPolicy.defaultProfile else QuataVideoExportPolicy.conservativeProfile
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
                    val videoCapabilities = capabilities.videoCapabilities ?: return@any false
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
