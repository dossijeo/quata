@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.core.platform

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Browser permission adapter. Only standard browser permission names are exposed; permissions
 * without a portable Web API deliberately return [PermissionStatus.Unavailable].
 */
class BrowserPermissionService : PermissionService {
    override suspend fun status(permission: PlatformPermission): PermissionStatus {
        if (permission == PlatformPermission.Notifications) {
            return browserNotificationStatus().toPermissionStatus()
        }
        // `Permissions.query({ name: "geolocation" })` must never be used to imply that an
        // insecure origin can request location. It does not prompt, but can report a stale state
        // inconsistent with the API the LocationService is actually able to call.
        if (permission == PlatformPermission.Location && !browserGeolocationIsAvailable()) {
            return PermissionStatus.Unavailable
        }
        val browserName = permission.browserPermissionName() ?: return PermissionStatus.Unavailable
        return browserPermissionStatus(browserName).toPermissionStatus()
    }

    override suspend fun request(permission: PlatformPermission): PermissionStatus = when (permission) {
        PlatformPermission.Notifications -> browserRequestNotificationPermission().toPermissionStatus()
        PlatformPermission.Camera -> browserRequestMediaPermission(video = true).toPermissionStatus()
        PlatformPermission.Microphone -> browserRequestMediaPermission(video = false).toPermissionStatus()
        PlatformPermission.Location -> if (browserGeolocationIsAvailable()) {
            browserRequestLocationPermission().toPermissionStatus()
        } else {
            PermissionStatus.Unavailable
        }
        PlatformPermission.Photos,
        PlatformPermission.Videos,
        PlatformPermission.Files,
        PlatformPermission.Contacts -> PermissionStatus.Unavailable
    }
}

private fun PlatformPermission.browserPermissionName(): String? = when (this) {
    PlatformPermission.Camera -> "camera"
    PlatformPermission.Microphone -> "microphone"
    PlatformPermission.Location -> "geolocation"
    PlatformPermission.Notifications -> "notifications"
    PlatformPermission.Photos,
    PlatformPermission.Videos,
    PlatformPermission.Files,
    PlatformPermission.Contacts -> null
}

private fun String?.toPermissionStatus(): PermissionStatus = when (this) {
    "granted" -> PermissionStatus.Granted
    "denied" -> PermissionStatus.Denied
    // "prompt" and an unavailable API do not prove that the permission can be requested.
    else -> PermissionStatus.Unavailable
}

private suspend fun browserPermissionStatus(name: String): String? = suspendCoroutine { continuation ->
    browserQueryPermission(name) { state -> continuation.resume(state) }
}

private suspend fun browserRequestNotificationPermission(): String? = suspendCoroutine { continuation ->
    browserRequestNotification { state -> continuation.resume(state) }
}

private fun browserNotificationStatus(): String? = js("globalThis.Notification?.permission ?? null")

private suspend fun browserRequestMediaPermission(video: Boolean): String? = suspendCoroutine { continuation ->
    browserRequestMedia(video) { state -> continuation.resume(state) }
}

private suspend fun browserRequestLocationPermission(): String? = suspendCoroutine { continuation ->
    browserRequestLocation { state -> continuation.resume(state) }
}

private fun browserQueryPermission(name: String, callback: (String?) -> Unit): Unit = js("""
    (() => {
    const permissions = globalThis.navigator?.permissions;
    if (!permissions?.query) { callback(null); return; }
    permissions.query({ name: name })
        .then(result => callback(result.state))
        .catch(() => callback(null));
    })()
""")

private fun browserRequestNotification(callback: (String?) -> Unit): Unit = js("""
    (() => {
    const notification = globalThis.Notification;
    if (!notification?.requestPermission) { callback(null); return; }
    notification.requestPermission()
        .then(result => callback(result))
        .catch(() => callback("denied"));
    })()
""")

private fun browserRequestMedia(video: Boolean, callback: (String?) -> Unit): Unit = js("""
    (() => {
    const getUserMedia = globalThis.navigator?.mediaDevices?.getUserMedia;
    if (!getUserMedia) { callback(null); return; }
    getUserMedia.call(globalThis.navigator.mediaDevices, video ? { video: true } : { audio: true })
        .then(stream => {
            stream.getTracks().forEach(track => track.stop());
            callback("granted");
        })
        .catch(error => callback(error?.name === "NotAllowedError" ? "denied" : null));
    })()
""")

private fun browserRequestLocation(callback: (String?) -> Unit): Unit = js("""
    (() => {
    const geolocation = globalThis.navigator?.geolocation;
    if (!geolocation?.getCurrentPosition) { callback(null); return; }
    const timeoutMillis = 10000;
    let settled = false;
    const finish = value => {
        if (settled) return;
        settled = true;
        globalThis.clearTimeout(fallbackTimeout);
        callback(value);
    };
    // The Geolocation timeout is not guaranteed to fire when a page is suspended. Finalise the
    // one-shot permission request ourselves so its coroutine cannot be retained indefinitely.
    const fallbackTimeout = globalThis.setTimeout(() => finish(null), timeoutMillis + 1000);
    try {
        geolocation.getCurrentPosition(
            () => finish("granted"),
            error => finish(error?.code === 1 ? "denied" : null),
            { enableHighAccuracy: false, timeout: timeoutMillis, maximumAge: 0 }
        );
    } catch (_) {
        finish(null);
    }
    })()
""")
