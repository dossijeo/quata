package com.quata.core.platform

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * MainActivity-owned Activity Result bridge for [AndroidFilePickerService]. Documents use the
 * Storage Access Framework (and retainable URI grants); gallery requests use GET_CONTENT. It does
 * not expose Android launchers to common code.
 */
class MainActivityFilePickerHost(
    private val activity: ComponentActivity,
) : AndroidFilePickerHost {
    private var pending: kotlinx.coroutines.CancellableContinuation<PlatformResult<List<Uri>>>? = null

    private val singleDocument = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        complete(uri?.let(::listOf) ?: emptyList())
    }
    private val multipleDocuments = activity.registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        complete(uris)
    }
    private val singleGallery = activity.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        complete(uri?.let(::listOf) ?: emptyList())
    }
    private val multipleGallery = activity.registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        complete(uris)
    }

    override suspend fun pick(request: AndroidFilePickerRequest): PlatformResult<List<Uri>> =
        suspendCancellableCoroutine { continuation ->
            if (pending != null) {
                continuation.resume(PlatformResult.Failure("file_picker_request_in_progress"))
                return@suspendCancellableCoroutine
            }
            pending = continuation
            continuation.invokeOnCancellation {
                if (pending === continuation) pending = null
            }
            lastRequestSource = request.source
            when (request.source) {
                FilePickerSource.Documents -> {
                    val mimeTypes = request.acceptedMimeTypes.filter { it.isNotBlank() }
                        .ifEmpty { listOf("*/*") }
                        .toTypedArray()
                    if (request.allowMultiple) multipleDocuments.launch(mimeTypes) else singleDocument.launch(mimeTypes)
                }
                FilePickerSource.Gallery -> {
                    val mimeType = request.acceptedMimeTypes.galleryMimeType()
                    if (request.allowMultiple) multipleGallery.launch(mimeType) else singleGallery.launch(mimeType)
                }
                FilePickerSource.Camera -> {
                    pending = null
                    continuation.resume(PlatformResult.Unsupported)
                }
            }
        }

    fun close() {
        pending?.let { continuation ->
            pending = null
            if (continuation.isActive) continuation.resume(PlatformResult.Cancelled)
        }
    }

    private fun complete(uris: List<Uri>) {
        val continuation = pending ?: return
        pending = null
        if (!continuation.isActive) return
        uris.forEach { uri ->
            if (lastRequestSource == FilePickerSource.Documents) {
                runCatching {
                    activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        }
        continuation.resume(
            if (uris.isEmpty()) PlatformResult.Cancelled else PlatformResult.Success(uris)
        )
    }

    private var lastRequestSource: FilePickerSource = FilePickerSource.Documents

    private fun List<String>.galleryMimeType(): String =
        firstOrNull { it.startsWith("image/") || it.startsWith("video/") }
            ?: "image/*"
}
