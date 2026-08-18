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
      const query = new URLSearchParams(globalThis.location?.search || '');
      const saveErrorE2e = query.get('quata-profile-sos-save-error-e2e') === '1' ||
        globalThis.sessionStorage?.getItem('quata.profile.sos.save_error_e2e') === '1';
      const bridge = Object.freeze({
        version: 1,
        saveErrorE2e,
        open: () => openSos(),
        close: () => closeSos(),
      });
      globalThis.__quataProfileSosE2eProduct = bridge;
      const element = globalThis.document?.documentElement;
      element?.setAttribute('data-quata-profile-sos-bridge', 'ready');
      if (saveErrorE2e) {
        element?.setAttribute('data-quata-profile-sos-save-error-e2e', 'enabled');
      }
      return () => {
        if (globalThis.__quataProfileSosE2eProduct === bridge) delete globalThis.__quataProfileSosE2eProduct;
        globalThis.document?.documentElement?.removeAttribute('data-quata-profile-sos-bridge');
        globalThis.document?.documentElement?.removeAttribute('data-quata-profile-sos-tab');
        globalThis.document?.documentElement?.removeAttribute('data-quata-profile-sos-selected-count');
        globalThis.document?.documentElement?.removeAttribute('data-quata-profile-sos-candidate-count');
        globalThis.document?.documentElement?.removeAttribute('data-quata-profile-sos-error-visible');
        globalThis.document?.documentElement?.removeAttribute('data-quata-profile-sos-save-error-e2e');
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

internal fun updateWebProfileSosE2eSelectionState(selectedCount: Int, candidateCount: Int) {
    updateProfileSosSelectionMarker(selectedCount, candidateCount)
}

internal fun updateWebProfileSosE2eErrorState(errorMessage: String?) {
    updateProfileSosErrorMarker(!errorMessage.isNullOrBlank())
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

@JsFun(
    """(selectedCount, candidateCount) => {
      const element = globalThis.document?.documentElement;
      if (!element) return;
      element.setAttribute('data-quata-profile-sos-selected-count', String(selectedCount));
      element.setAttribute('data-quata-profile-sos-candidate-count', String(candidateCount));
    }""",
)
private external fun updateProfileSosSelectionMarker(selectedCount: Int, candidateCount: Int)

@JsFun(
    """(visible) => {
      const element = globalThis.document?.documentElement;
      if (!element) return;
      if (visible) element.setAttribute('data-quata-profile-sos-error-visible', 'true');
      else element.removeAttribute('data-quata-profile-sos-error-visible');
    }""",
)
private external fun updateProfileSosErrorMarker(visible: Boolean)
