@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

internal fun installWebPostComposerE2eBridge(
    setText: (String) -> Unit,
    setImage: (String) -> Unit,
    setVideo: (String) -> Unit,
    setLocation: (String) -> Unit,
    submitText: () -> Unit,
    submitImage: () -> Unit,
    state: () -> String,
): () -> Unit = installPostComposerBridgeWhenAllowed(setText, setImage, setVideo, setLocation, submitText, submitImage, state)

@JsFun(
    """(setText, setImage, setVideo, setLocation, submitText, submitImage, state) => {
      const local = location?.hostname === 'localhost' || location?.hostname === '127.0.0.1';
      const params = new URLSearchParams(location?.search || '');
      const optedIn = params.get('quata-post-publish-e2e') === '1' ||
        params.get('quata-post-picker-camera-e2e') === '1' ||
        globalThis.sessionStorage?.getItem('quata.post_publish.e2e') === '1';
      if (!local || !optedIn) return () => {};
      const bridge = Object.freeze({
        version: 1,
        setText: (value) => setText(String(value ?? '')),
        setImage: (value) => setImage(String(value ?? '')),
        setVideo: (value) => setVideo(String(value ?? '')),
        setLocation: (value) => setLocation(String(value ?? '')),
        submitText: () => submitText(),
        submitImage: () => submitImage(),
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
    setImage: (String) -> Unit,
    setVideo: (String) -> Unit,
    setLocation: (String) -> Unit,
    submitText: () -> Unit,
    submitImage: () -> Unit,
    state: () -> String,
): () -> Unit
