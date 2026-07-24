package com.quata.core.platform

actual class PlatformCamera actual constructor() { actual fun isAvailable() = false }
actual class PlatformClipboard actual constructor() { actual fun isAvailable() = true }
actual class PlatformShare actual constructor() { actual fun isAvailable() = false }
/** iOS can request/query notification authorization; APNs delivery remains host-owned. */
actual class PlatformNotifications actual constructor() { actual fun isAvailable() = true }
actual class PlatformPreferences actual constructor() { actual fun isAvailable() = true }
actual class PlatformFilePicker actual constructor() { actual fun isAvailable() = false }
/** Notification permission is available without requiring a UIKit presenter. */
actual class PlatformPermissions actual constructor() { actual fun isAvailable() = true }
actual class PlatformLocation actual constructor() { actual fun isAvailable() = false }
actual class PlatformVideoPlayer actual constructor() { actual fun isAvailable() = false }
actual class PlatformAudioPlayer actual constructor() { actual fun isAvailable() = false }
