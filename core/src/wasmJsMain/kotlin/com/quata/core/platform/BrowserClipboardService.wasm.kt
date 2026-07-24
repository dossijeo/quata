package com.quata.core.platform

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Browser implementation backed by the asynchronous Clipboard API. Browsers only grant
 * clipboard access in secure contexts and, commonly, after a user gesture; denied reads are
 * exposed as null and denied writes remain a harmless no-op because [ClipboardService] has no
 * failure channel for writes.
 */
class BrowserClipboardService : ClipboardService {
    override suspend fun readText(): String? = suspendCoroutine { continuation ->
        browserClipboardRead { text -> continuation.resume(text) }
    }

    override suspend fun writeText(text: String) {
        suspendCoroutine { continuation ->
            browserClipboardWrite(text) { continuation.resume(Unit) }
        }
    }
}

private fun browserClipboardRead(onComplete: (String?) -> Unit) {
    js(
        """
        try {
          const clipboard = globalThis.navigator?.clipboard;
          if (!clipboard || typeof clipboard.readText !== 'function') {
            onComplete(null);
          } else {
            clipboard.readText().then((text) => onComplete(text)).catch(() => onComplete(null));
          }
        } catch (_) {
          onComplete(null);
        }
        """
    )
}

private fun browserClipboardWrite(text: String, onComplete: () -> Unit) {
    js(
        """
        try {
          const clipboard = globalThis.navigator?.clipboard;
          if (!clipboard || typeof clipboard.writeText !== 'function') {
            onComplete();
          } else {
            clipboard.writeText(text).then(() => onComplete()).catch(() => onComplete());
          }
        } catch (_) {
          onComplete();
        }
        """
    )
}
