package com.quata.core.platform

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

/** MainActivity-owned `TakePicture` bridge that returns a FileProvider-backed content URI. */
class MainActivityCameraCaptureHost(
    private val activity: ComponentActivity,
) : AndroidCameraCaptureHost {
    private var pending: CancellableContinuation<PlatformResult<PlatformFile>>? = null
    private var pendingFile: File? = null
    private var pendingUri: Uri? = null
    private var pendingRequest: CameraCaptureRequest? = null

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { succeeded ->
        val continuation = pending ?: return@registerForActivityResult
        val file = pendingFile
        val uri = pendingUri
        val request = pendingRequest
        clearPending()
        if (succeeded && file != null && uri != null && file.length() > 0L) {
            continuation.resume(
                PlatformResult.Success(
                    PlatformFile(
                        reference = uri.toString(),
                        displayName = request?.displayName?.takeIf { it.isNotBlank() } ?: file.name,
                        mimeType = request?.mimeType ?: "image/jpeg",
                        sizeBytes = file.length(),
                    ),
                ),
            )
        } else {
            file?.delete()
            continuation.resume(PlatformResult.Cancelled)
        }
    }

    override suspend fun capturePhoto(request: CameraCaptureRequest): PlatformResult<PlatformFile> =
        suspendCancellableCoroutine { continuation ->
            if (pending != null) {
                continuation.resume(PlatformResult.Failure("camera_capture_request_in_progress"))
                return@suspendCancellableCoroutine
            }
            val file = runCatching {
                File.createTempFile("quata_capture_", ".jpg", activity.cacheDir)
            }.getOrElse {
                continuation.resume(PlatformResult.Failure(it.message))
                return@suspendCancellableCoroutine
            }
            val uri = runCatching {
                FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
            }.getOrElse {
                file.delete()
                continuation.resume(PlatformResult.Failure(it.message))
                return@suspendCancellableCoroutine
            }
            pending = continuation
            pendingFile = file
            pendingUri = uri
            pendingRequest = request
            continuation.invokeOnCancellation {
                if (pending === continuation) {
                    file.delete()
                    clearPending()
                }
            }
            launcher.launch(uri)
        }

    fun close() {
        pending?.let { continuation ->
            pendingFile?.delete()
            clearPending()
            if (continuation.isActive) continuation.resume(PlatformResult.Cancelled)
        }
    }

    private fun clearPending() {
        pending = null
        pendingFile = null
        pendingUri = null
        pendingRequest = null
    }
}
