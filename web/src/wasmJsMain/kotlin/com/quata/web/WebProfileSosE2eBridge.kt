@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

internal fun installWebProfileSosE2eBridge(
    openSos: () -> Unit,
    closeSos: () -> Unit,
): () -> Unit = installProfileSosBridgeWhenAllowed(openSos, closeSos)

@JsFun(
    """(openSos, closeSos) => {
      const local = location?.hostname === 'localhost' || location?.hostname === '127.0.0.1';
      const optedIn = new URLSearchParams(location?.search || '').get('quata-auth-e2e') === '1';
      if (!local || !optedIn) return () => {};
      const bridge = Object.freeze({
        version: 1,
        open: () => openSos(),
        close: () => closeSos(),
      });
      globalThis.__quataProfileSosE2eProduct = bridge;
      globalThis.document?.documentElement?.setAttribute('data-quata-profile-sos-bridge', 'ready');
      return () => {
        if (globalThis.__quataProfileSosE2eProduct === bridge) delete globalThis.__quataProfileSosE2eProduct;
        globalThis.document?.documentElement?.removeAttribute('data-quata-profile-sos-bridge');
        globalThis.document?.documentElement?.removeAttribute('data-quata-profile-sos-tab');
      };
    }""",
)
private external fun installProfileSosBridgeWhenAllowed(
    openSos: () -> Unit,
    closeSos: () -> Unit,
): () -> Unit

internal fun updateWebProfileSosE2eTab(tab: String?) {
    updateProfileSosTabMarker(tab)
}

@JsFun(
    """(tab) => {
      const element = globalThis.document?.documentElement;
      if (!element) return;
      if (tab) element.setAttribute('data-quata-profile-sos-tab', tab);
      else element.removeAttribute('data-quata-profile-sos-tab');
    }""",
)
private external fun updateProfileSosTabMarker(tab: String?)
