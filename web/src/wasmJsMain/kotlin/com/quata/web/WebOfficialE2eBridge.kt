@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

internal fun installWebOfficialFeedE2eBridge(
    create: () -> Unit,
    state: () -> String,
): () -> Unit = installOfficialFeedBridgeWhenAllowed(create, state)

@JsFun(
    """(create, state) => {
      const local = location?.hostname === 'localhost' || location?.hostname === '127.0.0.1';
      const params = new URLSearchParams(location?.search || '');
      const optedIn = params.get('quata-auth-e2e') === '1' ||
        params.get('quata-official-editor-e2e') === '1' ||
        globalThis.sessionStorage?.getItem('quata.auth.e2e') === '1';
      if (!local || !optedIn) return () => {};
      const bridge = Object.freeze({
        version: 1,
        create: () => create(),
        state: () => {
          try { return JSON.parse(state()); } catch (error) { return { error: 'state_unavailable' }; }
        },
      });
      globalThis.__quataOfficialFeedE2eProduct = bridge;
      globalThis.document?.documentElement?.setAttribute('data-quata-official-feed-e2e', 'ready');
      return () => {
        if (globalThis.__quataOfficialFeedE2eProduct === bridge) delete globalThis.__quataOfficialFeedE2eProduct;
        globalThis.document?.documentElement?.removeAttribute('data-quata-official-feed-e2e');
      };
    }""",
)
private external fun installOfficialFeedBridgeWhenAllowed(
    create: () -> Unit,
    state: () -> String,
): () -> Unit

internal fun installWebOfficialEditorE2eBridge(
    setAdvancedMode: () -> Unit,
    setTitle: (String) -> Unit,
    setSummary: (String) -> Unit,
    setBodyHtml: (String) -> Unit,
    publish: () -> Unit,
    skipTranslation: () -> Boolean,
    state: () -> String,
): () -> Unit = installOfficialEditorBridgeWhenAllowed(
    setAdvancedMode,
    setTitle,
    setSummary,
    setBodyHtml,
    skipTranslation,
    state,
)

@JsFun(
    """(setAdvancedMode, setTitle, setSummary, setBodyHtml, skipTranslation, state) => {
      const local = location?.hostname === 'localhost' || location?.hostname === '127.0.0.1';
      const params = new URLSearchParams(location?.search || '');
      const optedIn = params.get('quata-official-editor-e2e') === '1' ||
        globalThis.sessionStorage?.getItem('quata.official_editor.e2e') === '1';
      if (!local || !optedIn) return () => {};
      const bridge = Object.freeze({
        version: 1,
        setAdvancedMode: () => setAdvancedMode(),
        setTitle: (value) => setTitle(String(value ?? '')),
        setSummary: (value) => setSummary(String(value ?? '')),
        setBodyHtml: (value) => setBodyHtml(String(value ?? '')),
        skipTranslation: () => skipTranslation(),
        state: () => {
          try { return JSON.parse(state()); } catch (error) { return { error: 'state_unavailable' }; }
        },
      });
      globalThis.__quataOfficialEditorE2eProduct = bridge;
      globalThis.document?.documentElement?.setAttribute('data-quata-official-editor-e2e', 'ready');
      return () => {
        if (globalThis.__quataOfficialEditorE2eProduct === bridge) delete globalThis.__quataOfficialEditorE2eProduct;
        globalThis.document?.documentElement?.removeAttribute('data-quata-official-editor-e2e');
      };
    }""",
)
private external fun installOfficialEditorBridgeWhenAllowed(
    setAdvancedMode: () -> Unit,
    setTitle: (String) -> Unit,
    setSummary: (String) -> Unit,
    setBodyHtml: (String) -> Unit,
    skipTranslation: () -> Boolean,
    state: () -> String,
): () -> Unit
