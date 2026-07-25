@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.core.platform

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Uses the browser decoder and canvas API to create a JPEG thumbnail without Media3 or bitmaps.
 * Blob URLs returned by [createThumbnail] are owned by this instance and may be released with
 * [release] after their host stops rendering or uploading the thumbnail.
 */
class BrowserVideoThumbnailService : VideoThumbnailService {
    private val issuedReferences = mutableSetOf<String>()

    override suspend fun createThumbnail(video: PlatformFile, maxWidth: Int): PlatformResult<PlatformFile> {
        if (maxWidth <= 0) return PlatformResult.Failure("invalid_thumbnail_width")
        if (!video.isBrowserVideo()) return PlatformResult.Unsupported
        if (!VideoThumbnailSupport.hasBrowserReadableReference(video)) return PlatformResult.Unsupported
        return suspendCoroutine { continuation ->
            browserCreateVideoThumbnail(video.reference.trim(), maxWidth) { state, payload ->
                continuation.resume(state.toVideoThumbnailResult(payload).also(::trackIssuedReference))
            }
        }
    }

    /** Revokes only a Blob URL emitted by this service instance; external references are ignored. */
    fun release(file: PlatformFile) {
        val reference = file.reference
        if (reference.startsWith("blob:", ignoreCase = true) && issuedReferences.remove(reference)) {
            browserRevokeObjectUrl(reference)
        }
    }

    private fun trackIssuedReference(result: PlatformResult<PlatformFile>) {
        (result as? PlatformResult.Success<PlatformFile>)?.value?.reference
            ?.takeIf { it.startsWith("blob:", ignoreCase = true) }
            ?.let(issuedReferences::add)
    }
}

private fun PlatformFile.isBrowserVideo(): Boolean = VideoThumbnailSupport.isVideo(this)

private fun String.toVideoThumbnailResult(payload: String?): PlatformResult<PlatformFile> = when (this) {
    "success" -> payload.toVideoThumbnailFile()?.let { PlatformResult.Success(it) }
        ?: PlatformResult.Failure("video_thumbnail_payload_invalid")
    "unsupported" -> PlatformResult.Unsupported
    else -> PlatformResult.Failure(payload)
}

private fun String?.toVideoThumbnailFile(): PlatformFile? = runCatching {
    val value = Json.parseToJsonElement(orEmpty()).jsonObject
    PlatformFile(
        reference = requireNotNull(value["reference"]?.jsonPrimitive?.contentOrNull),
        displayName = value["displayName"]?.jsonPrimitive?.contentOrNull,
        mimeType = value["mimeType"]?.jsonPrimitive?.contentOrNull,
        sizeBytes = value["sizeBytes"]?.jsonPrimitive?.longOrNull,
    )
}.getOrNull()

private fun browserRevokeObjectUrl(reference: String): Unit = js(
    """
    (() => { if (reference.startsWith('blob:')) globalThis.URL?.revokeObjectURL?.(reference); })()
    """,
)

private fun browserCreateVideoThumbnail(
    reference: String,
    maxWidth: Int,
    onResult: (String, String?) -> Unit,
): Unit = js(
    """
    (() => {
    try {
      const document = globalThis.document;
      if (!document?.createElement || !globalThis.URL?.createObjectURL) { onResult('unsupported', null); return; }
      const source = new globalThis.URL(reference);
      // A Blob URL is usable only in the origin that created it. Do not turn this
      // adapter into a cross-origin media fetcher merely because the string has a
      // blob: prefix.
      if (source.protocol !== 'blob:' || !globalThis.location?.origin || source.origin !== globalThis.location.origin) {
        onResult('unsupported', null);
        return;
      }
      const video = document.createElement('video');
      video.preload = 'auto';
      video.muted = true;
      video.playsInline = true;
      let finished = false;
      let timeoutId = null;
      const finish = (state, value) => {
        if (finished) return;
        finished = true;
        if (timeoutId !== null) globalThis.clearTimeout?.(timeoutId);
        video.onerror = null;
        video.onloadeddata = null;
        video.onseeked = null;
        video.pause?.();
        video.removeAttribute('src');
        video.load?.();
        onResult(state, value);
      };
      const render = () => {
        try {
          const width = video.videoWidth;
          const height = video.videoHeight;
          if (!width || !height) { finish('failure', 'video_thumbnail_dimensions_missing'); return; }
          const scale = Math.min(1, maxWidth / width);
          const canvas = document.createElement('canvas');
          canvas.width = Math.max(1, Math.round(width * scale));
          canvas.height = Math.max(1, Math.round(height * scale));
          const context = canvas.getContext('2d');
          if (!context) { finish('unsupported', null); return; }
          context.drawImage(video, 0, 0, canvas.width, canvas.height);
          canvas.toBlob((blob) => {
            if (finished) return;
            if (!blob) { finish('failure', 'video_thumbnail_encode_failed'); return; }
            finish('success', JSON.stringify({
              reference: globalThis.URL.createObjectURL(blob),
              displayName: 'video-thumbnail.jpg',
              mimeType: blob.type || 'image/jpeg',
              sizeBytes: blob.size
            }));
          }, 'image/jpeg', 0.85);
        } catch (error) {
          finish('failure', error?.message ?? error?.name ?? 'video_thumbnail_render_failed');
        }
      };
      video.onerror = () => finish('failure', 'video_thumbnail_decode_failed');
      video.onloadeddata = () => {
        const target = Number.isFinite(video.duration) && video.duration > 0.1 ? Math.min(0.1, video.duration / 2) : 0;
        if (target > 0) {
          video.onseeked = render;
          video.currentTime = target;
        } else {
          render();
        }
      };
      timeoutId = globalThis.setTimeout?.(() => finish('failure', 'video_thumbnail_timeout'), 15000) ?? null;
      video.src = reference;
      video.load();
    } catch (error) {
      onResult('failure', error?.message ?? error?.name ?? 'video_thumbnail_start_failed');
    }
    })()
    """,
)
