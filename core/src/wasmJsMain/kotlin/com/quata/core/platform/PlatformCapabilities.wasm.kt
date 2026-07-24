package com.quata.core.platform

/** Runtime browser capability probes used by shared UI before requesting an injected service. */
actual class PlatformCamera actual constructor() { actual fun isAvailable() = browserCameraAvailable() }
actual class PlatformClipboard actual constructor() { actual fun isAvailable() = browserClipboardAvailable() }
actual class PlatformShare actual constructor() { actual fun isAvailable() = browserShareAvailable() }
actual class PlatformNotifications actual constructor() { actual fun isAvailable() = browserNotificationsAvailable() }
actual class PlatformPreferences actual constructor() { actual fun isAvailable() = true }
actual class PlatformFilePicker actual constructor() { actual fun isAvailable() = browserFilePickerAvailable() }
actual class PlatformPermissions actual constructor() { actual fun isAvailable() = browserPermissionsAvailable() }
actual class PlatformLocation actual constructor() { actual fun isAvailable() = browserLocationAvailable() }
actual class PlatformVideoPlayer actual constructor() { actual fun isAvailable() = browserVideoAvailable() }
actual class PlatformAudioPlayer actual constructor() { actual fun isAvailable() = browserAudioAvailable() }

private fun browserCameraAvailable(): Boolean =
    js("typeof globalThis.navigator?.mediaDevices?.getUserMedia === 'function'")

private fun browserClipboardAvailable(): Boolean =
    js("typeof globalThis.navigator?.clipboard?.writeText === 'function'")

private fun browserShareAvailable(): Boolean =
    js("typeof globalThis.navigator?.share === 'function'")

private fun browserNotificationsAvailable(): Boolean =
    js("typeof globalThis.Notification?.requestPermission === 'function'")

private fun browserFilePickerAvailable(): Boolean =
    js("typeof globalThis.document?.createElement === 'function'")

private fun browserPermissionsAvailable(): Boolean =
    js("typeof globalThis.navigator?.permissions?.query === 'function'")

private fun browserLocationAvailable(): Boolean =
    js("typeof globalThis.navigator?.geolocation?.getCurrentPosition === 'function'")

private fun browserVideoAvailable(): Boolean =
    js("typeof globalThis.document?.createElement === 'function'")

private fun browserAudioAvailable(): Boolean =
    js("typeof globalThis.AudioContext === 'function' || typeof globalThis.webkitAudioContext === 'function'")
