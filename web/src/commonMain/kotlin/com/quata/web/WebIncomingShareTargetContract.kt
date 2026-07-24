package com.quata.web

import com.quata.feature.externalshare.ExternalShareAttachment
import com.quata.feature.externalshare.ExternalSharePayload

/**
 * Platform-independent contract for a Web Share Target request.
 *
 * The service worker owns the real IndexedDB write because it is the browser context receiving
 * the POST. The Wasm launcher uses this contract when it reconstructs and discards that same
 * stored record. Keeping the validation and normalization here makes the browser boundary
 * independently testable without replacing IndexedDB with a fake store.
 */
internal object WebIncomingShareTargetContract {
    const val maxFiles: Int = 8
    const val maxFileBytes: Long = 25L * 1024L * 1024L

    fun normalizeText(title: String?, text: String?, url: String?): String =
        listOfNotNull(title, text, url)
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString(separator = "\n")

    fun validate(text: String, files: List<WebIncomingShareFile>): WebIncomingShareValidation = when {
        text.isBlank() && files.isEmpty() -> WebIncomingShareValidation.Empty
        files.size > maxFiles -> WebIncomingShareValidation.TooManyFiles
        files.any { it.byteCount < 0L || it.byteCount > maxFileBytes } -> WebIncomingShareValidation.FileTooLarge
        else -> WebIncomingShareValidation.Accepted
    }

    fun payloadOrNull(record: WebPersistedIncomingShare): ExternalSharePayload? {
        val normalizedText = normalizeText(title = null, text = record.text, url = null)
        val attachments = record.attachments.mapNotNull { attachment ->
            attachment.uri.trim().takeIf(String::isNotBlank)?.let { uri ->
                ExternalShareAttachment(
                    uri = uri,
                    name = attachment.name.trim().ifBlank { "attachment" },
                    mimeType = attachment.mimeType?.trim()?.ifBlank { null },
                )
            }
        }
        return ExternalSharePayload(id = record.id.trim(), text = normalizedText, attachments = attachments)
            .takeIf { it.id.isNotBlank() && (it.text.isNotBlank() || it.attachments.isNotEmpty()) }
    }

    /** Only object URLs created by [WebIncomingShareStore] are released on discard. */
    fun blobUrlsToRevoke(payload: ExternalSharePayload): List<String> =
        payload.attachments.map(ExternalShareAttachment::uri).filter { it.startsWith("blob:") }
}

internal data class WebIncomingShareFile(val byteCount: Long)

internal sealed interface WebIncomingShareValidation {
    data object Accepted : WebIncomingShareValidation
    data object Empty : WebIncomingShareValidation
    data object TooManyFiles : WebIncomingShareValidation
    data object FileTooLarge : WebIncomingShareValidation
}

internal data class WebPersistedIncomingShare(
    val id: String,
    val text: String,
    val attachments: List<WebPersistedIncomingShareAttachment>,
)

internal data class WebPersistedIncomingShareAttachment(
    val uri: String,
    val name: String,
    val mimeType: String?,
)
