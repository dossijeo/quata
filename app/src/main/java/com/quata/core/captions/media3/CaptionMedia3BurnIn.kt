@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.quata.core.captions.media3

import android.graphics.Bitmap
import androidx.media3.common.Effect
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import com.quata.core.captions.android.CaptionBitmapRenderer
import com.quata.core.captions.core.CaptionDocument
import com.quata.core.captions.templates.CaptionTemplateStyle

data class CaptionBurnInTrack(
    val document: CaptionDocument,
    val style: CaptionTemplateStyle,
    val outputWidth: Int,
    val outputHeight: Int
)

object CaptionMedia3BurnIn {
    fun effectsFor(track: CaptionBurnInTrack?): List<Effect> {
        if (track == null || track.document.isEmpty) return emptyList()
        return listOf(
            OverlayEffect(
                listOf(
                    CaptionBitmapOverlay(track)
                )
            )
        )
    }
}

private class CaptionBitmapOverlay(
    private val track: CaptionBurnInTrack,
    private val renderer: CaptionBitmapRenderer = CaptionBitmapRenderer()
) : BitmapOverlay() {
    private val cache = object : LinkedHashMap<Long, Bitmap>(CaptionOverlayCacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Bitmap>): Boolean {
            val shouldRemove = size > CaptionOverlayCacheSize
            if (shouldRemove) eldest.value.recycle()
            return shouldRemove
        }
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val bucketMs = (presentationTimeUs / 1000L / CaptionOverlayFrameBucketMs) * CaptionOverlayFrameBucketMs
        return cache.getOrPut(bucketMs) {
            renderer.renderFrame(
                document = track.document,
                style = track.style,
                timeMs = bucketMs,
                width = track.outputWidth,
                height = track.outputHeight
            )
        }
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings =
        OverlaySettings.Builder().build()

    override fun release() {
        cache.values.forEach { it.recycle() }
        cache.clear()
    }
}

private const val CaptionOverlayCacheSize = 36
private const val CaptionOverlayFrameBucketMs = 33L
