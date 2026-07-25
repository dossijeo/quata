package com.quata.core.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVAuthorizationStatus
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import kotlin.coroutines.resume

/**
 * Real AVFoundation camera authorization boundary.
 *
 * It intentionally owns only [PlatformPermission.Camera]. Microphone, Photos and Files remain
 * unavailable through [IosCompositePermissionService] until each has a platform-specific policy
 * and usage-description contract. The iOS target must declare `NSCameraUsageDescription`.
 */
class IosCameraPermissionService : PermissionService {
    override suspend fun status(permission: PlatformPermission): PermissionStatus = when (permission) {
        PlatformPermission.Camera -> AVCaptureDevice
            .authorizationStatusForMediaType(AVMediaTypeVideo)
            .toCameraPermissionStatus()
        else -> PermissionStatus.Unavailable
    }

    override suspend fun request(permission: PlatformPermission): PermissionStatus {
        if (permission != PlatformPermission.Camera) return PermissionStatus.Unavailable
        val current = status(permission)
        if (current != PermissionStatus.Denied) return current
        return suspendCancellableCoroutine { continuation ->
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted: Boolean ->
                if (!continuation.isActive) return@requestAccessForMediaType
                continuation.resume(
                    if (granted) PermissionStatus.Granted
                    else AVCaptureDevice
                        .authorizationStatusForMediaType(AVMediaTypeVideo)
                        .toCameraPermissionStatus(),
                )
            }
        }
    }
}

private fun AVAuthorizationStatus.toCameraPermissionStatus(): PermissionStatus = when (this) {
    AVAuthorizationStatusAuthorized -> PermissionStatus.Granted
    AVAuthorizationStatusDenied,
    AVAuthorizationStatusRestricted -> PermissionStatus.PermanentlyDenied
    AVAuthorizationStatusNotDetermined -> PermissionStatus.Denied
    else -> PermissionStatus.Unavailable
}
