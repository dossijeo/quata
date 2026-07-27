@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import com.quata.core.platform.BrowserDocumentOpenService
import com.quata.core.platform.DocumentOpenService
import com.quata.core.platform.DocumentSupport
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Browser-only DocMentis surface for formats supported by the pinned SDK.
 *
 * The viewer is loaded lazily only after a compatible document is opened. It owns a real DOM
 * modal rather than leaking a package dependency into shared Compose content. Closing/replacing
 * that modal destroys both the UDoc viewer and client. A load failure returns to the hardened
 * browser download path instead of leaving a stale overlay or pretending a preview succeeded.
 *
 * Do not add a license, attribution or telemetry override here. Version 0.7.9 retains the
 * provider defaults; deployment/legal configuration belongs outside the app source.
 */
class WebDocmentisDocumentOpenService(
    private val fallback: DocumentOpenService = BrowserDocumentOpenService(),
) : DocumentOpenService {
    override suspend fun open(file: PlatformFile): PlatformResult<Unit> {
        if (!DocmentisDocumentPolicy.supports(file)) return fallback.open(file)

        return when (val result = openWithDocmentis(file.reference, file.displayName)) {
            DocmentisOpenResult.Opened -> PlatformResult.Success(Unit)
            DocmentisOpenResult.Cancelled -> PlatformResult.Cancelled
            DocmentisOpenResult.Unsupported,
            is DocmentisOpenResult.Failed,
            -> fallback.open(file)
        }
    }
}

/** Formats explicitly advertised by the pinned `@docmentis/udoc-viewer` 0.7.9 package. */
internal object DocmentisDocumentPolicy {
    private val supportedExtensions = setOf("pdf", "docx", "pptx", "xlsx")
    private val supportedMimeTypes = setOf(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    )

    fun supports(file: PlatformFile): Boolean {
        val descriptor = DocumentSupport.describe(file.reference, file.displayName, file.mimeType)
        return descriptor.extension in supportedExtensions || descriptor.mimeType in supportedMimeTypes
    }
}

private sealed interface DocmentisOpenResult {
    data object Opened : DocmentisOpenResult
    data object Cancelled : DocmentisOpenResult
    data object Unsupported : DocmentisOpenResult
    data class Failed(val reason: String?) : DocmentisOpenResult
}

private suspend fun openWithDocmentis(reference: String, displayName: String?): DocmentisOpenResult =
    suspendCoroutine { continuation ->
        docmentisOpen(reference, displayName.orEmpty()) { state, reason ->
            continuation.resume(
                when (state) {
                    "opened" -> DocmentisOpenResult.Opened
                    "cancelled" -> DocmentisOpenResult.Cancelled
                    "unsupported" -> DocmentisOpenResult.Unsupported
                    else -> DocmentisOpenResult.Failed(reason)
                },
            )
        }
    }

/**
 * This JavaScript is deliberately kept in the Web launcher. The `import()` is statically named
 * so webpack includes the package, but executes only after a compatible attachment is selected.
 */
private fun docmentisOpen(
    reference: String,
    displayName: String,
    onResult: (String, String?) -> Unit,
): Unit = js(
    """
    (() => {
      const complete = (() => {
        let completed = false;
        return (state, reason) => {
          if (completed) return;
          completed = true;
          onResult(state, reason ?? null);
        };
      })();
      try {
        const rawReference = typeof reference === 'string' ? reference.trim() : '';
        if (!rawReference || /[\u0000-\u001F\u007F]/.test(rawReference)) {
          complete('unsupported', 'docmentis_invalid_url');
          return;
        }
        const parsed = new URL(rawReference, globalThis.location?.href);
        if (!['https:', 'http:'].includes(parsed.protocol) || parsed.username || parsed.password) {
          complete('unsupported', 'docmentis_url_scheme_not_allowed');
          return;
        }
        const document = globalThis.document;
        if (!document?.body || typeof document.createElement !== 'function') {
          complete('unsupported', 'docmentis_dom_unavailable');
          return;
        }

        // There is at most one SDK client. Replacing an open attachment releases its worker,
        // WebAssembly state and DOM before a new lazy import starts.
        globalThis.__quataDocmentisActive?.close?.('replaced');

        const overlay = document.createElement('section');
        overlay.dataset.quataDocmentisViewer = 'true';
        overlay.setAttribute('role', 'dialog');
        overlay.setAttribute('aria-modal', 'true');
        overlay.setAttribute('aria-label', displayName || 'Document viewer');
        overlay.style.cssText = 'position:fixed;inset:0;z-index:2147483000;background:#111827;display:flex;flex-direction:column;';

        const header = document.createElement('header');
        header.style.cssText = 'display:flex;align-items:center;gap:12px;min-height:48px;padding:0 12px;color:#fff;background:#111827;font:500 14px system-ui,sans-serif;';
        const title = document.createElement('span');
        title.textContent = displayName || 'Document';
        title.style.cssText = 'overflow:hidden;text-overflow:ellipsis;white-space:nowrap;flex:1;';
        const closeButton = document.createElement('button');
        closeButton.type = 'button';
        closeButton.textContent = 'Close';
        closeButton.setAttribute('aria-label', 'Close document viewer');
        closeButton.style.cssText = 'border:0;border-radius:6px;padding:8px 12px;background:#374151;color:#fff;font:inherit;cursor:pointer;';
        header.append(title, closeButton);
        const container = document.createElement('div');
        container.style.cssText = 'position:relative;flex:1;min-height:0;background:#f3f4f6;';
        overlay.append(header, container);
        document.body.appendChild(overlay);

        let client = null;
        let viewer = null;
        let opened = false;
        let active = true;
        const onKeyDown = (event) => {
          if (event.key === 'Escape') close('cancelled');
        };
        const close = (reason) => {
          if (!active) return;
          active = false;
          document.removeEventListener('keydown', onKeyDown);
          try { viewer?.destroy?.(); } catch (_) {}
          try { client?.destroy?.(); } catch (_) {}
          overlay.remove();
          if (globalThis.__quataDocmentisActive?.close === close) delete globalThis.__quataDocmentisActive;
          if (!opened) complete(reason === 'cancelled' ? 'cancelled' : 'unsupported', 'docmentis_' + reason);
        };
        closeButton.addEventListener('click', () => close('cancelled'));
        document.addEventListener('keydown', onKeyDown);
        globalThis.__quataDocmentisActive = { close };

        import('@docmentis/udoc-viewer')
          .then(({ UDocClient }) => UDocClient.create({
            // Keep browser document opening local to the supplied attachment and bundled WASM.
            // The free SDK otherwise checks npm for updates and may fetch missing Google fonts.
            disableUpdateCheck: true,
            googleFonts: false,
          }))
          .then((createdClient) => {
            if (!active) { createdClient.destroy?.(); return null; }
            client = createdClient;
            return client.createViewer({ container });
          })
          .then((createdViewer) => {
            if (!createdViewer) return null;
            if (!active) { createdViewer.destroy?.(); return null; }
            viewer = createdViewer;
            return viewer.load(parsed.href);
          })
          .then(() => {
            if (!active) return;
            opened = true;
            complete('opened', null);
          })
          .catch((error) => {
            if (!active) return;
            close('failed');
            complete('failed', error?.message ?? error?.name ?? 'docmentis_load_failed');
          });
      } catch (error) {
        complete('failed', error?.message ?? error?.name ?? 'docmentis_open_failed');
      }
    })()
    """,
)

/** Installs a test-only load probe when an explicit smoke query flag is present. */
internal fun installDocmentisSmokeProbe(): Unit = js(
    """
    (() => {
      if (!new URLSearchParams(globalThis.location?.search ?? '').has('quata-docmentis-smoke')) return;
      // This hook is intentionally unavailable outside the local browser smoke. In particular,
      // never expose an arbitrary URL loader from a deployed application merely for testing.
      if (!['127.0.0.1', 'localhost', '::1'].includes(globalThis.location?.hostname)) return;
      const runWithViewer = async action => {
        const { UDocClient } = await import('@docmentis/udoc-viewer');
        const host = globalThis.document?.createElement?.('div');
        if (!host || !globalThis.document?.body) throw new Error('DocMentis smoke DOM unavailable');
        host.dataset.quataDocmentisSmoke = 'true';
        host.style.cssText = 'position:fixed;inset:0;opacity:0;pointer-events:none;z-index:-1;';
        globalThis.document.body.appendChild(host);
        let client = null;
        let viewer = null;
        try {
          client = await UDocClient.create({ disableUpdateCheck: true, googleFonts: false });
          viewer = await client.createViewer({ container: host });
          return await action({ UDocClient, host, viewer });
        } finally {
          try { viewer?.destroy?.(); } catch (_) {}
          try { client?.destroy?.(); } catch (_) {}
          host.remove();
        }
      };
      globalThis.__quataDocmentisProbe = {
        async mount() {
          return runWithViewer(async ({ UDocClient, host }) => {
            await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
            return {
              package: '@docmentis/udoc-viewer',
              version: UDocClient.version,
              clientCreated: true,
              viewerCreated: true,
              mounted: host.childElementCount > 0 || host.querySelectorAll('*').length > 0,
            };
          });
        },
        async expectPermitFailClosed(fixturePath = '/__quata-smoke-fixtures/legal.docx') {
          return runWithViewer(async ({ UDocClient, viewer }) => {
            const source = new URL(fixturePath, globalThis.location.origin);
            if (!['http:', 'https:'].includes(source.protocol)) throw new Error('DocMentis smoke URL is not HTTP(S)');
            let phase = null;
            let documentLoaded = false;
            viewer.on('error', event => { phase = event?.phase ?? null; });
            viewer.on('document:load', () => { documentLoaded = true; });
            try {
              await viewer.load(source.href);
              return {
                package: '@docmentis/udoc-viewer',
                version: UDocClient.version,
                clientCreated: true,
                blocked: false,
                phase,
                documentLoaded,
              };
            } catch (error) {
              return {
                package: '@docmentis/udoc-viewer',
                version: UDocClient.version,
                clientCreated: true,
                blocked: true,
                phase,
                documentLoaded,
                message: error?.message ?? String(error),
              };
            }
          });
        },
      };
    })()
    """,
)
