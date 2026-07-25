package com.quata.core.platform

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Browser reader for plain-text-like Blob and URL references produced by [BrowserFilePickerService]. */
class BrowserDocumentTextReader : DocumentTextReader {
    override suspend fun readText(file: PlatformFile, maxCharacters: Int): PlatformResult<String> {
        if (maxCharacters <= 0 || !DocumentSupport.isTextLike(file.reference, file.displayName, file.mimeType)) {
            return PlatformResult.Unsupported
        }
        return suspendCoroutine { continuation ->
            browserReadDocumentText(file.reference, maxCharacters) { state, value ->
                continuation.resume(
                    when (state) {
                        "success" -> PlatformResult.Success(value.orEmpty())
                        "unsupported" -> PlatformResult.Unsupported
                        else -> PlatformResult.Failure(value)
                    },
                )
            }
        }
    }
}

private fun browserReadDocumentText(
    reference: String,
    maxCharacters: Int,
    onResult: (String, String?) -> Unit,
): Unit = js(
    """
    (() => {
    if (typeof globalThis.fetch !== 'function') {
      onResult('unsupported', null);
      return;
    }
    globalThis.fetch(reference)
      .then((response) => {
        if (!response.ok) throw new Error(`web_document_source_${'$'}{response.status}`);
        return response.text();
      })
      .then((text) => onResult('success', text.slice(0, maxCharacters)))
      .catch((error) => onResult('failure', error?.message ?? error?.name ?? 'web_document_read_failed'));
    })()
    """,
)
