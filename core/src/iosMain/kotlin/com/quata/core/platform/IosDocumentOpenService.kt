package com.quata.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.QuickLook.QLPreviewController
import platform.QuickLook.QLPreviewControllerDataSourceProtocol
import platform.QuickLook.QLPreviewItemProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

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
        val dataSource = IosQuickLookDataSource(url)
        val preview = QLPreviewController().apply { this.dataSource = dataSource }
        activeDataSource = dataSource
        activePreview = preview
        presenter.presentViewController(preview, animated = true, completion = null)
        return PlatformResult.Success(Unit)
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
