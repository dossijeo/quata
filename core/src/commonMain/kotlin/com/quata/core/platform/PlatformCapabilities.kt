package com.quata.core.platform

/** Platform capabilities injected into shared presentation and use cases. */
expect class PlatformCamera() { fun isAvailable(): Boolean }

/**
 * Reports whether this platform exposes a potential clipboard implementation, not whether a
 * particular read or write is currently permitted. Browser clipboard access can still require a
 * secure context, an active user gesture, or an approval granted by the browser.
 */
expect class PlatformClipboard() { fun isAvailable(): Boolean }
expect class PlatformShare() { fun isAvailable(): Boolean }
expect class PlatformNotifications() { fun isAvailable(): Boolean }
expect class PlatformPreferences() { fun isAvailable(): Boolean }
expect class PlatformFilePicker() { fun isAvailable(): Boolean }
expect class PlatformPermissions() { fun isAvailable(): Boolean }
expect class PlatformLocation() { fun isAvailable(): Boolean }
expect class PlatformVideoPlayer() { fun isAvailable(): Boolean }
expect class PlatformAudioPlayer() { fun isAvailable(): Boolean }

interface DocumentPreviewer {
    fun canPreview(source: String, fileName: String, mimeType: String?): Boolean
}

object DefaultDocumentPreviewer : DocumentPreviewer {
    override fun canPreview(source: String, fileName: String, mimeType: String?): Boolean =
        DocumentSupport.canPreview(source, fileName, mimeType)
}

interface MediaEditor {
    fun supportsImageEditing(): Boolean
    fun supportsVideoEditing(): Boolean
}
