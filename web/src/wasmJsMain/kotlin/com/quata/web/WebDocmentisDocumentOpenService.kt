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
        recordDocumentOpenEvidence(file.reference, file.displayName, file.mimeType)
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

private fun recordDocumentOpenEvidence(reference: String, displayName: String?, mimeType: String?): Unit = js(
    """
    (() => {
      const query = new URLSearchParams(globalThis.location?.search ?? '');
      const local = ['127.0.0.1', 'localhost', '::1'].includes(globalThis.location?.hostname);
      if (!local || !query.has('quata-auth-e2e')) return;
      const events = Array.isArray(globalThis.__quataDocumentOpenEvidence)
        ? globalThis.__quataDocumentOpenEvidence
        : [];
      events.push({
        reference: typeof reference === 'string' ? reference : '',
        displayName: typeof displayName === 'string' ? displayName : '',
        mimeType: typeof mimeType === 'string' ? mimeType : '',
      });
      globalThis.__quataDocumentOpenEvidence = events;
    })()
    """,
)

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
        let documentLoaded = false;
        let pageSlotRendered = false;
        let renderedPageCount = 0;
        let renderFailure = null;
        let resolveRenderReady;
        let rejectRenderReady;
        const renderReady = new Promise((resolve, reject) => {
          resolveRenderReady = resolve;
          rejectRenderReady = reject;
        });
        // `viewer.load()` can reject in the same turn as the vendor error callback. Keep that
        // expected failure observed until the load chain reaches its fail-closed handler.
        renderReady.catch(() => {});
        const markRenderReady = () => {
          // `viewer.load()` only proves that the SDK accepted the source. A page-slot callback is
          // invoked by the real DocMentis renderer when it mounts a visible page, so require both
          // signals before reporting an integrated preview as opened.
          if (!active || !documentLoaded || !pageSlotRendered || viewer?.isLoaded !== true || viewer?.pageCount < 1) return;
          overlay.dataset.quataDocmentisRenderReady = 'true';
          resolveRenderReady();
        };
        const renderReadyTimeout = globalThis.setTimeout(() => {
          if (!active || overlay.dataset.quataDocmentisRenderReady === 'true') return;
          rejectRenderReady(new Error(renderFailure || 'docmentis_render_ready_timeout'));
        }, 10_000);
        const onKeyDown = (event) => {
          if (event.key === 'Escape') close('cancelled');
        };
        const close = (reason, completeOnClose = true) => {
          if (!active) return;
          active = false;
          document.removeEventListener('keydown', onKeyDown);
          try { viewer?.destroy?.(); } catch (_) {}
          try { client?.destroy?.(); } catch (_) {}
          globalThis.clearTimeout(renderReadyTimeout);
          overlay.remove();
          if (globalThis.__quataDocmentisActive?.close === close) delete globalThis.__quataDocmentisActive;
          if (!opened && completeOnClose) complete(reason === 'cancelled' ? 'cancelled' : 'unsupported', 'docmentis_' + reason);
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
            return client.createViewer({
              container,
              // The SDK documents this hook as running when a page slot is mounted by its
              // renderer. It is deliberately not a Quata DOM heuristic or a load-promise proxy.
              customPageOverlay: (_pageIndex, _pageContainer, _scale) => {
                pageSlotRendered = true;
                markRenderReady();
              },
            });
          })
          .then((createdViewer) => {
            if (!createdViewer) return null;
            if (!active) { createdViewer.destroy?.(); return null; }
            viewer = createdViewer;
            viewer.on?.('document:load', ({ pageCount }) => {
              documentLoaded = Number.isInteger(pageCount) && pageCount > 0;
              renderedPageCount = pageCount || 0;
              markRenderReady();
            });
            viewer.on?.('error', ({ error, phase }) => {
              renderFailure = 'docmentis_' + (phase || 'render') + '_' + (error?.message || error?.name || 'failed');
              rejectRenderReady(new Error(renderFailure));
            });
            return viewer.load(parsed.href);
          })
          .then(() => {
            if (!active) return;
            return renderReady;
          })
          .then(() => {
            if (!active || renderedPageCount < 1) return;
            opened = true;
            complete('opened', null);
          })
          .catch((error) => {
            if (!active) return;
            close('failed', false);
            complete('failed', error?.message ?? error?.name ?? 'docmentis_load_failed');
          });
      } catch (error) {
        complete('failed', error?.message ?? error?.name ?? 'docmentis_open_failed');
      }
    })()
    """,
)

/**
 * Exposes the composition-root [DocumentOpenService] only to an explicit localhost smoke.
 *
 * The bridge never imports DocMentis or implements format routing itself. The browser smoke must
 * therefore traverse [WebDocmentisDocumentOpenService.open], its admission policy and its real
 * hardened fallback rather than obtaining a false positive from a second SDK-only test path.
 */
internal fun installDocmentisProductSmokeBridge(
    onOpen: (
        reference: String,
        displayName: String,
        mimeType: String,
        complete: (String, String?) -> Unit,
    ) -> Unit,
): () -> Unit = js(
    """
    (() => {
      const query = new URLSearchParams(globalThis.location?.search ?? '');
      const local = ['127.0.0.1', 'localhost', '::1'].includes(globalThis.location?.hostname);
      if (!local || !query.has('quata-docmentis-smoke')) return () => {};
      const bridge = {
        open(file) {
          return new Promise((resolve) => {
            onOpen(
              typeof file?.reference === 'string' ? file.reference : '',
              typeof file?.displayName === 'string' ? file.displayName : '',
              typeof file?.mimeType === 'string' ? file.mimeType : '',
              (state, reason) => resolve({ state, reason: reason ?? null }),
            );
          });
        },
      };
      globalThis.__quataDocmentisProductProbe = bridge;
      return () => {
        if (globalThis.__quataDocmentisProductProbe === bridge) {
          delete globalThis.__quataDocmentisProductProbe;
        }
      };
    })()
    """,
)
