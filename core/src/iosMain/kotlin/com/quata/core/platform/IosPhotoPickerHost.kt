package com.quata.core.platform

import kotlin.coroutines.resume
import kotlin.random.Random
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSFileManager
import platform.Foundation.NSItemProvider
import platform.Foundation.NSURL
import platform.Foundation.NSTemporaryDirectory
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/**
 * Real PhotosUI gallery adapter. PHPicker returns temporary representations, so each selection is
 * copied into the app temporary directory before it crosses the shared [PlatformFile] boundary.
 */
@OptIn(ExperimentalForeignApi::class)
class IosPhotoPickerHost(
    private val presenterProvider: IosViewControllerProvider,
) : IosFilePickerHost {
    private var activeDelegate: IosPhotoPickerDelegate? = null

    override suspend fun pick(request: IosFilePickerRequest): PlatformResult<List<PlatformFile>> {
        if (request.source != FilePickerSource.Gallery) return PlatformResult.Unsupported
        if (activeDelegate != null) return PlatformResult.Failure("gallery_picker_in_progress")
        val presenter: UIViewController = presenterProvider.activeViewController()
            ?: return PlatformResult.Unsupported
        return suspendCancellableCoroutine { continuation ->
            val picker = PHPickerViewController(
                configuration = PHPickerConfiguration().apply {
                    selectionLimit = if (request.allowMultiple) 0L else 1L
                },
            )
            lateinit var delegate: IosPhotoPickerDelegate
            delegate = IosPhotoPickerDelegate(request) { result ->
                if (activeDelegate === delegate) activeDelegate = null
                if (continuation.isActive) continuation.resume(result)
            }
            activeDelegate = delegate
            picker.delegate = delegate
            continuation.invokeOnCancellation {
                if (activeDelegate === delegate) {
                    activeDelegate = null
                    picker.dismissViewControllerAnimated(flag = true, completion = null)
                }
            }
            presenter.presentViewController(picker, animated = true, completion = null)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosPhotoPickerDelegate(
    private val request: IosFilePickerRequest,
    private val complete: (PlatformResult<List<PlatformFile>>) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    private var completed = false

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(flag = true, completion = null)
        val results = didFinishPicking.filterIsInstance<PHPickerResult>()
        if (results.isEmpty()) return finish(PlatformResult.Cancelled)
        val files = mutableListOf<PlatformFile>()
        var remaining = results.size
        var failure: String? = null
        results.forEach { result ->
            result.itemProvider.copyGalleryFile { copied ->
                when (copied) {
                    is PlatformResult.Success -> if (copied.value.matches(request.acceptedMimeTypes)) files += copied.value
                    is PlatformResult.Failure -> failure = copied.reason ?: "gallery_picker_copy_failed"
                    PlatformResult.Cancelled -> Unit
                    PlatformResult.Unsupported -> failure = "gallery_picker_representation_unsupported"
                }
                remaining -= 1
                if (remaining == 0) {
                    val finalResult: PlatformResult<List<PlatformFile>> = when {
                        failure != null -> PlatformResult.Failure(failure)
                        files.isNotEmpty() -> PlatformResult.Success(files)
                        else -> PlatformResult.Cancelled
                    }
                    finish(finalResult)
                }
            }
        }
    }

    private fun finish(result: PlatformResult<List<PlatformFile>>) {
        if (completed) return
        completed = true
        complete(result)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSItemProvider.copyGalleryFile(complete: (PlatformResult<PlatformFile>) -> Unit) {
    val typeIdentifier = registeredTypeIdentifiers.firstOrNull()
        ?: return complete(PlatformResult.Unsupported)
    loadFileRepresentationForTypeIdentifier(typeIdentifier) { source, error ->
        if (source == null || error != null) return@loadFileRepresentationForTypeIdentifier complete(PlatformResult.Failure(error?.localizedDescription))
        val extension = (source.pathExtension as? String)?.takeIf(String::isNotBlank)
        val destination = NSURL.fileURLWithPath(
            NSTemporaryDirectory() + "quata_gallery_${Random.nextLong().toString(16)}" + extension?.let { ".$it" }.orEmpty(),
        )
        val copied = NSFileManager.defaultManager.copyItemAtURL(source, destination, null)
        if (!copied) return@loadFileRepresentationForTypeIdentifier complete(PlatformResult.Failure("gallery_picker_copy_failed"))
        complete(
            PlatformResult.Success(
                PlatformFile(
                    reference = destination.absoluteString ?: destination.path.orEmpty(),
                    displayName = destination.lastPathComponent,
                    mimeType = extension?.galleryMimeType(),
                ),
            ),
        )
    }
}

private fun PlatformFile.matches(acceptedMimeTypes: List<String>): Boolean {
    val accepted = acceptedMimeTypes.map(String::lowercase).filter(String::isNotBlank)
    if (accepted.isEmpty() || "*/*" in accepted) return true
    val type = mimeType?.lowercase() ?: return false
    return type in accepted || accepted.any { it.endsWith("/*") && type.startsWith(it.removeSuffix("*")) }
}

private fun String.galleryMimeType(): String? = when (lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "heic" -> "image/heic"
    "mp4" -> "video/mp4"
    "mov" -> "video/quicktime"
    else -> null
}
