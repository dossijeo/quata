package com.quata.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewController

/**
 * UIKit composition root for the real iOS platform adapters.
 *
 * Swift retains one instance for the application lifetime and attaches whichever Compose host is
 * visible. Shared features receive [services] only when their own composition root is ready; this
 * class deliberately owns no repository, credentials or sample data.
 */
@OptIn(ExperimentalForeignApi::class)
class IosPlatformServiceComposition(
    private val coreLocationHost: IosCoreLocationHost = IosCoreLocationHost(),
) : IosViewControllerProvider {
    private var presenter: UIViewController? = null

    /**
     * Real adapters: UIKit share/document/gallery/camera, Core Location, notification permission,
     * NSUserDefaults and UIPasteboard. Audio remains explicitly unsupported until an AV host is
     * injected through [IosPlatformServices] by the consuming feature.
     */
    val services: IosPlatformServices = IosPlatformServices(
        presenterProvider = this,
        locationHost = coreLocationHost,
    )

    /** Attaches the visible UIKit controller that may present system sheets and pickers. */
    fun attachPresenter(controller: UIViewController) {
        presenter = controller
    }

    /** Allows a future navigation host to release a controller when its scene is discarded. */
    fun detachPresenter(controller: UIViewController) {
        if (presenter === controller) presenter = null
    }

    override fun activeViewController(): UIViewController? = presenter
}
