package com.quata.documentreader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.quata.core.platform.DocumentPreviewDescriptor
import com.quata.documentreader.activity.All_Document_Reader_Activity
import com.quata.core.platform.DocumentSupport
import java.io.File

object QuataDocumentReader {
    const val EXTRA_FILE_NAME = "com.quata.documentreader.extra.FILE_NAME"
    const val EXTRA_MIME_TYPE = "com.quata.documentreader.extra.MIME_TYPE"
    const val EXTRA_IS_DARK_MODE = "com.quata.documentreader.extra.IS_DARK_MODE"
    const val EXTRA_FALLBACK_URI = "com.quata.documentreader.extra.FALLBACK_URI"
    const val EXTRA_OWNED_TEMP_PATH = "com.quata.documentreader.extra.OWNED_TEMP_PATH"

    fun canOpen(uri: Uri, fileName: String? = null, mimeType: String? = null): Boolean {
        return previewDescriptor(uri, fileName, mimeType).isPreviewable
    }

    internal fun previewDescriptor(
        uri: Uri,
        fileName: String? = null,
        mimeType: String? = null,
    ): DocumentPreviewDescriptor = DocumentSupport.describe(uri.toString(), fileName, mimeType)

    fun open(
        context: Context,
        uri: Uri,
        fileName: String? = null,
        mimeType: String? = null,
        isDarkMode: Boolean? = null
    ): Boolean {
        if (!canOpen(uri, fileName, mimeType)) return false
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
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            // A URI permission is meaningful only for a content provider. Never attach a grant
            // flag to an HTTP(S) document hand-off.
            if (uri.scheme.equals("content", ignoreCase = true)) {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
        return true
    }

    internal fun isTextLike(fileName: String?, mimeType: String?): Boolean =
        DocumentSupport.isTextLike(fileName = fileName, mimeType = mimeType)

    internal fun extensionForMimeType(mimeType: String?): String? = DocumentSupport.extensionForMimeType(mimeType)

    @JvmStatic
    fun cleanupOwnedTempFile(context: Context, path: String?) {
        val file = ownedTempFileOrNull(context, path) ?: return
        runCatching { file.delete() }
    }

    @JvmStatic
    fun pruneOwnedTempFiles(context: Context) {
        val directory = File(context.cacheDir, DocumentReaderTempDirectory)
        val now = System.currentTimeMillis()
        val files = directory.listFiles()?.filter { it.isFile }.orEmpty()
        files
            .filter { now - it.lastModified() > DocumentReaderTempMaxAgeMillis }
            .forEach { runCatching { it.delete() } }
        directory.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.lastModified() }
            ?.let { remaining ->
                var totalBytes = remaining.sumOf { it.length().coerceAtLeast(0L) }
                for (file in remaining) {
                    if (totalBytes <= DocumentReaderTempMaxBytes) break
                    val size = file.length().coerceAtLeast(0L)
                    if (runCatching { file.delete() }.getOrDefault(false)) {
                        totalBytes -= size
                    }
                }
            }
    }

    @JvmStatic
    fun isOwnedTempFile(context: Context, path: String?): Boolean =
        ownedTempFileOrNull(context, path) != null

    private fun ownedTempFileOrNull(context: Context, path: String?): File? {
        if (path.isNullOrBlank()) return null
        val directory = runCatching { File(context.cacheDir, DocumentReaderTempDirectory).canonicalFile }.getOrNull() ?: return null
        val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        return file.takeIf { it.path.startsWith(directory.path + File.separator) }
    }

    private const val DocumentReaderTempDirectory = "quata_document_reader"
    private const val DocumentReaderTempMaxBytes = 150L * 1024L * 1024L
    private const val DocumentReaderTempMaxAgeMillis = 6L * 60L * 60L * 1_000L
}
