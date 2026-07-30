@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import kotlin.test.Test
import kotlin.test.assertEquals

class WebBrowserFragmentObserverTest {
    @Test
    fun browserHashObserverInstallsOnceDeliversShellRoutesAndCleansUp() {
        installBrowserFragmentObserverHarness()
        val received = mutableListOf<String>()

        val stopObserving = observeBrowserFragmentChanges(received::add)

        assertEquals(1, browserFragmentObserverListenerCount())
        listOf("notifications", "profile", "chat", "official", "", "post-publication-123").forEach {
            dispatchBrowserFragmentChange(it)
        }
        assertEquals(
            listOf("notifications", "profile", "chat", "official", "feed", "post/publication-123"),
            received.map { it.toWebNavigationState().route },
        )

        stopObserving()
        assertEquals(0, browserFragmentObserverListenerCount())
        dispatchBrowserFragmentChange("notifications")
        assertEquals(6, received.size)
        restoreBrowserFragmentObserverHarness()
    }
}

private fun installBrowserFragmentObserverHarness(): Unit = js(
    """
    (() => {
      const harness = globalThis.__quataBrowserFragmentObserverHarness = {
        listeners: new Set(),
        addEventListener: globalThis.addEventListener,
        removeEventListener: globalThis.removeEventListener,
      };
      globalThis.addEventListener = (type, listener) => {
        if (type === 'hashchange') harness.listeners.add(listener);
        else harness.addEventListener?.call(globalThis, type, listener);
      };
      globalThis.removeEventListener = (type, listener) => {
        if (type === 'hashchange') harness.listeners.delete(listener);
        else harness.removeEventListener?.call(globalThis, type, listener);
      };
    })()
    """,
)

private fun browserFragmentObserverListenerCount(): Int = js(
    "globalThis.__quataBrowserFragmentObserverHarness.listeners.size",
)

private fun dispatchBrowserFragmentChange(fragment: String): Unit = js(
    """
    (() => {
      globalThis.location.hash = fragment;
      for (const listener of globalThis.__quataBrowserFragmentObserverHarness.listeners) listener();
    })()
    """,
)

private fun restoreBrowserFragmentObserverHarness(): Unit = js(
    """
    (() => {
      const harness = globalThis.__quataBrowserFragmentObserverHarness;
      globalThis.addEventListener = harness.addEventListener;
      globalThis.removeEventListener = harness.removeEventListener;
      delete globalThis.__quataBrowserFragmentObserverHarness;
    })()
    """,
)
