package com.quata.core.platform

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Browser implementation backed by the Web Share API, including picker-produced Blob URLs. */
class BrowserShareService : ShareService {
    override suspend fun share(payload: SharePayload): PlatformResult<Unit> {
        if (payload.text.isNullOrBlank() && payload.title.isNullOrBlank() && payload.files.isEmpty()) {
            return PlatformResult.Failure("share_payload_empty")
        }
        if (!browserShareIsAvailable()) return PlatformResult.Unsupported

        return suspendCoroutine { continuation ->
            browserShare(
                title = payload.title,
                text = payload.text,
                fileReferences = encodeStrings(payload.files.map { it.reference }),
                fileNames = encodeStrings(payload.files.map { it.displayName.orEmpty() }),
                fileMimeTypes = encodeStrings(payload.files.map { it.mimeType.orEmpty() }),
                onShared = { continuation.resume(PlatformResult.Success(Unit)) },
                onCancelled = { continuation.resume(PlatformResult.Cancelled) },
                onUnsupported = { continuation.resume(PlatformResult.Unsupported) },
                onFailure = { reason -> continuation.resume(PlatformResult.Failure(reason)) },
            )
        }
    }
}

private fun encodeStrings(values: List<String>): String =
    Json.encodeToString(ListSerializer(String.serializer()), values)

private fun browserShareIsAvailable(): Boolean =
    js("typeof globalThis.navigator?.share === 'function'")

private fun browserShare(
    title: String?,
    text: String?,
    fileReferences: String,
    fileNames: String,
    fileMimeTypes: String,
    onShared: () -> Unit,
    onCancelled: () -> Unit,
    onUnsupported: () -> Unit,
    onFailure: (String?) -> Unit,
): Unit = js(
    """
    const references = JSON.parse(fileReferences);
    const names = JSON.parse(fileNames);
    const mimeTypes = JSON.parse(fileMimeTypes);
    const finishError = (error) => {
      if (error?.name === 'AbortError') onCancelled();
      else if (error?.name === 'NotSupportedError' || error?.name === 'TypeError') onUnsupported();
      else onFailure(error?.message ?? error?.name ?? 'share_failed');
    };
    const share = (files) => {
      const data = {};
      if (title != null && title.length > 0) data.title = title;
      if (text != null && text.length > 0) data.text = text;
      if (files.length > 0) {
        if (typeof globalThis.File !== 'function') {
          onUnsupported();
          return;
        }
        if (typeof globalThis.navigator.canShare === 'function' && !globalThis.navigator.canShare({ files })) {
          onUnsupported();
          return;
        }
        data.files = files;
      }
      globalThis.navigator.share(data).then(() => onShared(), finishError);
    };
    if (references.length === 0) {
      share([]);
    } else if (typeof globalThis.fetch !== 'function' || typeof globalThis.File !== 'function') {
      onUnsupported();
    } else {
      Promise.all(references.map((reference, index) => globalThis.fetch(reference)
        .then((response) => {
          if (!response.ok) throw new Error('share_file_unavailable');
          return response.blob();
        })
        .then((blob) => new globalThis.File(
          [blob],
          names[index] || `quata-file-${'$'}{index + 1}`,
          { type: mimeTypes[index] || blob.type || 'application/octet-stream' }
        ))
      )).then(share, finishError);
    }
    """,
)
