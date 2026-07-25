package com.quata.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Photos.PHAccessLevelReadWrite
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusDenied
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHAuthorizationStatusRestricted
import platform.Photos.PHPhotoLibrary
import kotlin.coroutines.resume

/**
 * Real Photos authorization boundary for explicit photo-library access.
 *
 * PHPicker can return user-selected representations without this authorization, so opening the
 * existing gallery picker must not call this service. This permission is only for a feature that
 * needs direct `PHPhotoLibrary` read/write access. iOS may grant `limited` access; the portable
 * contract has no partial state, therefore it is reported as [PermissionStatus.Granted] while
 * callers must still tolerate access to only the user-selected assets.
 *
 * The iOS target must declare `NSPhotoLibraryUsageDescription`. This adapter requests the
 * read/write level; it deliberately does not request add-only access and does not write assets.
 */
@OptIn(ExperimentalForeignApi::class)
class IosPhotosPermissionService : PermissionService {
    override suspend fun status(permission: PlatformPermission): PermissionStatus = when (permission) {
        PlatformPermission.Photos -> PHPhotoLibrary
            .authorizationStatusForAccessLevel(PHAccessLevelReadWrite)
            .toPhotosPermissionStatus()
        else -> PermissionStatus.Unavailable
    }

    override suspend fun request(permission: PlatformPermission): PermissionStatus {
        if (permission != PlatformPermission.Photos) return PermissionStatus.Unavailable
        val current = status(permission)
        if (current != PermissionStatus.Denied) return current
        return suspendCancellableCoroutine { continuation ->
            PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelReadWrite) { status ->
                if (continuation.isActive) {
                    continuation.resume(status.toPhotosPermissionStatus())
                }
            }
        }
    }
}

private fun Long.toPhotosPermissionStatus(): PermissionStatus = when (this) {
    PHAuthorizationStatusAuthorized,
    PHAuthorizationStatusLimited -> PermissionStatus.Granted
    PHAuthorizationStatusDenied,
    PHAuthorizationStatusRestricted -> PermissionStatus.PermanentlyDenied
    PHAuthorizationStatusNotDetermined -> PermissionStatus.Denied
    else -> PermissionStatus.Unavailable
}
