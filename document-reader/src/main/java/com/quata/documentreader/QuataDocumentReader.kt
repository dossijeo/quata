package com.quata.documentreader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.quata.core.platform.DocumentPreviewDescriptor
import com.quata.documentreader.activity.All_Document_Reader_Activity
import com.quata.core.platform.DocumentSupport

object QuataDocumentReader {
    const val EXTRA_FILE_NAME = "com.quata.documentreader.extra.FILE_NAME"
    const val EXTRA_MIME_TYPE = "com.quata.documentreader.extra.MIME_TYPE"
    const val EXTRA_IS_DARK_MODE = "com.quata.documentreader.extra.IS_DARK_MODE"
    private const val DUPLICATE_OPEN_WINDOW_MS = 1_200L

    private var lastOpenKey: String? = null
    private var lastOpenAtMillis: Long = 0L

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

    internal fun isTextLike(fileName: String?, mimeType: String?): Boolean =
        DocumentSupport.isTextLike(fileName = fileName, mimeType = mimeType)

    internal fun extensionForMimeType(mimeType: String?): String? = DocumentSupport.extensionForMimeType(mimeType)
}
