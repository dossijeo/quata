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
    semanticClick: (String) -> Boolean,
    semanticInput: (String, String) -> Boolean,
    skipTranslation: () -> Boolean,
    state: () -> String,
): () -> Unit = installOfficialEditorBridgeWhenAllowed(
    semanticClick,
    semanticInput,
    skipTranslation,
    state,
)

@JsFun(
    """(semanticClick, semanticInput, skipTranslation, state) => {
      const local = location?.hostname === 'localhost' || location?.hostname === '127.0.0.1';
      const params = new URLSearchParams(location?.search || '');
      const optedIn = params.get('quata-official-editor-e2e') === '1' ||
        globalThis.sessionStorage?.getItem('quata.official_editor.e2e') === '1';
      if (!local || !optedIn) return () => {};
      const bridge = Object.freeze({
        version: 1,
        semanticClick: (target) => semanticClick(String(target ?? '')) === true,
        semanticInput: (target, value) => semanticInput(String(target ?? ''), String(value ?? '')) === true,
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
    semanticClick: (String) -> Boolean,
    semanticInput: (String, String) -> Boolean,
    skipTranslation: () -> Boolean,
    state: () -> String,
): () -> Unit

internal fun installWebOfficialRichTextEditorE2eBridge(
    open: () -> Unit,
    inputHtml: (String) -> Unit,
    save: () -> Unit,
): () -> Unit = installOfficialRichTextEditorBridgeWhenAllowed(open, inputHtml, save)

@JsFun(
    """(open, inputHtml, save) => {
      const local = location?.hostname === 'localhost' || location?.hostname === '127.0.0.1';
      const params = new URLSearchParams(location?.search || '');
      const optedIn = params.get('quata-official-editor-e2e') === '1' ||
        globalThis.sessionStorage?.getItem('quata.official_editor.e2e') === '1';
      if (!local || !optedIn) return () => {};
      const bridge = Object.freeze({
        version: 1,
        semanticClick: (target) => {
          const id = String(target ?? '');
          if (id === 'official-editor-body-action') { open(); return true; }
          if (id === 'official-editor-long-save') { save(); return true; }
          return false;
        },
        semanticInput: (target, value) => {
          if (String(target ?? '') !== 'quata-portable-rich-text-field') return false;
          inputHtml(String(value ?? ''));
          return true;
        },
      });
      globalThis.__quataOfficialRichTextEditorE2eProduct = bridge;
      globalThis.document?.documentElement?.setAttribute('data-quata-official-rich-text-editor-e2e', 'ready');
      return () => {
        if (globalThis.__quataOfficialRichTextEditorE2eProduct === bridge) delete globalThis.__quataOfficialRichTextEditorE2eProduct;
        globalThis.document?.documentElement?.removeAttribute('data-quata-official-rich-text-editor-e2e');
      };
    }""",
)
private external fun installOfficialRichTextEditorBridgeWhenAllowed(
    open: () -> Unit,
    inputHtml: (String) -> Unit,
    save: () -> Unit,
): () -> Unit
