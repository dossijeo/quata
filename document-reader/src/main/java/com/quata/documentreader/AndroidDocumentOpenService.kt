package com.quata.documentreader

import android.content.Context
import android.content.Intent
import android.widget.Toast
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
    private val bridge = QuataDocumentReaderOpenBridge(
        launchReader = { request ->
            QuataDocumentReader.open(
                context = applicationContext,
                uri = request.uri,
                fileName = request.displayName,
                mimeType = request.mimeType,
                isDarkMode = isDarkModeProvider(),
            )
        },
        launchChooser = { request -> applicationContext.openWithSystemChooser(request) },
    )

    override suspend fun open(request: AndroidDocumentOpenRequest): PlatformResult<Unit> {
        return bridge.open(request)
    }

    private fun Context.openWithSystemChooser(request: AndroidDocumentOpenRequest): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(request.uri, request.mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            startActivity(
                Intent.createChooser(intent, request.displayName ?: "document")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.onFailure {
            Toast.makeText(this, request.displayName ?: "document", Toast.LENGTH_SHORT).show()
        }.getOrDefault(false)
    }
}

/**
 * Small testable boundary around the vendored renderer. URI admission and FileProvider conversion
 * happen in :core before this point; this bridge never creates a `file://` reference or guesses
 * Office MIME types. It merely maps the established renderer result into the shared contract.
 */
internal class QuataDocumentReaderOpenBridge(
    private val launchReader: (AndroidDocumentOpenRequest) -> Boolean,
    private val launchChooser: (AndroidDocumentOpenRequest) -> Boolean,
) {
    fun open(request: AndroidDocumentOpenRequest): PlatformResult<Unit> {
        val readerOpened = runCatching { launchReader(request) }.getOrDefault(false)
        if (readerOpened) {
            return PlatformResult.Success(Unit)
        }
        return runCatching {
            if (launchChooser(request)) {
                PlatformResult.Success(Unit)
            } else {
                PlatformResult.Unsupported
            }
        }.getOrElse { error ->
            PlatformResult.Failure(error.message ?: "android_document_open_failed")
        }
    }
}
