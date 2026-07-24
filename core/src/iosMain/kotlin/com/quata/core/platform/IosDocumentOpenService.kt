package com.quata.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
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
        val url = file.localFileUrlOrNull() ?: return PlatformResult.Unsupported
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

@OptIn(ExperimentalForeignApi::class)
private fun PlatformFile.localFileUrlOrNull(): NSURL? {
    val value = reference.trim()
    val url = when {
        value.startsWith("file://") -> NSURL(string = value)
        value.startsWith("/") -> NSURL.fileURLWithPath(value)
        else -> null
    }
    return url?.takeIf { it.isFileURL() }
}
