@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.core.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Browser viewport information is real state, not a hard-coded portrait fallback. */
@Composable
actual fun rememberQuataWindowLayoutInfo(): QuataWindowLayoutInfo {
    fun currentLayout(): QuataWindowLayoutInfo {
        val width = readWasmViewportWidth()
        val height = readWasmViewportHeight()
        return wasmViewportLayoutInfo(width, height)
    }
    var layout by remember { mutableStateOf(currentLayout()) }
    DisposableEffect(Unit) {
        val stopObserving = observeWasmViewport { layout = currentLayout() }
        onDispose(stopObserving)
    }
    return layout
}

internal fun wasmViewportLayoutInfo(widthPx: Int, heightPx: Int): QuataWindowLayoutInfo {
    val width = widthPx.coerceAtLeast(0)
    val height = heightPx.coerceAtLeast(0)
    return QuataWindowLayoutInfo(width, height, width > height, "wasm:${width}x${height}")
}

private fun readWasmViewportWidth(): Int = js("Math.max(0, Math.round(globalThis.innerWidth || globalThis.document?.documentElement?.clientWidth || 0))")
private fun readWasmViewportHeight(): Int = js("Math.max(0, Math.round(globalThis.innerHeight || globalThis.document?.documentElement?.clientHeight || 0))")

private fun observeWasmViewport(onChanged: () -> Unit): () -> Unit = js(
    """(() => {
    const listener = () => onChanged();
    globalThis.addEventListener?.('resize', listener);
    globalThis.addEventListener?.('orientationchange', listener);
    return () => {
      globalThis.removeEventListener?.('resize', listener);
      globalThis.removeEventListener?.('orientationchange', listener);
    };
    })()""",
)
