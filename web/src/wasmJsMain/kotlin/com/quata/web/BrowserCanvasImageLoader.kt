@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.painter.BitmapPainter
import kotlin.js.JsArray
import kotlin.js.JsNumber
import kotlin.js.JsString
import kotlin.js.toJsString

/** Terminal and transient states exposed to the Compose-only browser image surface. */
internal sealed interface BrowserCanvasImageState {
    data object Loading : BrowserCanvasImageState
    data class Ready(val bitmap: ImageBitmap) : BrowserCanvasImageState
    data object Error : BrowserCanvasImageState
}

/**
 * Shared browser image fetch/decode cache.
 *
 * The browser is only used to fetch bytes. Decoding uses Compose UI's Wasm implementation of
 * [decodeToImageBitmap], which delegates to Skia, and all visible pixels remain in the Compose
 * canvas. A request is aborted when its final subscriber leaves composition.
 */
internal object BrowserCanvasImageLoader {
    private const val maxReadyEntries = 64

    private val readyCache = LinkedHashMap<String, BrowserCanvasImageState.Ready>()
    private val requests = mutableMapOf<String, Request>()

    fun subscribe(url: String, observer: (BrowserCanvasImageState) -> Unit): () -> Unit {
        readyCache[url]?.let(observer)
        if (readyCache.containsKey(url)) return {}

        val request = requests.getOrPut(url) {
            Request().also { created ->
                created.cancel = fetchBrowserCanvasImage(
                    url = url.toJsString(),
                    onSuccess = { encodedBytes ->
                        val result = runCatching { encodedBytes.toByteArray().decodeToImageBitmap() }
                            .fold(
                                onSuccess = { BrowserCanvasImageState.Ready(it) },
                                onFailure = { BrowserCanvasImageState.Error },
                            )
                        complete(url, result)
                    },
                    onFailure = { complete(url, BrowserCanvasImageState.Error) },
                )
            }
        }
        request.observers += observer

        return {
            request.observers -= observer
            if (request.observers.isEmpty() && requests.remove(url) === request) {
                request.cancel.invoke()
            }
        }
    }

    private fun complete(url: String, state: BrowserCanvasImageState) {
        val request = requests.remove(url) ?: return
        if (state is BrowserCanvasImageState.Ready) putReady(url, state)
        request.observers.toList().forEach { it(state) }
        request.observers.clear()
    }

    private fun putReady(url: String, state: BrowserCanvasImageState.Ready) {
        readyCache.remove(url)
        readyCache[url] = state
        while (readyCache.size > maxReadyEntries) readyCache.entries.iterator().next().also { readyCache.remove(it.key) }
    }

    private class Request {
        val observers = mutableSetOf<(BrowserCanvasImageState) -> Unit>()
        lateinit var cancel: () -> Unit
    }
}

/** Failed decodes and transport failures are deliberately not cached so a new composition retries. */
internal fun browserCanvasImageIsCacheable(state: BrowserCanvasImageState): Boolean =
    state is BrowserCanvasImageState.Ready

@Composable
internal fun rememberBrowserCanvasImage(url: String): BrowserCanvasImageState {
    var state by remember(url) { mutableStateOf<BrowserCanvasImageState>(BrowserCanvasImageState.Loading) }
    DisposableEffect(url) {
        val unsubscribe = BrowserCanvasImageLoader.subscribe(url) { state = it }
        onDispose(unsubscribe)
    }
    return state
}

/** Compose-canvas image pixels. Its caller owns the shared seeded media surface for all states. */
@Composable
internal fun BrowserCanvasImage(
    url: String,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
) {
    when (val state = rememberBrowserCanvasImage(url)) {
        BrowserCanvasImageState.Loading,
        BrowserCanvasImageState.Error -> Box(modifier = modifier)
        is BrowserCanvasImageState.Ready -> Image(
            painter = BitmapPainter(state.bitmap),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier.fillMaxSize(),
        )
    }
}

private fun JsArray<JsNumber>.toByteArray(): ByteArray = ByteArray(length) { index ->
    (this[index]?.toInt() ?: 0).toByte()
}

@JsFun(
    """(url, onSuccess, onFailure) => {
      const controller = new AbortController();
      fetch(url, { signal: controller.signal })
        .then(response => {
          if (!response.ok) throw new Error('http_' + response.status);
          return response.arrayBuffer();
        })
        .then(buffer => onSuccess(Array.from(new Uint8Array(buffer))))
        .catch(error => {
          if (error?.name !== 'AbortError') onFailure(String(error?.message || 'image_fetch_failed'));
        });
      return () => controller.abort();
    }""",
)
private external fun fetchBrowserCanvasImage(
    url: JsString,
    onSuccess: (JsArray<JsNumber>) -> Unit,
    onFailure: (JsString) -> Unit,
): () -> Unit
