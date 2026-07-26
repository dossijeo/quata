package com.quata.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.QuickLook.QLPreviewController
import platform.QuickLook.QLPreviewControllerDataSourceProtocol
import platform.QuickLook.QLPreviewItemProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

/** Real Quick Look adapter for local, sandbox-readable document URLs. */
@OptIn(ExperimentalForeignApi::class)
class IosDocumentOpenService(
    private val presenterProvider: IosViewControllerProvider,
) : DocumentOpenService {
    private var activePreview: QLPreviewController? = null
    private var activeDataSource: IosQuickLookDataSource? = null

    override suspend fun open(file: PlatformFile): PlatformResult<Unit> {
        val presenter = presenterProvider.activeViewController() ?: return PlatformResult.Unsupported
        if (DocumentPreviewAdmissions.admit(file, DocumentPreviewAdmissions.QuickLook) !is DocumentPreviewAdmission.Open) {
            return PlatformResult.Unsupported
        }
        val url = iosDocumentLocalUrlOrNull(file.reference) ?: return PlatformResult.Unsupported
        val path = url.path ?: return PlatformResult.Failure("document_open_source_path_missing")
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) {
            return PlatformResult.Failure("document_open_source_missing")
        }
        return suspendCancellableCoroutine { continuation ->
            // URLSession-backed feature adapters may resume on a delegate queue. UIKit and the
            // retained Quick Look data source must instead be mutated on the main queue.
            dispatch_async(dispatch_get_main_queue()) {
                if (!continuation.isActive) return@dispatch_async
                val dataSource = IosQuickLookDataSource(url)
                val preview = QLPreviewController().apply { this.dataSource = dataSource }
                activeDataSource = dataSource
                activePreview = preview
                presenter.presentViewController(preview, animated = true, completion = null)
                continuation.resume(PlatformResult.Success(Unit))
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosQuickLookDataSource(
    private val url: NSURL,
) : NSObject(), QLPreviewControllerDataSourceProtocol {
    override fun numberOfPreviewItemsInPreviewController(controller: QLPreviewController): Long = 1L

    override fun previewController(
        controller: QLPreviewController,
        previewItemAtIndex: Long,
    ): QLPreviewItemProtocol = url as QLPreviewItemProtocol
}
