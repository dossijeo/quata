package com.quata.core.platform

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/** Request passed from the context-free common contract to an iOS UIKit host. */
data class IosFilePickerRequest(
    val acceptedMimeTypes: List<String>,
    val allowMultiple: Boolean,
)

/**
 * UIKit/SwiftUI host boundary for `UIDocumentPickerViewController` (or an
 * equivalent system picker). The host owns delegates, presentation and any
 * security-scoped URL access; shared code receives only portable files.
 */
fun interface IosFilePickerHost {
    suspend fun pick(request: IosFilePickerRequest): PlatformResult<List<PlatformFile>>
}

/**
 * iOS [FilePickerService] ready for host injection.
 *
 * There is deliberately no fallback picker: without an active UIKit presenter
 * a document picker cannot be displayed, so the contract returns Unsupported
 * rather than reporting a fabricated success.
 */
class IosFilePickerService : FilePickerService {
    private val requests = Mutex()

    @Volatile
    private var host: IosFilePickerHost? = null

    fun attachHost(host: IosFilePickerHost) {
        this.host = host
    }

    fun detachHost(host: IosFilePickerHost) {
        if (this.host === host) this.host = null
    }

    /** Attaches the real UIKit document picker while keeping the UIViewController host injected. */
    fun attachDocumentPicker(presenterProvider: IosViewControllerProvider): IosDocumentPickerHost =
        IosDocumentPickerHost(presenterProvider).also(::attachHost)

    override suspend fun pickFiles(
        acceptedMimeTypes: List<String>,
        allowMultiple: Boolean,
    ): PlatformResult<List<PlatformFile>> = requests.withLock {
        val activeHost = host ?: return@withLock PlatformResult.Unsupported
        activeHost.pick(IosFilePickerRequest(acceptedMimeTypes, allowMultiple))
    }
}
