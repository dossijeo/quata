package com.quata.core.platform

import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIViewController

/**
 * Activity-sheet presenter owned by the UIKit/SwiftUI host. Keeping it injected means shared
 * features can request sharing without retaining a UIViewController.
 */
fun interface IosSharePresenter {
    fun present(activityController: UIActivityViewController): PlatformResult<Unit>
}

/** Real UIKit sheet presenter backed by the active host controller supplied by the launcher. */
class IosUIKitSharePresenter(
    private val presenterProvider: IosViewControllerProvider,
) : IosSharePresenter {
    override fun present(activityController: UIActivityViewController): PlatformResult<Unit> {
        val presenter: UIViewController = presenterProvider.activeViewController()
            ?: return PlatformResult.Unsupported
        return runCatching {
            presenter.presentViewController(activityController, animated = true, completion = null)
            PlatformResult.Success(Unit)
        }.getOrElse { PlatformResult.Failure(it.message) }
    }
}

/**
 * Real iOS share adapter. A host must inject [IosSharePresenter] from an active UIKit hierarchy;
 * otherwise the operation is explicitly unsupported rather than reported as successful.
 */
class IosShareService(
    private val presenter: IosSharePresenter? = null,
) : ShareService {
    constructor(presenterProvider: IosViewControllerProvider) : this(IosUIKitSharePresenter(presenterProvider))

    override suspend fun share(payload: SharePayload): PlatformResult<Unit> = runCatching {
        val activePresenter = presenter ?: return PlatformResult.Unsupported
        val fileUrls = payload.files.map { file ->
            NSURL.URLWithString(file.reference) ?: return PlatformResult.Unsupported
        }
        val items = buildList<Any> {
            payload.text?.takeIf(String::isNotBlank)?.let(::add)
            addAll(fileUrls)
        }
        if (items.isEmpty()) return PlatformResult.Failure("share_payload_empty")
        activePresenter.present(
            UIActivityViewController(
                activityItems = items,
                applicationActivities = null,
            )
        )
    }.getOrElse { PlatformResult.Failure(it.message) }
}
