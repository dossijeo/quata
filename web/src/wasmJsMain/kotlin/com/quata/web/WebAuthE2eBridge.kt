@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

/**
 * Localhost-only browser E2E boundary. It invokes the product repositories and coordinator; it
 * does not render or accept an alternate DOM implementation of Auth.
 */
internal fun installWebAuthE2eBridge(
    login: (String, String, String, (String) -> Unit, (String) -> Unit) -> Unit,
    restore: ((String) -> Unit, (String) -> Unit) -> Unit,
    logout: ((String) -> Unit, (String) -> Unit) -> Unit,
): () -> Unit = installAuthBridgeWhenAllowed(login, restore, logout)

@JsFun(
    """(login, restore, logout) => {
      const location = globalThis.location;
      const localHost = location?.hostname === '127.0.0.1' || location?.hostname === 'localhost';
      const optedIn = new URLSearchParams(location?.search || '').get('quata-auth-e2e') === '1';
      if (!localHost || !optedIn) return () => {};
      const promise = (operation) => new Promise((resolve, reject) => operation(resolve, reject));
      const bridge = Object.freeze({
        version: 1,
        login: (countryCode, phone, password) =>
          promise((resolve, reject) => login(countryCode, phone, password, resolve, reject)),
        restore: () => promise((resolve, reject) => restore(resolve, reject)),
        logout: () => promise((resolve, reject) => logout(resolve, reject)),
      });
      globalThis.__quataAuthE2eProduct = bridge;
      return () => {
        if (globalThis.__quataAuthE2eProduct === bridge) delete globalThis.__quataAuthE2eProduct;
      };
    }""",
)
private external fun installAuthBridgeWhenAllowed(
    login: (String, String, String, (String) -> Unit, (String) -> Unit) -> Unit,
    restore: ((String) -> Unit, (String) -> Unit) -> Unit,
    logout: ((String) -> Unit, (String) -> Unit) -> Unit,
): () -> Unit
