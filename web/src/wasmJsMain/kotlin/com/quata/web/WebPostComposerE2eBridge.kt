@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

internal fun installWebPostComposerE2eBridge(
    setText: (String) -> Unit,
    submitText: () -> Unit,
    state: () -> String,
): () -> Unit = installPostComposerBridgeWhenAllowed(setText, submitText, state)

@JsFun(
    """(setText, submitText, state) => {
      const local = location?.hostname === 'localhost' || location?.hostname === '127.0.0.1';
      const optedIn = new URLSearchParams(location?.search || '').get('quata-post-publish-e2e') === '1' ||
        globalThis.sessionStorage?.getItem('quata.post_publish.e2e') === '1';
      if (!local || !optedIn) return () => {};
      const bridge = Object.freeze({
        version: 1,
        setText: (value) => setText(String(value ?? '')),
        submitText: () => submitText(),
        state: () => {
          try { return JSON.parse(state()); } catch (error) { return { error: 'state_unavailable' }; }
        },
      });
      globalThis.__quataPostComposerE2eProduct = bridge;
      globalThis.document?.documentElement?.setAttribute('data-quata-post-composer-e2e', 'ready');
      return () => {
        if (globalThis.__quataPostComposerE2eProduct === bridge) delete globalThis.__quataPostComposerE2eProduct;
        globalThis.document?.documentElement?.removeAttribute('data-quata-post-composer-e2e');
      };
    }""",
)
private external fun installPostComposerBridgeWhenAllowed(
    setText: (String) -> Unit,
    submitText: () -> Unit,
    state: () -> String,
): () -> Unit
