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
 * Still-camera adapter backed by `MediaDevices.getUserMedia`, a transient video element and a
 * canvas JPEG encode. It is deliberately separate from [BrowserFilePickerService]: no file input
 * or `capture` hint is used as a substitute for access to the real camera stream.
 *
 * The stream is stopped after every result. A successful [PlatformFile] owns a Blob URL until the
 * caller has finished uploading/previewing it; call [release] to revoke that URL.
 */
class BrowserCameraCaptureService : CameraCaptureService {
    private var captureInProgress = false
    // Blob URLs are process-wide browser resources. Keep ownership local so a composition cannot
    // accidentally revoke a URL issued by the gallery, document viewer or another camera host.
    private val issuedReferences = mutableSetOf<String>()

    override suspend fun capturePhoto(request: CameraCaptureRequest): PlatformResult<PlatformFile> {
        if (!request.supportsBrowserJpeg()) return PlatformResult.Failure("camera_capture_mime_unsupported")
        if (captureInProgress) return PlatformResult.Failure("camera_capture_in_progress")
        captureInProgress = true
        return suspendCoroutine { continuation ->
            browserCaptureJpeg(request.displayName) { result ->
                captureInProgress = false
                val captureResult = result.toCameraCaptureResult()
                if (captureResult is PlatformResult.Success) {
                    issuedReferences += captureResult.value.reference
                }
                continuation.resume(captureResult)
            }
        }
    }

    /** Releases a Blob URL returned by this service after its preview/upload no longer reads it. */
    fun release(file: PlatformFile) {
        if (issuedReferences.remove(file.reference)) {
            browserReleaseCameraCapture(file.reference)
        }
    }
}

private fun CameraCaptureRequest.supportsBrowserJpeg(): Boolean =
    mimeType.trim().lowercase() in setOf("image/jpeg", "image/jpg")

private fun String.toCameraCaptureResult(): PlatformResult<PlatformFile> = when {
    this == BrowserCameraUnsupported -> PlatformResult.Unsupported
    this == BrowserCameraCancelled -> PlatformResult.Cancelled
    startsWith(BrowserCameraFailurePrefix) -> PlatformResult.Failure(removePrefix(BrowserCameraFailurePrefix))
    else -> runCatching {
        val value = Json.parseToJsonElement(this).jsonObject
        val reference = value["reference"]?.jsonPrimitive?.contentOrNull
            ?: error("camera_capture_reference_missing")
        PlatformResult.Success(
            PlatformFile(
                reference = reference,
                displayName = value["displayName"]?.jsonPrimitive?.contentOrNull,
                mimeType = value["mimeType"]?.jsonPrimitive?.contentOrNull ?: "image/jpeg",
                sizeBytes = value["sizeBytes"]?.jsonPrimitive?.longOrNull,
            ),
        )
    }.getOrElse { PlatformResult.Failure("camera_capture_invalid_result") }
}

private fun browserCaptureJpeg(displayName: String?, onResult: (String) -> Unit): Unit = js(
    """
    (() => {
      // Browsers intentionally do not expose camera capture outside a secure context. Checking it
      // up front yields a deterministic result instead of depending on the browser-specific
      // shape of navigator.mediaDevices on an HTTP origin.
      // `localhost` (including its loopback aliases) is a potentially trustworthy origin even
      // when a browser/test harness does not populate isSecureContext. Do not reject local Web
      // development before MediaDevices gets the chance to make its own authoritative decision.
      const hostname = globalThis.location?.hostname;
      const isLoopbackHost = hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '::1' || hostname === '[::1]';
      if (globalThis.isSecureContext !== true && !isLoopbackHost) {
        onResult('failure:camera_secure_context_required');
        return;
      }
      const media = globalThis.navigator?.mediaDevices;
      if (!media || typeof media.getUserMedia !== 'function' || !globalThis.document?.createElement || !globalThis.URL?.createObjectURL) {
        onResult('unsupported');
        return;
      }
      let stream = null;
      let video = null;
      let finished = false;
      let captureTimeout = null;
      const cleanup = () => {
        if (captureTimeout != null) {
          globalThis.clearTimeout(captureTimeout);
          captureTimeout = null;
        }
        if (video) {
          video.pause?.();
          video.srcObject = null;
          video.remove?.();
          video = null;
        }
        if (stream) {
          stream.getTracks().forEach((track) => track.stop());
          stream = null;
        }
      };
      const finish = (result) => {
        if (finished) return;
        finished = true;
        cleanup();
        onResult(result);
      };
      const fail = (error, fallback) => {
        const name = error?.name || fallback || 'camera_capture_failed';
        if (name === 'AbortError') finish('cancelled');
        else if (name === 'NotSupportedError' || name === 'OverconstrainedError' || name === 'NotFoundError') finish('unsupported');
        else if (name === 'NotAllowedError' || name === 'SecurityError') finish('failure:camera_permission_denied');
        else if (name === 'NotReadableError' || name === 'TrackStartError') finish('failure:camera_device_busy');
        else finish('failure:' + name);
      };
      try {
        // Cover both a permission/device request that never resolves and a stream which resolves
        // but never produces a frame. A late successful getUserMedia resolution is stopped below.
        captureTimeout = globalThis.setTimeout(
          () => fail(null, 'camera_capture_frame_timeout'),
          15000,
        );
        media.getUserMedia({
          video: { facingMode: { ideal: 'environment' } },
          audio: false,
        }).then((grantedStream) => {
          // A frame timeout can finish before a delayed permission prompt resolves. Do not create
          // a detached video or leave the newly granted device stream running in that case.
          if (finished) {
            grantedStream.getTracks().forEach((track) => track.stop());
            return;
          }
          stream = grantedStream;
          video = globalThis.document.createElement('video');
          video.muted = true;
          video.playsInline = true;
          video.setAttribute('aria-hidden', 'true');
          video.style.cssText = 'position:fixed;width:1px;height:1px;opacity:0;pointer-events:none;left:-2px;top:-2px;';
          globalThis.document.body?.appendChild(video);
          video.srcObject = stream;
          const capture = () => {
            if (finished) return;
            const width = Number(video.videoWidth || 0);
            const height = Number(video.videoHeight || 0);
            if (!width || !height) { fail(null, 'camera_capture_frame_unavailable'); return; }
            const canvas = globalThis.document.createElement('canvas');
            canvas.width = width;
            canvas.height = height;
            const context = canvas.getContext('2d');
            if (!context) { finish('unsupported'); return; }
            context.drawImage(video, 0, 0, width, height);
            canvas.toBlob((blob) => {
              if (finished) return;
              if (!blob || !blob.size) { finish('failure:camera_capture_jpeg_failed'); return; }
              const safeName = String(displayName || '').trim().replace(/[^A-Za-z0-9._-]/g, '_').slice(0, 80) || `quata_camera_${'$'}{Date.now()}.jpg`;
              const lowerCaseName = safeName.toLowerCase();
              const fileName = (lowerCaseName.endsWith('.jpg') || lowerCaseName.endsWith('.jpeg')) ? safeName : `${'$'}{safeName}.jpg`;
              finish(JSON.stringify({
                reference: globalThis.URL.createObjectURL(blob),
                displayName: fileName,
                mimeType: 'image/jpeg',
                sizeBytes: blob.size,
              }));
            }, 'image/jpeg', 0.92);
          };
          video.addEventListener('loadeddata', capture, { once: true });
          video.addEventListener('error', () => fail(null, 'camera_capture_preview_failed'), { once: true });
          // Permission can succeed while a device fails to produce frames (for example, when a
          // camera is disconnected). Always stop tracks rather than leaving an invisible stream.
          video.play().catch((error) => fail(error, 'camera_capture_preview_failed'));
        }).catch((error) => fail(error, 'camera_permission_denied'));
      } catch (error) { fail(error, 'camera_capture_failed'); }
    })()
    """,
)

private fun browserReleaseCameraCapture(reference: String): Unit = js(
    """
    (() => {
    if (typeof reference === 'string' && reference.startsWith('blob:')) {
      try { globalThis.URL?.revokeObjectURL(reference); } catch (_) {}
    }
    })()
    """,
)

private const val BrowserCameraUnsupported = "unsupported"
private const val BrowserCameraCancelled = "cancelled"
private const val BrowserCameraFailurePrefix = "failure:"
