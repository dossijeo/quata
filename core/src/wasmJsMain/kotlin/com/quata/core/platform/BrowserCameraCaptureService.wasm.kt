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

    override suspend fun capturePhoto(request: CameraCaptureRequest): PlatformResult<PlatformFile> {
        if (!request.supportsBrowserJpeg()) return PlatformResult.Failure("camera_capture_mime_unsupported")
        if (captureInProgress) return PlatformResult.Failure("camera_capture_in_progress")
        captureInProgress = true
        return suspendCoroutine { continuation ->
            browserCaptureJpeg(request.displayName) { result ->
                captureInProgress = false
                continuation.resume(result.toCameraCaptureResult())
            }
        }
    }

    /** Releases a Blob URL returned by this service. Calling it for another reference is harmless. */
    fun release(file: PlatformFile) {
        browserReleaseCameraCapture(file.reference)
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
      const media = globalThis.navigator?.mediaDevices;
      if (!media || typeof media.getUserMedia !== 'function' || !globalThis.document?.createElement || !globalThis.URL?.createObjectURL) {
        onResult('unsupported');
        return;
      }
      let stream = null;
      let video = null;
      let finished = false;
      const cleanup = () => {
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
        else if (name === 'NotSupportedError' || name === 'OverconstrainedError') finish('unsupported');
        else finish('failure:' + name);
      };
      try {
        media.getUserMedia({
          video: { facingMode: { ideal: 'environment' } },
          audio: false,
        }).then((grantedStream) => {
          stream = grantedStream;
          video = globalThis.document.createElement('video');
          video.muted = true;
          video.playsInline = true;
          video.setAttribute('aria-hidden', 'true');
          video.style.cssText = 'position:fixed;width:1px;height:1px;opacity:0;pointer-events:none;left:-2px;top:-2px;';
          globalThis.document.body?.appendChild(video);
          video.srcObject = stream;
          const capture = () => {
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
