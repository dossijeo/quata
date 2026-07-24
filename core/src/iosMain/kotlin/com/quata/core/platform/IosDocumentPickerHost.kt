package com.quata.core.platform

import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTType
import platform.darwin.NSObject

/** Supplies the active UIKit presenter without leaking it into common feature code. */
fun interface IosViewControllerProvider {
    fun activeViewController(): UIViewController?
}

/**
 * Concrete UIKit implementation of [IosFilePickerHost].
 *
 * Import mode gives the application a sandboxed copy, so the returned NSURL can be consumed
 * after the delegate callback without retaining a security-scoped external document URL.
 */
@OptIn(ExperimentalForeignApi::class)
class IosDocumentPickerHost(
    private val presenterProvider: IosViewControllerProvider,
) : IosFilePickerHost {
    private var activeDelegate: IosDocumentPickerDelegate? = null

    override suspend fun pick(request: IosFilePickerRequest): PlatformResult<List<PlatformFile>> {
        val presenter = presenterProvider.activeViewController() ?: return PlatformResult.Unsupported
        val contentTypes = request.acceptedMimeTypes.toDocumentContentTypes()
        if (contentTypes.isEmpty()) return PlatformResult.Failure("file_picker_content_type_invalid")
        return suspendCancellableCoroutine { continuation ->
            val picker = UIDocumentPickerViewController(
                forOpeningContentTypes = contentTypes,
                asCopy = true,
            ).apply {
                allowsMultipleSelection = request.allowMultiple
            }
            lateinit var delegate: IosDocumentPickerDelegate
            delegate = IosDocumentPickerDelegate { result ->
                if (activeDelegate === delegate) activeDelegate = null
                if (continuation.isActive) continuation.resume(result)
            }
            activeDelegate = delegate // UIKit's delegate reference is weak.
            picker.delegate = delegate
            continuation.invokeOnCancellation {
                if (activeDelegate === delegate) activeDelegate = null
            }
            presenter.presentViewController(picker, animated = true, completion = null)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosDocumentPickerDelegate(
    private val complete: (PlatformResult<List<PlatformFile>>) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    private var completed = false

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val files = didPickDocumentsAtURLs
            .filterIsInstance<NSURL>()
            .map(NSURL::toPlatformFile)
        finish(if (files.isEmpty()) PlatformResult.Cancelled else PlatformResult.Success(files))
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        finish(PlatformResult.Cancelled)
    }

    private fun finish(result: PlatformResult<List<PlatformFile>>) {
        if (completed) return
        completed = true
        complete(result)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSURL.toPlatformFile(): PlatformFile = PlatformFile(
    reference = absoluteString ?: path.orEmpty(),
    displayName = lastPathComponent,
    mimeType = pathExtension?.toMimeType(),
)

/** UIDocumentPicker's legacy initializer receives UTI identifiers, not browser MIME strings. */
private fun List<String>.toDocumentContentTypes(): List<UTType> {
    val identifiers = if (isEmpty()) listOf("public.item") else mapNotNull { mimeType ->
        when (mimeType.trim().lowercase()) {
            "*/*" -> "public.item"
            "image/*" -> "public.image"
            "video/*" -> "public.movie"
            "audio/*" -> "public.audio"
            "text/*" -> "public.text"
            "application/pdf" -> "com.adobe.pdf"
            "application/json" -> "public.json"
            "application/zip", "application/x-zip-compressed" -> "public.zip-archive"
            else -> mimeType.takeIf { it.startsWith("public.") || it.startsWith("com.") }
                ?: "public.data"
        }
    }.distinct().ifEmpty { listOf("public.item") }
    return identifiers.mapNotNull(UTType::typeWithIdentifier)
}

private fun String.toMimeType(): String? = when (lowercase()) {
    "pdf" -> "application/pdf"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "mp4" -> "video/mp4"
    "mov" -> "video/quicktime"
    "m4a" -> "audio/mp4"
    "mp3" -> "audio/mpeg"
    "wav" -> "audio/wav"
    "txt" -> "text/plain"
    "json" -> "application/json"
    else -> null
}
