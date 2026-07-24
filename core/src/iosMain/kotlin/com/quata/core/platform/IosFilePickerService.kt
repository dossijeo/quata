package com.quata.core.platform

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/** Request passed from the context-free common contract to an iOS UIKit host. */
data class IosFilePickerRequest(
    val acceptedMimeTypes: List<String>,
    val allowMultiple: Boolean,
    val source: FilePickerSource,
)

/**
 * UIKit/SwiftUI host boundary for a system picker. The host owns delegates, presentation and any
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

    @Volatile
    private var galleryHost: IosFilePickerHost? = null

    fun attachHost(host: IosFilePickerHost) {
        this.host = host
    }

    fun detachHost(host: IosFilePickerHost) {
        if (this.host === host) this.host = null
        if (this.galleryHost === host) this.galleryHost = null
    }

    /** Attaches the real UIKit document picker while keeping the UIViewController host injected. */
    fun attachDocumentPicker(presenterProvider: IosViewControllerProvider): IosDocumentPickerHost =
        IosDocumentPickerHost(presenterProvider).also(::attachHost)

    /** Attaches the real PhotosUI gallery picker; camera remains a separate AVFoundation boundary. */
    fun attachGalleryPicker(presenterProvider: IosViewControllerProvider): IosPhotoPickerHost =
        IosPhotoPickerHost(presenterProvider).also { galleryHost = it }

    override suspend fun pickFiles(
        acceptedMimeTypes: List<String>,
        allowMultiple: Boolean,
    ): PlatformResult<List<PlatformFile>> = pick(
        FilePickerRequest(acceptedMimeTypes, allowMultiple, FilePickerSource.Documents),
    )

    /**
     * The injected UIKit adapter is a document importer, not a PhotosUI/camera implementation.
     * Do not report gallery support merely because image MIME types can be selected as documents.
     */
    override suspend fun pick(request: FilePickerRequest): PlatformResult<List<PlatformFile>> = requests.withLock {
        val activeHost = when (request.source) {
            FilePickerSource.Documents -> host
            FilePickerSource.Gallery -> galleryHost
            FilePickerSource.Camera -> null
        } ?: return@withLock PlatformResult.Unsupported
        activeHost.pick(IosFilePickerRequest(request.acceptedMimeTypes, request.allowMultiple, request.source))
    }
}
