package com.quata.core.platform

actual class PlatformCamera actual constructor() { actual fun isAvailable() = false }
actual class PlatformClipboard actual constructor() { actual fun isAvailable() = true }
/** UIKit provides sharing; [IosShareService] still requires an injected active presenter. */
actual class PlatformShare actual constructor() { actual fun isAvailable() = true }
/** iOS can request/query notification authorization; APNs delivery remains host-owned. */
actual class PlatformNotifications actual constructor() { actual fun isAvailable() = true }
actual class PlatformPreferences actual constructor() { actual fun isAvailable() = true }
/** UIKit supports document picking; [IosFilePickerService] still needs an active presenter host. */
actual class PlatformFilePicker actual constructor() { actual fun isAvailable() = true }
/** Notification permission is available without requiring a UIKit presenter. */
actual class PlatformPermissions actual constructor() { actual fun isAvailable() = true }
/** Core Location is available through [IosCoreLocationHost] injected by the launcher. */
actual class PlatformLocation actual constructor() { actual fun isAvailable() = true }
actual class PlatformVideoPlayer actual constructor() { actual fun isAvailable() = false }
actual class PlatformAudioPlayer actual constructor() { actual fun isAvailable() = false }
