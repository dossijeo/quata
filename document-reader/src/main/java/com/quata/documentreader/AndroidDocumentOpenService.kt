package com.quata.documentreader

import android.content.Context
import android.net.Uri
import com.quata.core.platform.DocumentOpenService
import com.quata.core.platform.DocumentSupport
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult

/**
 * Android implementation of the shared document-opening boundary.
 *
 * The reader accepts `content://`, app-local `file://`, and HTTP(S) references. Content is copied
 * through [android.content.ContentResolver] by the reader before a PDF/RTF/Office activity opens
 * it, so no `file://` URI is exposed to another application. Unsupported formats remain explicit
 * instead of pretending that an external viewer is always available.
 */
class AndroidDocumentOpenService(
    context: Context,
    private val isDarkModeProvider: () -> Boolean = { false },
) : DocumentOpenService {
    private val applicationContext = context.applicationContext

    override suspend fun open(file: PlatformFile): PlatformResult<Unit> {
        val reference = file.reference.trim()
        if (reference.isEmpty()) return PlatformResult.Failure("document_reference_missing")

        val descriptor = DocumentSupport.describe(reference, file.displayName, file.mimeType)
        if (!descriptor.isPreviewable) return PlatformResult.Unsupported

        val opened = runCatching {
            QuataDocumentReader.open(
                context = applicationContext,
                uri = Uri.parse(reference),
                fileName = file.displayName,
                mimeType = file.mimeType,
                isDarkMode = isDarkModeProvider(),
            )
        }.getOrElse { error ->
            return PlatformResult.Failure(error.message ?: "android_document_open_failed")
        }
        return if (opened) PlatformResult.Success(Unit) else PlatformResult.Unsupported
    }
}
