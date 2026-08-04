@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.core.language

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Browser fetch boundary for the shared Fang service. */
class BrowserTranslationHttpTransport : TranslationHttpTransport {
    override suspend fun get(url: String): TranslationHttpResponse = request("GET", url, null)

    override suspend fun post(url: String, body: String): TranslationHttpResponse = request("POST", url, body)

    private suspend fun request(method: String, url: String, body: String?): TranslationHttpResponse =
        suspendCancellableCoroutine { continuation ->
            val cancel = browserTranslationRequest(
                method = method,
                url = url,
                body = body,
                success = { status, message, responseBody ->
                    if (continuation.isActive) {
                        continuation.resume(TranslationHttpResponse(status, message, responseBody))
                    }
                },
                failure = { reason ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException(reason))
                    }
                },
            )
            continuation.invokeOnCancellation { cancel() }
        }
}

@JsFun(
    """(method, url, body, success, failure) => {
      const controller = new AbortController();
      const options = {
        method,
        signal: controller.signal,
        headers: { Accept: 'application/json' }
      };
      if (body !== null) {
        options.headers['Content-Type'] = 'application/json';
        options.body = body;
      }
      globalThis.fetch(url, options).then(async response => {
        const text = await response.text();
        success(response.status, response.statusText || '', text);
      }).catch(error => {
        if (error?.name !== 'AbortError') failure(error?.message || 'browser_translation_failed');
      });
      return () => controller.abort();
    }""",
)
private external fun browserTranslationRequest(
    method: String,
    url: String,
    body: String?,
    success: (Int, String, String) -> Unit,
    failure: (String) -> Unit,
): () -> Unit
