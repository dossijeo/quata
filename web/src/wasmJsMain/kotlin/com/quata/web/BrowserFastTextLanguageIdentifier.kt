@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import com.quata.core.language.FastTextLanguageDetector
import com.quata.core.language.FastTextTextLanguageIdentifier
import com.quata.core.language.QuataLanguageDetection
import com.quata.core.language.TextLanguageIdentifier
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.JsArray
import kotlin.js.JsNumber
import kotlin.js.JsString
import kotlin.js.toJsString
import kotlinx.coroutines.suspendCancellableCoroutine

internal object BrowserFastTextLanguageIdentifier : TextLanguageIdentifier {
    private val delegate = FastTextTextLanguageIdentifier(::fetchBrowserFastTextModelBytes)

    override suspend fun detect(text: String): QuataLanguageDetection =
        delegate.detect(text)
}

private suspend fun fetchBrowserFastTextModelBytes(): ByteArray =
    suspendCancellableCoroutine { continuation ->
        val cancel = fetchBrowserFastTextModel(
            path = FastTextLanguageDetector.ModelAssetName.toJsString(),
            onSuccess = { bytes ->
                if (continuation.isActive) continuation.resume(bytes.toByteArray())
            },
            onFailure = { message ->
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException(message.toString()))
            },
        )
        continuation.invokeOnCancellation { cancel() }
    }

private fun JsArray<JsNumber>.toByteArray(): ByteArray = ByteArray(length) { index ->
    (this[index]?.toInt() ?: 0).toByte()
}

@JsFun(
    """(path, onSuccess, onFailure) => {
      const controller = new AbortController();
      globalThis.fetch(path, { signal: controller.signal, credentials: 'same-origin' })
        .then(response => {
          if (!response.ok) throw new Error('fasttext_model_http_' + response.status);
          return response.arrayBuffer();
        })
        .then(buffer => onSuccess(Array.from(new Uint8Array(buffer))))
        .catch(error => {
          if (error?.name !== 'AbortError') onFailure(String(error?.message || 'fasttext_model_fetch_failed'));
        });
      return () => controller.abort();
    }""",
)
private external fun fetchBrowserFastTextModel(
    path: JsString,
    onSuccess: (JsArray<JsNumber>) -> Unit,
    onFailure: (JsString) -> Unit,
): () -> Unit
