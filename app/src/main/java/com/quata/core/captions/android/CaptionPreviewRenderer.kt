package com.quata.core.captions.android

import android.content.Context
import android.graphics.Bitmap
import com.quata.R
import com.quata.core.captions.core.CaptionDocument
import com.quata.core.captions.templates.CaptionTemplateStyle
import java.io.ByteArrayOutputStream

object CaptionPreviewRenderer {
    fun renderPreviewBitmap(
        context: Context,
        style: CaptionTemplateStyle,
        width: Int,
        height: Int
    ): Bitmap {
        val placeholder = context.getString(R.string.caption_preview_placeholder)
        val document = CaptionDocument.placeholder(placeholder)
        return CaptionBitmapRenderer().renderFrame(document, style, PreviewTimeMs, width, height)
    }

    fun renderPreviewPng(
        context: Context,
        style: CaptionTemplateStyle,
        width: Int = CaptionPreviewPngWidth,
        height: Int = CaptionPreviewPngHeight
    ): ByteArray {
        val bitmap = renderPreviewBitmap(context, style, width, height)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
    }
}

private const val PreviewTimeMs = 440L
private const val CaptionPreviewPngWidth = 1080
private const val CaptionPreviewPngHeight = 1920
