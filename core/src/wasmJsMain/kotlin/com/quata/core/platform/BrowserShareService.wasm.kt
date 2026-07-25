@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.core.platform

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Browser implementation backed by the Web Share API, including picker-produced Blob URLs. */
class BrowserShareService : ShareService {
    override suspend fun share(payload: SharePayload): PlatformResult<Unit> {
        val safeText = BrowserSharePolicy.safeTextOrNull(payload.text)
        val url = BrowserSharePolicy.webUrlOrNull(payload.text)
        if (!BrowserSharePolicy.hasShareableContent(payload, safeText, url)) {
            return PlatformResult.Failure("share_payload_empty")
        }
        if (!BrowserSharePolicy.hasSafeBrowserFileReferences(payload.files)) {
            // A PlatformFile may legitimately contain a content:// URI on Android.  Never turn
            // that capability into browser text/network traffic: only browser-minted Blob URLs
            // can be materialized as Web Share files.
            return PlatformResult.Unsupported
        }
        return suspendCoroutine { continuation ->
            browserShare(
                title = payload.title,
                text = safeText.takeUnless { url != null },
                url = url,
                fileReferences = encodeStrings(payload.files.map { it.reference }),
                fileNames = encodeStrings(payload.files.map { it.displayName.orEmpty() }),
                fileMimeTypes = encodeStrings(payload.files.map { it.mimeType.orEmpty() }),
                onShared = { continuation.resume(PlatformResult.Success(Unit)) },
                onCancelled = { continuation.resume(PlatformResult.Cancelled) },
                onUnsupported = { continuation.resume(PlatformResult.Unsupported) },
                onFailure = { reason -> continuation.resume(PlatformResult.Failure(reason)) },
            )
        }
    }
}

/**
 * Deterministic input policy which can be tested without browser APIs.  The browser performs a
 * second URL-origin validation immediately before fetching/opening a Blob capability.
 */
object BrowserSharePolicy {
    fun webUrlOrNull(text: String?): String? {
        val candidate = text?.trim().orEmpty()
        if (candidate.isEmpty() || candidate.any { it.isWhitespace() || it.code in 0..31 || it.code == 127 }) return null
        val schemeEnd = candidate.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = candidate.substring(0, schemeEnd).lowercase()
        if (scheme != "https" && scheme != "http") return null
        val authorityEnd = candidate.indexOfAny(charArrayOf('/', '?', '#'), startIndex = schemeEnd + 3)
            .let { if (it < 0) candidate.length else it }
        val authority = candidate.substring(schemeEnd + 3, authorityEnd)
        return candidate.takeIf { authority.isNotBlank() && '@' !in authority }
    }

    /** A standalone local/custom URI must never be shared as text by the browser adapter. */
    fun safeTextOrNull(text: String?): String? {
        val candidate = text?.trim().orEmpty()
        if (candidate.isEmpty()) return null
        val schemeEnd = candidate.indexOf("://")
        val looksLikeUri = candidate.startsWith("blob:", ignoreCase = true) ||
            (schemeEnd > 0 && candidate.substring(0, schemeEnd).all { it.isLetterOrDigit() || it in "+-." })
        return candidate.takeUnless { looksLikeUri && webUrlOrNull(candidate) == null }
    }

    fun hasSafeBrowserFileReferences(files: List<PlatformFile>): Boolean =
        files.all { file ->
            val reference = file.reference.trim()
            reference.startsWith("blob:", ignoreCase = true) && reference.length > "blob:".length
        }

    fun hasShareableContent(payload: SharePayload, safeText: String?, url: String?): Boolean =
        payload.title?.isNotBlank() == true || safeText != null || url != null || payload.files.isNotEmpty()
}

private fun encodeStrings(values: List<String>): String =
    Json.encodeToString(ListSerializer(String.serializer()), values)

private fun browserShare(
    title: String?,
    text: String?,
    url: String?,
    fileReferences: String,
    fileNames: String,
    fileMimeTypes: String,
    onShared: () -> Unit,
    onCancelled: () -> Unit,
    onUnsupported: () -> Unit,
    onFailure: (String?) -> Unit,
): Unit = js(
    """
    (() => {
    const references = JSON.parse(fileReferences);
    const names = JSON.parse(fileNames);
    const mimeTypes = JSON.parse(fileMimeTypes);
    let finished = false;
    const finish = (callback, value) => {
      if (finished) return;
      finished = true;
      callback(value);
    };
    const finishError = (error) => {
      if (error?.name === 'AbortError') finish(onCancelled);
      else if (error?.name === 'NotSupportedError') fallback();
      // TypeError means the browser rejected data; it is not a user cancellation and must not
      // be reported as one. A safe text/Blob fallback may still complete the user action.
      else if (error?.name === 'TypeError') fallback();
      else finish(onFailure, error?.message ?? error?.name ?? 'share_failed');
    };
    const safeUrl = (() => {
      if (!url) return null;
      try {
        const parsed = new URL(url);
        return (parsed.protocol === 'https:' || parsed.protocol === 'http:') && !parsed.username && !parsed.password
          ? parsed.href
          : null;
      } catch (_) {
        return null;
      }
    })();
    if (url && !safeUrl) {
      finish(onUnsupported);
      return;
    }
    const fallbackText = safeUrl || text || title || '';
    const downloadBlob = () => {
      if (references.length !== 1 || !globalThis.document?.body || typeof document.createElement !== 'function') return false;
      const link = document.createElement('a');
      link.href = references[0];
      link.download = names[0] || 'quata-file';
      link.rel = 'noopener noreferrer';
      link.style.display = 'none';
      document.body.appendChild(link);
      link.click();
      globalThis.setTimeout(() => link.remove(), 0);
      return true;
    };
    const openLink = () => {
      if (!safeUrl || typeof globalThis.open !== 'function') return false;
      return !!globalThis.open(safeUrl, '_blank', 'noopener,noreferrer');
    };
    const fallback = () => {
      // Do not claim that copying accompanying text shared an attachment. The fallback reports
      // success for a file payload only after the browser has actually initiated its download.
      if (references.length > 0) {
        if (downloadBlob()) finish(onShared); else finish(onUnsupported);
        return;
      }
      if (fallbackText && globalThis.navigator?.clipboard && typeof globalThis.navigator.clipboard.writeText === 'function') {
        globalThis.navigator.clipboard.writeText(fallbackText)
          .then(() => finish(onShared))
          .catch(() => (downloadBlob() || openLink()) ? finish(onShared) : finish(onUnsupported));
      } else if (downloadBlob() || openLink()) {
        finish(onShared);
      } else {
        finish(onUnsupported);
      }
    };
    const share = (files) => {
      const data = {};
      if (title != null && title.length > 0) data.title = title;
      if (text != null && text.length > 0) data.text = text;
      if (safeUrl) data.url = safeUrl;
      if (files.length > 0) {
        if (typeof globalThis.File !== 'function') { fallback(); return; }
        if (typeof globalThis.navigator?.canShare === 'function' && !globalThis.navigator.canShare({ files })) { fallback(); return; }
        data.files = files;
      }
      if (typeof globalThis.navigator?.share !== 'function') { fallback(); return; }
      globalThis.navigator.share(data).then(() => finish(onShared), finishError);
    };
    // A Blob URL is an origin-scoped capability. Reject a caller-supplied foreign-origin Blob
    // before either fetching it or placing it in an anchor fallback.
    const origin = globalThis.location?.origin;
    const referencesAreLocalBlobs = references.length === 0 || (!!origin && references.every((reference) => {
      try {
        const parsed = new URL(reference);
        return parsed.protocol === 'blob:' && parsed.origin === origin;
      } catch (_) {
        return false;
      }
    }));
    if (!referencesAreLocalBlobs) {
      finish(onUnsupported);
      return;
    }
    if (references.length === 0) {
      share([]);
    } else if (typeof globalThis.fetch !== 'function' || typeof globalThis.File !== 'function') {
      fallback();
    } else {
      Promise.all(references.map((reference, index) => globalThis.fetch(reference, { credentials: 'omit' })
        .then((response) => {
          if (!response.ok) throw new Error('share_file_unavailable');
          return response.blob();
        })
        .then((blob) => new globalThis.File(
          [blob],
          names[index] || `quata-file-${'$'}{index + 1}`,
          { type: mimeTypes[index] || blob.type || 'application/octet-stream' }
        ))
      )).then(share, finishError);
    }
    })()
    """,
)
