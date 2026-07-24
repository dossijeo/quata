package com.quata.core.platform

import platform.UIKit.UIPasteboard

/**
 * iOS-backed clipboard adapter. The pasteboard is injectable for app-group/named pasteboards and
 * tests, while production defaults to the system general pasteboard.
 */
class IosClipboardService(
    private val pasteboard: UIPasteboard = UIPasteboard.generalPasteboard,
) : ClipboardService {
    override suspend fun readText(): String? = pasteboard
        .takeIf { it.hasStrings }
        ?.string

    override suspend fun writeText(text: String) {
        pasteboard.string = text
    }
}
