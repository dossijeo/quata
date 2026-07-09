package com.quata.core.ui.components

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import com.quata.core.media.withQuataMediaMetadataRetriever

internal fun Int.normalizedQuataVideoRotation(): Int {
    val normalized = ((this % 360) + 360) % 360
    return if (normalized == 90 || normalized == 180 || normalized == 270) normalized else 0
}

internal fun readQuataVideoRotation(context: Context, uri: Uri): Int =
    runCatching {
        withQuataMediaMetadataRetriever { retriever ->
            retriever.setQuataVideoSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?.normalizedQuataVideoRotation()
                ?: 0
        }
    }.getOrDefault(0)

private fun MediaMetadataRetriever.setQuataVideoSource(context: Context, uri: Uri) {
    when (uri.scheme?.lowercase()) {
        null, "", "content", "file", "android.resource" -> setDataSource(context, uri)
        "http", "https" -> setDataSource(uri.toString(), emptyMap())
        else -> setDataSource(context, uri)
    }
}

internal fun View.findQuataTextureView(): TextureView? {
    if (this is TextureView) return this
    if (this !is ViewGroup) return null
    for (index in 0 until childCount) {
        getChildAt(index).findQuataTextureView()?.let { return it }
    }
    return null
}

internal fun TextureView.applyQuataVideoPlaybackTransform(rotationDegrees: Int) {
    fun applyTransform() {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f) return
        val rotation = rotationDegrees.normalizedQuataVideoRotation()
        if (rotation == 90 || rotation == 270) {
            val viewRect = RectF(0f, 0f, viewWidth, viewHeight)
            val bufferRect = RectF(0f, 0f, viewHeight, viewWidth)
            val centerX = viewRect.centerX()
            val centerY = viewRect.centerY()
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            val matrix = Matrix().apply {
                setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
                postRotate(rotation.toFloat(), centerX, centerY)
            }
            setTransform(matrix)
            invalidate()
            return
        }
        if (rotation == 180) {
            setTransform(
                Matrix().apply {
                    postRotate(180f, viewWidth / 2f, viewHeight / 2f)
                }
            )
            invalidate()
            return
        }
        setTransform(Matrix())
        invalidate()
    }
    if (width > 0 && height > 0) {
        applyTransform()
    } else {
        post { applyTransform() }
    }
}
