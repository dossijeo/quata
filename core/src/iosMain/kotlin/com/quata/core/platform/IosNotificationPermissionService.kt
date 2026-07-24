package com.quata.core.platform

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter

/**
 * Real system-notification permission adapter. It does not register APNs tokens or claim that
 * push delivery is configured; those operations remain owned by the iOS application delegate.
 */
class IosNotificationPermissionService(
    private val center: UNUserNotificationCenter = UNUserNotificationCenter.currentNotificationCenter(),
) : PermissionService {
    override suspend fun status(permission: PlatformPermission): PermissionStatus = when (permission) {
        PlatformPermission.Notifications -> notificationStatus()
        else -> PermissionStatus.Unavailable
    }

    override suspend fun request(permission: PlatformPermission): PermissionStatus {
        if (permission != PlatformPermission.Notifications) return PermissionStatus.Unavailable
        return suspendCoroutine { continuation ->
            center.requestAuthorizationWithOptions(
                options = UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound,
            ) { granted, error ->
                when {
                    granted -> continuation.resume(PermissionStatus.Granted)
                    error != null -> continuation.resume(PermissionStatus.Unavailable)
                    else -> center.getNotificationSettingsWithCompletionHandler { settings ->
                        continuation.resume(
                            settings?.authorizationStatus?.toNotificationPermissionStatus()
                                ?: PermissionStatus.Unavailable,
                        )
                    }
                }
            }
        }
    }

    private suspend fun notificationStatus(): PermissionStatus = suspendCoroutine { continuation ->
        center.getNotificationSettingsWithCompletionHandler { settings ->
            continuation.resume(
                settings?.authorizationStatus?.toNotificationPermissionStatus()
                    ?: PermissionStatus.Unavailable,
            )
        }
    }
}

private fun Long.toNotificationPermissionStatus(): PermissionStatus = when (this) {
    UNAuthorizationStatusAuthorized,
    UNAuthorizationStatusProvisional,
    UNAuthorizationStatusEphemeral -> PermissionStatus.Granted
    UNAuthorizationStatusDenied -> PermissionStatus.PermanentlyDenied
    else -> PermissionStatus.Denied
}
