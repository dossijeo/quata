package com.quata.core.platform

/**
 * iOS composition root for the shared platform boundaries.
 *
 * The UIKit/SwiftUI launcher may replace any service with a host-backed implementation while
 * keeping common features independent from UIKit. Location remains unsupported until a Core
 * Location host is attached; notifications use the real system permission adapter while every
 * other permission remains explicitly unavailable until its iOS host is implemented.
 */
class IosPlatformServices(
    presenterProvider: IosViewControllerProvider? = null,
    cameraCaptureHost: IosCameraCaptureHost? = null,
    audioRecorderHost: IosAudioRecorderHost? = null,
    audioPlayerHost: IosAudioPlayerHost? = null,
    locationHost: IosCoreLocationHost? = null,
    override val preferences: PreferenceStore = IosPreferenceStore(),
    override val clipboard: ClipboardService = IosClipboardService(),
    override val share: ShareService = presenterProvider?.let { provider -> IosShareService(provider) } ?: IosShareService(),
    override val filePicker: FilePickerService = IosFilePickerService().apply {
        presenterProvider?.let { provider ->
            attachDocumentPicker(provider)
            attachGalleryPicker(provider)
        }
    },
    override val contacts: ContactPickerService = IosContactPickerService().apply {
        presenterProvider?.let(::attachSystemPicker)
    },
    override val location: LocationService = locationHost ?: UnsupportedIosLocationService,
    override val permissions: PermissionService = locationHost?.let { host ->
        IosCompositePermissionService(location = host)
    } ?: IosNotificationPermissionService(),
) : PlatformServices {
    /** Feature-level boundary, injected by the UIKit launcher when camera capture is consumed. */
    val cameraCapture: IosCameraCaptureService = IosCameraCaptureService(
        cameraCaptureHost ?: presenterProvider?.let(::IosImagePickerCameraHost),
    )
    /** Exposed alongside platform services because audio is a feature-level injectable contract. */
    val audioRecorder: IosAudioRecorderService = IosAudioRecorderService(audioRecorderHost)
    val audioPlayer: IosAudioPlayerService = IosAudioPlayerService(audioPlayerHost)
    /** Local audio cache; unlike playback/recording it needs no UIKit or AVFoundation host. */
    val audioCache: AudioCacheService = IosAudioCacheService()
    /** Quick Look document opener; unavailable until the composition root supplies a presenter. */
    override val documentOpener: DocumentOpenService = presenterProvider?.let(::IosDocumentOpenService)
        ?: UnsupportedDocumentOpenService
    /** Quick Look Thumbnailing works without a visible presenter and returns portable PNG files. */
    val documentThumbnails: DocumentThumbnailService = IosDocumentThumbnailService()
    /** AVFoundation extracts a portable PNG from an existing local video without a UIKit host. */
    val videoThumbnails: VideoThumbnailService = IosVideoThumbnailService()
}

/** Explicit placeholder until a Core Location host is provided by iosApp. */
object UnsupportedIosLocationService : LocationService {
    override suspend fun currentLocation(): PlatformResult<GeoLocation> = PlatformResult.Unsupported
}

/** Explicit placeholder retained for callers that want all permission prompts disabled. */
object UnsupportedIosPermissionService : PermissionService {
    override suspend fun status(permission: PlatformPermission): PermissionStatus = PermissionStatus.Unavailable

    override suspend fun request(permission: PlatformPermission): PermissionStatus = PermissionStatus.Unavailable
}
