package com.quata.documentreader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream
import java.util.Locale

object QuataDocumentPreviewRenderer {
    private const val MaxTextPreviewBytes = 20 * 1024

    fun canRender(uri: Uri, fileName: String? = null, mimeType: String? = null): Boolean =
        QuataDocumentReader.canOpen(uri, fileName, mimeType)

    fun renderFirstPage(
        context: Context,
        uri: Uri,
        fileName: String? = null,
        mimeType: String? = null,
        maxWidth: Int = 1024,
        maxHeight: Int = 1448
    ): Bitmap? {
        if (!QuataDocumentReader.canOpen(uri, fileName, mimeType)) return null
        if (isPdf(uri, fileName, mimeType)) {
            return renderPdfFirstPage(context, uri, maxWidth, maxHeight)
        }
        if (QuataDocumentReader.isTextLike(fileNameFrom(fileName, uri), mimeType)) {
            return renderTextPreview(context, uri, fileName, maxWidth, maxHeight)
        }
        return renderDocumentCover(uri, fileName, mimeType, maxWidth, maxHeight)
    }

    private fun renderPdfFirstPage(
        context: Context,
        uri: Uri,
        maxWidth: Int,
        maxHeight: Int
    ): Bitmap? =
        runCatching {
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

    private fun renderTextPreview(
        context: Context,
        uri: Uri,
        fileName: String?,
        maxWidth: Int,
        maxHeight: Int
    ): Bitmap? = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var remaining = MaxTextPreviewBytes
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (read <= 0) break
                output.write(buffer, 0, read)
                remaining -= read
            }
            String(output.toByteArray(), Charsets.UTF_8)
        } ?: return@runCatching null
        renderTextBitmap(
            title = fileNameFrom(fileName, uri) ?: "Documento",
            body = text,
            maxWidth = maxWidth,
            maxHeight = maxHeight
        )
    }.getOrNull()

    private fun renderTextBitmap(title: String, body: String, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = maxWidth.coerceAtLeast(240)
        val height = maxHeight.coerceAtLeast(340)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(253, 250, 246))

        val margin = (width * 0.075f).coerceAtLeast(28f)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(24, 19, 16)
            textSize = (width * 0.046f).coerceAtLeast(20f)
            typeface = Typeface.DEFAULT_BOLD
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(65, 55, 48)
            textSize = (width * 0.032f).coerceAtLeast(15f)
            typeface = Typeface.MONOSPACE
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 116, 22)
            strokeWidth = (width * 0.006f).coerceAtLeast(3f)
        }

        val contentWidth = (width - margin * 2).toInt().coerceAtLeast(1)
        canvas.translate(margin, margin)
        StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(2)
            .setIncludePad(false)
            .build()
            .draw(canvas)

        canvas.translate(0f, titlePaint.textSize * 2.2f)
        canvas.drawLine(0f, 0f, contentWidth.toFloat(), 0f, dividerPaint)
        canvas.translate(0f, margin * 0.7f)

        val normalizedBody = body
            .replace("\u0000", "")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .take(4_000)
            .let { if (body.length > 4_000) "$it\n..." else it }
            .ifBlank { "(sin contenido visible)" }
        canvas.save()
        canvas.clipRect(0f, 0f, contentWidth.toFloat(), height - margin * 3.4f)
        StaticLayout.Builder.obtain(normalizedBody, 0, normalizedBody.length, bodyPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.08f)
            .setIncludePad(false)
            .build()
            .draw(canvas)
        canvas.restore()
        return bitmap
    }

    private fun renderDocumentCover(
        uri: Uri,
        fileName: String?,
        mimeType: String?,
        maxWidth: Int,
        maxHeight: Int
    ): Bitmap {
        val width = maxWidth.coerceAtLeast(240)
        val height = maxHeight.coerceAtLeast(340)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(246, 241, 235))

        val margin = (width * 0.09f).coerceAtLeast(30f)
        val pagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(35, 55, 42, 30) }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 116, 22) }
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(31, 25, 21)
            textSize = (width * 0.044f).coerceAtLeast(20f)
            typeface = Typeface.DEFAULT_BOLD
        }
        val extensionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = (width * 0.09f).coerceAtLeast(34f)
            typeface = Typeface.DEFAULT_BOLD
        }

        val pageRect = RectF(margin, margin * 1.1f, width - margin, height - margin * 1.1f)
        canvas.drawRoundRect(pageRect.left + 10f, pageRect.top + 12f, pageRect.right + 10f, pageRect.bottom + 12f, 24f, 24f, shadowPaint)
        canvas.drawRoundRect(pageRect, 24f, 24f, pagePaint)
        canvas.drawRect(pageRect.left, pageRect.top, pageRect.right, pageRect.top + margin * 0.33f, accentPaint)

        val badgeSize = width * 0.32f
        val badgeRect = RectF(
            width / 2f - badgeSize / 2f,
            height * 0.27f,
            width / 2f + badgeSize / 2f,
            height * 0.27f + badgeSize
        )
        canvas.drawRoundRect(badgeRect, 26f, 26f, accentPaint)
        val extension = extensionFrom(fileName, uri)
            ?: QuataDocumentReader.extensionForMimeType(mimeType)
            ?: "DOC"
        val extensionLabel = extension.uppercase(Locale.US).take(5)
        val baseline = badgeRect.centerY() - (extensionPaint.descent() + extensionPaint.ascent()) / 2f
        canvas.drawText(extensionLabel, badgeRect.centerX(), baseline, extensionPaint)

        val title = fileNameFrom(fileName, uri) ?: "Documento"
        canvas.save()
        canvas.translate(pageRect.left + margin * 0.45f, badgeRect.bottom + margin * 0.85f)
        StaticLayout.Builder.obtain(title, 0, title.length, textPaint, (pageRect.width() - margin * 0.9f).toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(3)
            .setIncludePad(false)
            .build()
            .draw(canvas)
        canvas.restore()
        return bitmap
    }

    private fun isPdf(uri: Uri, fileName: String?, mimeType: String?): Boolean {
        val normalizedMime = mimeType?.lowercase(Locale.US)?.substringBefore(";")
        if (normalizedMime == "application/pdf") return true
        val extension = extensionFrom(fileName, uri)
        return extension?.lowercase(Locale.US) == "pdf"
    }

    private fun fileNameFrom(fileName: String?, uri: Uri): String? =
        fileName?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
                ?.substringBefore('?')
                ?.substringAfterLast('/')
                ?.takeIf { it.isNotBlank() }

    private fun extensionFrom(fileName: String?, uri: Uri): String? =
        fileNameFrom(fileName, uri)
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
}
