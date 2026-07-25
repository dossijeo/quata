package com.quata.documentreader

import android.content.Context
import com.quata.core.platform.AndroidDocumentOpenHost
import com.quata.core.platform.AndroidDocumentOpenRequest
import com.quata.core.platform.PlatformResult

/**
 * Adapter from the core Android document boundary to Quata's existing internal reader.
 *
 * URI and MIME policy stays in `AndroidDocumentOpenService` in :core. This module only launches
 * the established reader, preserving its PDF/RTF/Office handling rather than duplicating it.
 */
class QuataDocumentReaderOpenHost(
    context: Context,
    private val isDarkModeProvider: () -> Boolean = { false },
) : AndroidDocumentOpenHost {
    private val applicationContext = context.applicationContext

    override suspend fun open(request: AndroidDocumentOpenRequest): PlatformResult<Unit> {
        val opened = runCatching {
            QuataDocumentReader.open(
                context = applicationContext,
                uri = request.uri,
                fileName = request.displayName,
                mimeType = request.mimeType,
                isDarkMode = isDarkModeProvider(),
            )
        }.getOrElse { error ->
            return PlatformResult.Failure(error.message ?: "android_document_open_failed")
        }
        return if (opened) PlatformResult.Success(Unit) else PlatformResult.Unsupported
    }
}
