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
    publish,
    skipTranslation,
    state,
)

@JsFun(
    """(setAdvancedMode, setTitle, setSummary, setBodyHtml, publish, skipTranslation, state) => {
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
        publish: () => publish(),
        skipTranslation: () => skipTranslation(),
        state: () => {
          try { return JSON.parse(state()); } catch (error) { return { error: 'state_unavailable' }; }
        },
      });
      globalThis.__quataOfficialEditorE2eProduct = bridge;
      globalThis.document?.documentElement?.setAttribute('data-quata-official-editor-e2e', 'ready');
      const publishAnchor = globalThis.document?.createElement?.('button');
      let publishAnchorTimer = null;
      const attachPublishAnchor = () => {
        if (!publishAnchor || publishAnchor.isConnected) return;
        const host = globalThis.document?.body || globalThis.document?.documentElement;
        if (!host) return;
        publishAnchor.type = 'button';
        publishAnchor.onclick = (event) => {
          event?.preventDefault?.();
          publish();
          return null;
        };
        publishAnchor.id = 'official-editor-publish';
        publishAnchor.textContent = 'official-editor-publish';
        publishAnchor.setAttribute('aria-label', 'official-editor-publish');
        publishAnchor.style.position = 'fixed';
        publishAnchor.style.right = '12px';
        publishAnchor.style.bottom = '12px';
        publishAnchor.style.width = '220px';
        publishAnchor.style.height = '56px';
        publishAnchor.style.opacity = '0.01';
        publishAnchor.style.zIndex = '2147483647';
        publishAnchor.style.pointerEvents = 'auto';
        host.appendChild(publishAnchor);
      };
      attachPublishAnchor();
      if (publishAnchor && !publishAnchor.isConnected) {
        publishAnchorTimer = globalThis.setInterval?.(() => {
          attachPublishAnchor();
          if (publishAnchor.isConnected && publishAnchorTimer !== null) {
            globalThis.clearInterval?.(publishAnchorTimer);
            publishAnchorTimer = null;
          }
        }, 25);
      }
      return () => {
        if (globalThis.__quataOfficialEditorE2eProduct === bridge) delete globalThis.__quataOfficialEditorE2eProduct;
        if (publishAnchorTimer !== null) globalThis.clearInterval?.(publishAnchorTimer);
        publishAnchor?.remove?.();
        globalThis.document?.documentElement?.removeAttribute('data-quata-official-editor-e2e');
      };
    }""",
)
private external fun installOfficialEditorBridgeWhenAllowed(
    setAdvancedMode: () -> Unit,
    setTitle: (String) -> Unit,
    setSummary: (String) -> Unit,
    setBodyHtml: (String) -> Unit,
    publish: () -> Unit,
    skipTranslation: () -> Boolean,
    state: () -> String,
): () -> Unit
