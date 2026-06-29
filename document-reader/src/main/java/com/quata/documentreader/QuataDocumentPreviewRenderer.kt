package com.quata.documentreader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import java.util.Locale

object QuataDocumentPreviewRenderer {
    fun canRender(uri: Uri, fileName: String? = null, mimeType: String? = null): Boolean =
        isPdf(uri, fileName, mimeType)

    fun renderFirstPage(
        context: Context,
        uri: Uri,
        fileName: String? = null,
        mimeType: String? = null,
        maxWidth: Int = 1024,
        maxHeight: Int = 1448
    ): Bitmap? {
        if (!isPdf(uri, fileName, mimeType)) return null
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                val renderer = PdfRenderer(descriptor)
                try {
                    if (renderer.pageCount <= 0) return@use null
                    val page = renderer.openPage(0)
                    try {
                        val scale = minOf(
                            maxWidth.toFloat() / page.width.toFloat(),
                            maxHeight.toFloat() / page.height.toFloat()
                        ).coerceAtMost(1f).coerceAtLeast(0.1f)
                        val bitmap = Bitmap.createBitmap(
                            (page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    } finally {
                        page.close()
                    }
                } finally {
                    renderer.close()
                }
            }
        }.getOrNull()
    }

    private fun isPdf(uri: Uri, fileName: String?, mimeType: String?): Boolean {
        val normalizedMime = mimeType?.lowercase(Locale.US)?.substringBefore(";")
        if (normalizedMime == "application/pdf") return true
        val extension = fileName?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
                ?.substringBefore('?')
                ?.substringAfterLast('.', missingDelimiterValue = "")
                ?.takeIf { it.isNotBlank() }
        return extension?.lowercase(Locale.US) == "pdf"
    }
}
