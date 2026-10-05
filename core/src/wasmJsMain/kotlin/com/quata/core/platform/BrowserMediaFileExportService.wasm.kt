@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.core.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Browser media export composed from one leased Blob URL and one native user effect. */
class BrowserMediaFileExportService(
    private val materializer: MediaFileMaterializer = BrowserMediaFileMaterializer(),
) : MediaFileExportService {
    override suspend fun export(
        descriptor: MediaFileExportDescriptor,
        action: MediaFileExportAction,
    ): PlatformResult<Unit> {
        val lease = when (val materialized = materializer.materialize(descriptor)) {
            is PlatformResult.Success -> materialized.value
            is PlatformResult.Failure -> return materialized
            PlatformResult.Cancelled -> return PlatformResult.Cancelled
            PlatformResult.Unsupported -> return PlatformResult.Unsupported
        }
        return try {
            useBrowserMaterializedMediaFile(lease.file, action)
        } finally {
            lease.release()
        }
    }
}

/** Bounded browser fetch whose Blob URL has an explicit, idempotent release lease. */
class BrowserMediaFileMaterializer : MediaFileMaterializer {
    override suspend fun materialize(
        descriptor: MediaFileExportDescriptor,
    ): PlatformResult<MaterializedMediaFileLease> = suspendCancellableCoroutine { continuation ->
        val cancel = browserMaterializeMediaFile(
            reference = descriptor.reference,
            displayName = descriptor.displayName,
            mimeType = descriptor.mimeType,
            allowResponseMimeOverride = descriptor.allowResponseMimeOverride,
        ) { state, reason, localReference, size, resolvedMimeType ->
            if (!continuation.isActive) {
                localReference?.let(::browserReleaseMaterializedMediaFile)
                return@browserMaterializeMediaFile
            }
            continuation.resume(
                when (state) {
                    "success" -> {
                        if (localReference == null) {
                            PlatformResult.Failure("web_media_export_materialization_missing")
                        } else {
                            val file = PlatformFile(
                                reference = localReference,
                                displayName = descriptor.displayName,
                                mimeType = resolvedMimeType ?: descriptor.mimeType,
                                sizeBytes = size.toLong(),
                            )
                            PlatformResult.Success(
                                MaterializedMediaFileLease(file) {
                                    browserReleaseMaterializedMediaFile(localReference)
                                },
                            )
                        }
                    }
                    "cancelled" -> PlatformResult.Cancelled
                    "unsupported" -> PlatformResult.Unsupported
                    else -> PlatformResult.Failure(reason ?: "web_media_export_failed")
                },
            )
        }
        continuation.invokeOnCancellation { cancel() }
    }
}

private suspend fun useBrowserMaterializedMediaFile(
    file: PlatformFile,
    action: MediaFileExportAction,
): PlatformResult<Unit> = suspendCancellableCoroutine { continuation ->
    val cancel = browserUseMaterializedMediaFile(
        reference = file.reference,
        displayName = file.displayName ?: "quata-media",
        mimeType = file.mimeType ?: "application/octet-stream",
        action = action.name.lowercase(),
    ) { state, reason ->
        if (!continuation.isActive) return@browserUseMaterializedMediaFile
        continuation.resume(
            when (state) {
                "success" -> PlatformResult.Success(Unit)
                "cancelled" -> PlatformResult.Cancelled
                "unsupported" -> PlatformResult.Unsupported
                else -> PlatformResult.Failure(reason ?: "web_media_export_effect_failed")
            },
        )
    }
    continuation.invokeOnCancellation { cancel() }
}

@JsFun(
    """(reference, displayName, mimeType, allowResponseMimeOverride, onResult) => {
      const controller = new AbortController();
      let finished = false;
      const finish = (state, reason = null, localReference = null, size = -1) => {
        if (finished) return;
        finished = true;
        onResult(state, reason, localReference, size, null);
      };
      const safeUrl = (() => {
        try {
          const parsed = new URL(String(reference || '').trim(), globalThis.location?.href);
          const loopback = ['localhost', '127.0.0.1', '[::1]'].includes(parsed.hostname);
          if ((parsed.protocol !== 'https:' && !(parsed.protocol === 'http:' && loopback)) || parsed.username || parsed.password) return null;
          return parsed.href;
        } catch (_) { return null; }
      })();
      if (!safeUrl) {
        finish('failure', 'web_media_export_url_invalid');
        return () => controller.abort();
      }
      if (typeof globalThis.fetch !== 'function' || !globalThis.URL?.createObjectURL) {
        finish('unsupported');
        return () => controller.abort();
      }
      const maxBytes = 50 * 1024 * 1024;
      const readBoundedBlob = async response => {
        if (!response.ok || response.redirected) throw new Error('web_media_export_http_' + response.status);
        const responseMime = String(response.headers.get('content-type') || '').split(';')[0].trim().toLowerCase();
        const expectedMime = String(mimeType || '').split(';')[0].trim().toLowerCase();
        if (!expectedMime || (!allowResponseMimeOverride && responseMime !== expectedMime)) throw new Error('web_media_export_mime_invalid');
        const resolvedMime = allowResponseMimeOverride && responseMime ? responseMime : expectedMime;
        const declaredHeader = response.headers.get('content-length');
        const declared = declaredHeader == null || declaredHeader === '' ? NaN : Number(declaredHeader);
        if (Number.isFinite(declared) && (declared <= 0 || declared > maxBytes)) throw new Error('web_media_export_size_invalid');
        const reader = response.body?.getReader?.();
        if (!reader) {
          const blob = await response.blob();
          if (!blob || blob.size <= 0 || blob.size > maxBytes) throw new Error('web_media_export_size_invalid');
          return new Blob([blob], { type: resolvedMime });
        }
        const chunks = [];
        let total = 0;
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          total += value.byteLength;
          if (total > maxBytes) { await reader.cancel(); throw new Error('web_media_export_size_invalid'); }
          chunks.push(value);
        }
        if (total <= 0) throw new Error('web_media_export_size_invalid');
        return new Blob(chunks, { type: resolvedMime });
      };
      globalThis.fetch(safeUrl, {
        method: 'GET', credentials: 'omit', cache: 'no-store', redirect: 'error', signal: controller.signal,
        headers: { Accept: mimeType || 'image/*,video/*' }
      }).then(readBoundedBlob).then(sourceBlob => {
        const blob = sourceBlob;
        const localReference = URL.createObjectURL(blob);
        const blobs = globalThis.__quataMediaExportBlobs || (globalThis.__quataMediaExportBlobs = new Map());
        blobs.set(localReference, blob);
        if (finished) return;
        finished = true;
        onResult('success', null, localReference, blob.size, blob.type || mimeType);
      }).catch(error => {
        if (error?.name === 'AbortError') finish('cancelled');
        else finish('failure', error?.message || error?.name || 'web_media_export_failed');
      });
      return () => controller.abort();
    }""",
)
private external fun browserMaterializeMediaFile(
    reference: String,
    displayName: String,
    mimeType: String,
    allowResponseMimeOverride: Boolean,
    onResult: (String, String?, String?, Double, String?) -> Unit,
): () -> Unit

@JsFun(
    """(reference, displayName, mimeType, action, onResult) => {
      let cancelled = false;
      const finish = (state, reason = null) => { if (!cancelled) onResult(state, reason); };
      if (!String(reference || '').startsWith('blob:') || !['download', 'share'].includes(action)) {
        finish('failure', 'web_media_export_materialized_reference_invalid');
        return () => { cancelled = true; };
      }
      if (action === 'download') {
        const document = globalThis.document;
        if (!document?.body || typeof document.createElement !== 'function') finish('unsupported');
        else {
          const link = document.createElement('a');
          link.href = reference;
          link.download = displayName || 'quata-media';
          link.rel = 'noopener noreferrer';
          link.style.display = 'none';
          document.body.appendChild(link);
          link.click();
          link.remove();
          finish('success');
        }
        return () => { cancelled = true; };
      }
      if (typeof globalThis.File !== 'function' || typeof globalThis.navigator?.share !== 'function') {
        finish('unsupported');
        return () => { cancelled = true; };
      }
      const blob = globalThis.__quataMediaExportBlobs?.get?.(reference);
      if (!blob) {
        finish('failure', 'web_media_export_blob_missing');
        return () => { cancelled = true; };
      }
      Promise.resolve(blob).then(async blob => {
        const file = new File([blob], displayName || 'quata-media', { type: mimeType || blob.type });
        if (typeof globalThis.navigator.canShare === 'function' && !globalThis.navigator.canShare({ files: [file] })) {
          finish('unsupported');
          return;
        }
        try {
          await globalThis.navigator.share({ files: [file] });
          finish('success');
        } catch (error) {
          if (error?.name === 'AbortError') finish('cancelled');
          else if (error?.name === 'NotSupportedError') finish('unsupported');
          else finish('failure', error?.message || error?.name || 'web_media_export_share_failed');
        }
      }).catch(error => finish('failure', error?.message || error?.name || 'web_media_export_share_failed'));
      return () => { cancelled = true; };
    }""",
)
private external fun browserUseMaterializedMediaFile(
    reference: String,
    displayName: String,
    mimeType: String,
    action: String,
    onResult: (String, String?) -> Unit,
): () -> Unit

@JsFun("""reference => { globalThis.__quataMediaExportBlobs?.delete?.(reference); globalThis.setTimeout(() => globalThis.URL?.revokeObjectURL?.(reference), 1000); }""")
private external fun browserReleaseMaterializedMediaFile(reference: String)
