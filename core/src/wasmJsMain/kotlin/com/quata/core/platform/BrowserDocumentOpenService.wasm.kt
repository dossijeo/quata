@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.core.platform

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Opens PDFs in the browser's native viewer and triggers a browser download for RTF/Office.
 *
 * The browser is the authority for rendering PDF. Office and RTF are deliberately downloaded
 * instead of being passed to an untrusted third-party viewer or interpreted as text. References
 * are normalized by the browser URL parser immediately before use; executable URL schemes and
 * cross-origin Blob URLs are rejected.
 */
class BrowserDocumentOpenService : DocumentOpenService {
    override suspend fun open(file: PlatformFile): PlatformResult<Unit> {
        val safeReference = browserDocumentReferenceOrNull(file.reference, browserCurrentOrigin())
            ?: return PlatformResult.Failure("web_document_url_scheme_not_allowed")
        val kind = DocumentSupport.describe(file.reference, file.displayName, file.mimeType).kind
        val mode = BrowserDocumentOpenPolicy.actionFor(kind, file.reference) ?: return PlatformResult.Unsupported
        return suspendCoroutine { continuation ->
            browserOpenDocument(safeReference, BrowserDocumentOpenPolicy.downloadName(file.displayName), mode) { state, reason ->
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

/**
 * Normalizes the only document references that may reach browser navigation/download APIs.
 *
 * The browser URL parser is the source of truth here, rather than a Kotlin URL implementation
 * that could diverge from the browser. [expectedOrigin] is supplied explicitly so the same
 * policy can be exercised under the Wasm test runner without changing its location.
 */
internal fun browserDocumentReferenceOrNull(reference: String, expectedOrigin: String?): String? = js(
    """
    (() => {
      const rawReference = typeof reference === 'string' ? reference.trim() : '';
      if (!rawReference || /[\u0000-\u001F\u007F]/.test(rawReference)) return null;
      try {
        const parsed = new URL(rawReference, globalThis.location?.href);
        if (!['https:', 'http:', 'blob:'].includes(parsed.protocol) || parsed.username || parsed.password) return null;
        const origin = typeof expectedOrigin === 'string' && expectedOrigin
          ? expectedOrigin
          : globalThis.location?.origin;
        if (parsed.protocol === 'blob:' && origin && parsed.origin !== origin) return null;
        return parsed.href;
      } catch (_) {
        return null;
      }
    })()
    """,
)

private fun browserCurrentOrigin(): String? = js("globalThis.location?.origin ?? null")

/**
 * Browser-specific values kept deterministic so hosts never pass path-like or control characters
 * to the download attribute. URL validation remains in [browserDocumentReferenceOrNull], where
 * the standards-compliant browser URL parser is available.
 */
object BrowserDocumentOpenPolicy {
    /**
     * Blob contents are not self-authenticating from [PlatformFile] metadata. Download a claimed
     * PDF Blob instead of navigating to it, so a Blob containing HTML cannot execute under this
     * application's origin. HTTP(S) PDFs remain eligible for the browser's native viewer.
     */
    fun actionFor(kind: DocumentPreviewKind, reference: String): String? = when (kind) {
        DocumentPreviewKind.Pdf -> if (reference.trim().startsWith("blob:", ignoreCase = true)) "download" else "view"
        DocumentPreviewKind.RichText,
        DocumentPreviewKind.Office -> "download"
        DocumentPreviewKind.PlainText,
        DocumentPreviewKind.Unsupported -> null
    }

    fun downloadName(displayName: String?): String = displayName
        .orEmpty()
        .trim()
        .replace('\\', '_')
        .replace('/', '_')
        .filterNot { it.code in 0..31 || it.code == 127 }
        .take(MaxDownloadNameLength)
        .ifBlank { DefaultDownloadName }

    private const val DefaultDownloadName = "document"
    private const val MaxDownloadNameLength = 180
}

private fun browserOpenDocument(
    reference: String,
    displayName: String,
    mode: String,
    onResult: (String, String?) -> Unit,
): Unit = js(
    """
    (() => {
    try {
      // [reference] was normalized through browserDocumentReferenceOrNull immediately before
      // this private bridge is called. Keep it as a URL parser result, never raw caller input.
      const href = reference;
      const download = () => {
        const document = globalThis.document;
        if (!document || typeof document.createElement !== 'function' || !document.body) return false;
        const link = document.createElement('a');
        link.href = href;
        link.download = displayName || 'document';
        link.rel = 'noopener noreferrer';
        link.style.display = 'none';
        document.body.appendChild(link);
        link.click();
        globalThis.setTimeout(() => link.remove(), 0);
        return true;
      };
      if (mode === 'view') {
        const opened = globalThis.open?.(href, '_blank', 'noopener,noreferrer');
        // A PDF popup can legitimately be blocked outside a direct gesture. In that case offer
        // the browser download route rather than reporting a false successful preview.
        if (opened) {
          onResult('success', null);
        } else if (download()) {
          onResult('success', 'web_document_popup_blocked_download_started');
        } else {
          onResult('failure', 'web_document_popup_blocked');
        }
        return;
      }
      onResult(download() ? 'success' : 'unsupported', null);
    } catch (error) {
      onResult('failure', error?.message ?? error?.name ?? 'web_document_open_failed');
    }
    })()
    """,
)
