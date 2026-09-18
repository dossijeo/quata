@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

internal fun installWebAccountPostflightE2eBridge(
    openManagement: () -> Unit,
    backToOverview: () -> Unit,
    openDeactivateConfirmation: () -> Unit,
    openDeleteConfirmation: () -> Unit,
    cancelConfirmation: () -> Unit,
    snapshot: () -> String,
): () -> Unit = installAccountPostflightBridgeWhenAllowed(
    openManagement,
    backToOverview,
    openDeactivateConfirmation,
    openDeleteConfirmation,
    cancelConfirmation,
    snapshot,
)

@JsFun(
    """(openManagement, backToOverview, openDeactivate, openDelete, cancelConfirmation, snapshot) => {
      const local = location?.hostname === 'localhost' || location?.hostname === '127.0.0.1';
      if (!local) return () => {};
      const root = globalThis.document?.documentElement;
      const assertOptedIn = () => {
        const params = new URLSearchParams(location?.search || '');
        const optedIn = params.get('quata-account-postflight-e2e') === '1' &&
          globalThis.localStorage?.getItem('quata_account_postflight_e2e_opt_in') === 'I_ACCEPT_WEB_ACCOUNT_POSTFLIGHT_FIXTURE';
        if (!optedIn) throw Error('account_postflight_bridge_not_enabled');
      };
      const read = () => {
        assertOptedIn();
        const parts = String(snapshot() || '').split('\u001F');
        root?.setAttribute('data-quata-account-postflight-page', parts[0] || '');
        root?.setAttribute('data-quata-account-postflight-confirmation', parts[1] || '');
        root?.setAttribute('data-quata-account-postflight-profile-ready', parts[2] || 'false');
        return parts;
      };
      const invoke = (action) => { assertOptedIn(); action(); queueMicrotask(read); };
      const bridge = Object.freeze({
        version: 1,
        openManagement: () => invoke(openManagement),
        backToOverview: () => invoke(backToOverview),
        openDeactivateConfirmation: () => invoke(openDeactivate),
        openDeleteConfirmation: () => invoke(openDelete),
        cancelConfirmation: () => invoke(cancelConfirmation),
        snapshot: read
      });
      globalThis.__quataAccountPostflightE2EProduct = bridge;
      root?.setAttribute('data-quata-account-postflight-bridge', 'ready');
      return () => {
        if (globalThis.__quataAccountPostflightE2EProduct === bridge) delete globalThis.__quataAccountPostflightE2EProduct;
        for (const name of [
          'data-quata-account-postflight-bridge',
          'data-quata-account-postflight-page',
          'data-quata-account-postflight-confirmation',
          'data-quata-account-postflight-profile-ready'
        ]) root?.removeAttribute(name);
      };
    }""",
)
private external fun installAccountPostflightBridgeWhenAllowed(
    openManagement: () -> Unit,
    backToOverview: () -> Unit,
    openDeactivateConfirmation: () -> Unit,
    openDeleteConfirmation: () -> Unit,
    cancelConfirmation: () -> Unit,
    snapshot: () -> String,
): () -> Unit
