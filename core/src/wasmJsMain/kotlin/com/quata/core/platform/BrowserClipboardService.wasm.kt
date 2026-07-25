package com.quata.core.platform

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Browser implementation backed by the asynchronous Clipboard API. Browsers only grant
 * clipboard access in secure contexts and, commonly, after a user gesture. Reads are attempted
 * only while the browser reports a user activation or an explicit browser-granted
 * `clipboard-read` permission; denied reads are exposed as null. Writes prefer that API and can
 * use the deliberately narrow, deprecated DOM copy fallback only during a user activation. The
 * [ClipboardService] contract has no failure channel for writes, so a denied write remains a
 * harmless no-op rather than being reported as copied.
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
          const navigator = globalThis.navigator;
          const clipboard = navigator?.clipboard;
          const read = () => {
            if (!clipboard || typeof clipboard.readText !== 'function') {
              onComplete(null);
              return;
            }
            clipboard.readText().then((text) => onComplete(text)).catch(() => onComplete(null));
          };
          // Do not make a browser read request from background composition. A browser may allow
          // it only after a gesture, or after it has explicitly granted clipboard-read.
          if (navigator?.userActivation?.isActive === true) {
            read();
          } else if (typeof navigator?.permissions?.query === 'function') {
            navigator.permissions.query({ name: 'clipboard-read' })
              .then((permission) => permission.state === 'granted' ? read() : onComplete(null))
              .catch(() => onComplete(null));
          } else {
            onComplete(null);
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
          const navigator = globalThis.navigator;
          const clipboard = navigator?.clipboard;
          const legacyCopy = () => {
            // execCommand is intentionally a last resort. Requiring an active user gesture keeps
            // it from turning an asynchronous/background write into a silent clipboard change.
            if (navigator?.userActivation?.isActive !== true ||
                !globalThis.document?.body || typeof document.createElement !== 'function' ||
                typeof document.execCommand !== 'function') {
              onComplete();
              return;
            }
            const textarea = document.createElement('textarea');
            textarea.value = text;
            textarea.setAttribute('readonly', '');
            textarea.style.position = 'fixed';
            textarea.style.opacity = '0';
            textarea.style.pointerEvents = 'none';
            document.body.appendChild(textarea);
            textarea.focus();
            textarea.select();
            try {
              document.execCommand('copy');
            } catch (_) {
              // ClipboardService deliberately has no write error surface.
            } finally {
              textarea.remove();
            }
            onComplete();
          };
          if (!clipboard || typeof clipboard.writeText !== 'function') {
            legacyCopy();
          } else {
            clipboard.writeText(text).then(() => onComplete()).catch(() => legacyCopy());
          }
        } catch (_) {
          onComplete();
        }
        """
    )
}
