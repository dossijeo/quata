package com.quata.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSProcessInfo
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
    // Keep AVFoundation at the iOS composition boundary. The portable services still accept
    // injected hosts for tests or a launcher that needs to coordinate a different audio policy.
    private val audioRecorderHost: IosAudioRecorderHost = iosAudioRecorderHost()
    private val audioPlayerHost: IosAudioPlayerHost = IosAvFoundationAudioPlayerHost()

    /**
     * Real adapters: UIKit share/document/gallery/camera, Core Location, notification permission,
     * NSUserDefaults, UIPasteboard and AVFoundation audio. AVFoundation is installed here rather
     * than in common code: microphone authorization and the app-wide audio session stay on iOS.
     */
    val services: IosPlatformServices = IosPlatformServices(
        presenterProvider = this,
        locationHost = coreLocationHost,
        audioRecorderHost = audioRecorderHost,
        audioPlayerHost = audioPlayerHost,
    )

    /** Attaches the visible UIKit controller that may present system sheets and pickers. */
    fun attachPresenter(controller: UIViewController) {
        presenter = controller
    }

    /** Allows a future navigation host to release a controller when its scene is discarded. */
    fun detachPresenter(controller: UIViewController) {
        // Swift can bridge the same Objective-C UIViewController through a different Kotlin
        // wrapper. `==` delegates to Objective-C equality, while `===` only compares wrappers.
        // Keep the guard so an unrelated scene/controller cannot detach the active presenter.
        if (presenter == controller) presenter = null
    }

    override fun activeViewController(): UIViewController? = presenter
}

private const val IosAudioRecorderEvidenceFakeEnv = "QUATA_IOS_AUDIO_RECORDER_E2E_FAKE"

private fun iosAudioRecorderHost(): IosAudioRecorderHost =
    if (NSProcessInfo.processInfo.environment[IosAudioRecorderEvidenceFakeEnv]?.toString() == "1") {
        IosEvidenceAudioRecorderHost()
    } else {
        IosAvFoundationAudioHost()
    }
