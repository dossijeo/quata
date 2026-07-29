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
    private val bridge = QuataDocumentReaderOpenBridge { request ->
        QuataDocumentReader.open(
            context = applicationContext,
            uri = request.uri,
            fileName = request.displayName,
            mimeType = request.mimeType,
            isDarkMode = isDarkModeProvider(),
        )
    }

    override suspend fun open(request: AndroidDocumentOpenRequest): PlatformResult<Unit> {
        return bridge.open(request)
    }
}

/**
 * Small testable boundary around the vendored renderer. URI admission and FileProvider conversion
 * happen in :core before this point; this bridge never creates a `file://` reference or guesses
 * Office MIME types. It merely maps the established renderer result into the shared contract.
 */
internal class QuataDocumentReaderOpenBridge(
    private val launch: (AndroidDocumentOpenRequest) -> Boolean,
) {
    fun open(request: AndroidDocumentOpenRequest): PlatformResult<Unit> = runCatching {
        if (launch(request)) PlatformResult.Success(Unit) else PlatformResult.Unsupported
    }.getOrElse { error ->
        PlatformResult.Failure(error.message ?: "android_document_open_failed")
    }
}
