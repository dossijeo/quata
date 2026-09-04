package com.quata.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ObjCAction
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSFileManager
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSURL
import platform.QuickLook.QLPreviewController
import platform.QuickLook.QLPreviewControllerDataSourceProtocol
import platform.QuickLook.QLPreviewControllerDelegateProtocol
import platform.QuickLook.QLPreviewItemProtocol
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonItemStyle
import platform.UIKit.UINavigationController
import platform.UIKit.UIPresentationController
import platform.UIKit.UIViewController
import platform.UIKit.navigationItem
import platform.UIKit.presentationController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

/** Real Quick Look adapter for local, sandbox-readable document URLs. */
interface IosDismissAwareDocumentOpenService : DocumentOpenService {
    suspend fun open(file: PlatformFile, onDismiss: () -> Unit): PlatformResult<Unit>
    suspend fun open(
        file: PlatformFile,
        onDismiss: () -> Unit,
        onPreviewAccepted: () -> Unit,
    ): PlatformResult<Unit>
}

@OptIn(ExperimentalForeignApi::class)
class IosDocumentOpenService(
    private val presenterProvider: IosViewControllerProvider,
) : IosDismissAwareDocumentOpenService {
    private var activePreview: QLPreviewController? = null
    private var activeNavigationController: UINavigationController? = null
    private var activeDataSource: IosQuickLookDataSource? = null
    private var activeDelegate: IosQuickLookLifecycleDelegate? = null
    private var activeCloseTarget: IosQuickLookCloseTarget? = null

    override suspend fun open(file: PlatformFile): PlatformResult<Unit> = open(file) {}

    override suspend fun open(
        file: PlatformFile,
        onDismiss: () -> Unit,
    ): PlatformResult<Unit> = open(file, onDismiss) {}

    override suspend fun open(
        file: PlatformFile,
        onDismiss: () -> Unit,
        onPreviewAccepted: () -> Unit,
    ): PlatformResult<Unit> {
        val presenter = presenterProvider.activeViewController() ?: return PlatformResult.Unsupported
        if (DocumentPreviewAdmissions.admit(file, DocumentPreviewAdmissions.QuickLook) !is DocumentPreviewAdmission.Open) {
            return PlatformResult.Unsupported
        }
        val url = iosDocumentLocalUrlOrNull(file.reference) ?: return PlatformResult.Unsupported
        val path = url.path ?: return PlatformResult.Failure("document_open_source_path_missing")
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) {
            return PlatformResult.Failure("document_open_source_missing")
        }
        if (activePreview != null) {
            return PlatformResult.Failure("document_open_preview_already_presented")
        }
        return suspendCancellableCoroutine { continuation ->
            // URLSession-backed feature adapters may resume on a delegate queue. UIKit and the
            // retained Quick Look data source must instead be mutated on the main queue.
            dispatch_async(dispatch_get_main_queue()) {
                if (!continuation.isActive) return@dispatch_async
                if (activePreview != null) {
                    continuation.resume(PlatformResult.Failure("document_open_preview_already_presented"))
                    return@dispatch_async
                }
                val dataSource = IosQuickLookDataSource(
                    IosQuickLookPreviewItem(
                        url = url,
                        title = file.displayName,
                    ),
                )
                lateinit var preview: QLPreviewController
                var dismissed = false
                fun clearActivePreview() {
                    if (activePreview === preview) {
                        activePreview = null
                        activeNavigationController = null
                        activeDataSource = null
                        activeDelegate = null
                        activeCloseTarget = null
                    }
                }
                fun dismissAndRelease() {
                    if (dismissed) return
                    dismissed = true
                    runCatching(onDismiss)
                    clearActivePreview()
                }
                fun dismissPreviewAndRelease(animated: Boolean) {
                    if (dismissed) return
                    val presentedController = activeNavigationController ?: preview
                    presentedController.dismissViewControllerAnimated(animated) {
                        dismissAndRelease()
                    }
                }
                val delegate = IosQuickLookLifecycleDelegate {
                    dismissAndRelease()
                }
                preview = QLPreviewController().apply {
                    this.dataSource = dataSource
                    this.delegate = delegate
                }
                val closeTarget = IosQuickLookCloseTarget {
                    dismissPreviewAndRelease(animated = false)
                }
                val closeButton = UIBarButtonItem(
                    title = "Cerrar",
                    style = UIBarButtonItemStyle.UIBarButtonItemStylePlain,
                    target = closeTarget,
                    action = NSSelectorFromString("closeQuickLook"),
                )
                preview.navigationItem()?.setLeftBarButtonItem(closeButton)
                val navigationController = UINavigationController(rootViewController = preview).apply {
                    presentationController()?.setDelegate(delegate)
                }
                activeDataSource = dataSource
                activeDelegate = delegate
                activeCloseTarget = closeTarget
                activePreview = preview
                activeNavigationController = navigationController
                runCatching(onPreviewAccepted)
                continuation.invokeOnCancellation {
                    dispatch_async(dispatch_get_main_queue()) {
                        if (activePreview === preview) {
                            dismissPreviewAndRelease(animated = false)
                        }
                    }
                }
                presenter.presentViewController(navigationController, animated = true) {
                    val presented = navigationController.presentingViewController() != null ||
                        presenter.presentedViewController() === navigationController
                    if (!presented) {
                        dismissAndRelease()
                    }
                }
                if (continuation.isActive) {
                    continuation.resume(PlatformResult.Success(Unit))
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosQuickLookLifecycleDelegate(
    private val onDismiss: () -> Unit,
) : NSObject(), QLPreviewControllerDelegateProtocol, UIAdaptivePresentationControllerDelegateProtocol {
    override fun previewControllerDidDismiss(controller: QLPreviewController) {
        onDismiss()
    }

    override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
        onDismiss()
    }
}

@OptIn(BetaInteropApi::class)
private class IosQuickLookCloseTarget(
    private val onClose: () -> Unit,
) : NSObject() {
    @ObjCAction
    fun closeQuickLook() {
        onClose()
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosQuickLookPreviewItem(
    private val url: NSURL,
    private val title: String?,
) : NSObject(), QLPreviewItemProtocol {
    override fun previewItemURL(): NSURL? = url

    override fun previewItemTitle(): String? = title?.takeIf(String::isNotBlank) ?: url.lastPathComponent
}

@OptIn(ExperimentalForeignApi::class)
private class IosQuickLookDataSource(
    private val item: QLPreviewItemProtocol,
) : NSObject(), QLPreviewControllerDataSourceProtocol {
    override fun numberOfPreviewItemsInPreviewController(controller: QLPreviewController): Long = 1L

    override fun previewController(
        controller: QLPreviewController,
        previewItemAtIndex: Long,
    ): QLPreviewItemProtocol = item
}
