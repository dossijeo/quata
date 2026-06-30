package com.quata.documentreader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.quata.documentreader.activity.All_Document_Reader_Activity
import java.util.Locale

object QuataDocumentReader {
    const val EXTRA_FILE_NAME = "com.quata.documentreader.extra.FILE_NAME"
    const val EXTRA_MIME_TYPE = "com.quata.documentreader.extra.MIME_TYPE"
    const val EXTRA_IS_DARK_MODE = "com.quata.documentreader.extra.IS_DARK_MODE"
    private const val DUPLICATE_OPEN_WINDOW_MS = 1_200L

    private val officeExtensions = setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx")
    private val richTextExtensions = setOf("rtf")
    private val plainTextExtensions = setOf(
        "txt", "text", "csv", "log",
        "md", "markdown",
        "json", "xml", "html", "htm", "xhtml", "css",
        "js", "mjs", "cjs", "ts", "tsx", "jsx",
        "kt", "kts", "java", "gradle", "properties",
        "ini", "conf", "cfg", "yaml", "yml",
        "sql", "sh", "bat", "ps1", "svg"
    )
    private val textExtensions = richTextExtensions + plainTextExtensions
    private val supportedExtensions = officeExtensions + textExtensions + "pdf"
    private val supportedMimes = setOf(
        "application/pdf",
        "application/msword",
        "application/vnd.ms-word",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/rtf",
        "text/rtf",
        "text/csv",
        "application/csv",
        "text/plain",
        "text/html",
        "text/xml",
        "text/css",
        "text/javascript",
        "text/markdown",
        "text/yaml",
        "application/json",
        "application/xml",
        "application/xhtml+xml",
        "application/javascript",
        "application/x-javascript",
        "application/x-yaml",
        "image/svg+xml"
    )
    private var lastOpenKey: String? = null
    private var lastOpenAtMillis: Long = 0L

    fun canOpen(uri: Uri, fileName: String? = null, mimeType: String? = null): Boolean {
        extensionFrom(fileName, uri)
            ?.lowercase(Locale.US)
            ?.let { return it in supportedExtensions }
        val normalizedMime = mimeType?.lowercase(Locale.US)?.substringBefore(";")
        return normalizedMime in supportedMimes || isPlainTextMime(normalizedMime)
    }

    fun open(
        context: Context,
        uri: Uri,
        fileName: String? = null,
        mimeType: String? = null,
        isDarkMode: Boolean? = null
    ): Boolean {
        if (!canOpen(uri, fileName, mimeType)) return false
        val openKey = "${uri.normalizeScheme()}|${fileName.orEmpty()}|${mimeType.orEmpty()}"
        val now = android.os.SystemClock.elapsedRealtime()
        synchronized(this) {
            if (openKey == lastOpenKey && now - lastOpenAtMillis < DUPLICATE_OPEN_WINDOW_MS) {
                return true
            }
            lastOpenKey = openKey
            lastOpenAtMillis = now
        }
        val intent = Intent(context, All_Document_Reader_Activity::class.java).apply {
            action = Intent.ACTION_VIEW
            if (mimeType.isNullOrBlank()) {
                data = uri
            } else {
                setDataAndType(uri, mimeType)
            }
            putExtra(EXTRA_FILE_NAME, fileName)
            putExtra(EXTRA_MIME_TYPE, mimeType)
            isDarkMode?.let { putExtra(EXTRA_IS_DARK_MODE, it) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
        return true
    }

    internal fun isTextLike(fileName: String?, mimeType: String?): Boolean {
        val normalizedMime = mimeType?.lowercase(Locale.US)?.substringBefore(";")
        val extension = fileName?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase(Locale.US)
            ?.takeIf { it.isNotBlank() }
        return extension?.let { it in plainTextExtensions } == true || isPlainTextMime(normalizedMime)
    }

    internal fun extensionForMimeType(mimeType: String?): String? =
        when (mimeType?.lowercase(Locale.US)?.substringBefore(";")) {
            "application/pdf" -> "pdf"
            "application/msword", "application/vnd.ms-word" -> "doc"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            "application/vnd.ms-excel" -> "xls"
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
            "application/vnd.ms-powerpoint" -> "ppt"
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
            "application/rtf", "text/rtf" -> "rtf"
            "text/csv", "application/csv" -> "csv"
            "text/html" -> "html"
            "text/xml", "application/xml" -> "xml"
            "application/xhtml+xml" -> "xhtml"
            "text/css" -> "css"
            "application/json" -> "json"
            "text/markdown" -> "md"
            "text/yaml", "application/x-yaml" -> "yaml"
            "text/javascript", "application/javascript", "application/x-javascript" -> "js"
            "image/svg+xml" -> "svg"
            "text/plain" -> "txt"
            else -> null
        }

    private fun isPlainTextMime(normalizedMime: String?): Boolean =
        when {
            normalizedMime == null -> false
            normalizedMime == "text/rtf" -> false
            normalizedMime.startsWith("text/") -> true
            normalizedMime in setOf(
                "application/json",
                "application/xml",
                "application/xhtml+xml",
                "application/javascript",
                "application/x-javascript",
                "application/x-yaml",
                "image/svg+xml"
            ) -> true
            else -> false
        }

    private fun extensionFrom(fileName: String?, uri: Uri): String? {
        val fromName = fileName?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
        if (fromName != null) return fromName
        return uri.lastPathSegment
            ?.substringBefore('?')
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
    }
}
