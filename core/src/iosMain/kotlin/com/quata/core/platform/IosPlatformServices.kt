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
    override val preferences: PreferenceStore = IosPreferenceStore(),
    override val clipboard: ClipboardService = IosClipboardService(),
    override val share: ShareService = IosShareService(),
    override val filePicker: FilePickerService = IosFilePickerService(),
    override val location: LocationService = UnsupportedIosLocationService,
    override val permissions: PermissionService = IosNotificationPermissionService(),
) : PlatformServices

/** Explicit placeholder until a Core Location host is provided by iosApp. */
object UnsupportedIosLocationService : LocationService {
    override suspend fun currentLocation(): PlatformResult<GeoLocation> = PlatformResult.Unsupported
}

/** Explicit placeholder retained for callers that want all permission prompts disabled. */
object UnsupportedIosPermissionService : PermissionService {
    override suspend fun status(permission: PlatformPermission): PermissionStatus = PermissionStatus.Unavailable

    override suspend fun request(permission: PlatformPermission): PermissionStatus = PermissionStatus.Unavailable
}
