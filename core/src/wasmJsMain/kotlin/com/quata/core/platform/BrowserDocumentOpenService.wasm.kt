package com.quata.core.platform

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Opens PDFs in the browser's native viewer and triggers a real browser download for RTF/Office.
 * No document bytes are interpreted as plain text here.
 */
class BrowserDocumentOpenService : DocumentOpenService {
    override suspend fun open(file: PlatformFile): PlatformResult<Unit> {
        val kind = DocumentSupport.describe(file.reference, file.displayName, file.mimeType).kind
        val mode = when (kind) {
            DocumentPreviewKind.Pdf -> "view"
            DocumentPreviewKind.RichText,
            DocumentPreviewKind.Office -> "download"
            DocumentPreviewKind.PlainText,
            DocumentPreviewKind.Unsupported -> return PlatformResult.Unsupported
        }
        return suspendCoroutine { continuation ->
            browserOpenDocument(file.reference, file.displayName ?: "document", mode) { state, reason ->
                continuation.resume(
                    when (state) {
                        "success" -> PlatformResult.Success(Unit)
                        "unsupported" -> PlatformResult.Unsupported
                        else -> PlatformResult.Failure(reason)
                    },
                )
            }
        }
    }
}

private fun browserOpenDocument(
    reference: String,
    displayName: String,
    mode: String,
    onResult: (String, String?) -> Unit,
): Unit = js(
    """
    try {
      if (mode === 'view') {
        const opened = globalThis.open?.(reference, '_blank', 'noopener,noreferrer');
        onResult(opened ? 'success' : 'failure', opened ? null : 'web_document_popup_blocked');
        return;
      }
      const document = globalThis.document;
      if (!document || typeof document.createElement !== 'function') {
        onResult('unsupported', null);
        return;
      }
      const link = document.createElement('a');
      link.href = reference;
      link.download = displayName || 'document';
      link.rel = 'noopener noreferrer';
      link.style.display = 'none';
      document.body?.appendChild(link);
      link.click();
      globalThis.setTimeout(() => link.remove(), 0);
      onResult('success', null);
    } catch (error) {
      onResult('failure', error?.message ?? error?.name ?? 'web_document_open_failed');
    }
    """,
)
