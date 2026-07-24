package com.quata.core.platform

import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController

/**
 * Activity-sheet presenter owned by the UIKit/SwiftUI host. Keeping it injected means shared
 * features can request sharing without retaining a UIViewController.
 */
fun interface IosSharePresenter {
    fun present(activityController: UIActivityViewController)
}

/**
 * Real iOS share adapter. A host must inject [IosSharePresenter] from an active UIKit hierarchy;
 * otherwise the operation is explicitly unsupported rather than reported as successful.
 */
class IosShareService(
    private val presenter: IosSharePresenter? = null,
) : ShareService {
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
        PlatformResult.Success(Unit)
    }.getOrElse { PlatformResult.Failure(it.message) }
}
