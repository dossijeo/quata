package com.quata.core.platform

import platform.UIKit.UIPasteboard

/** iOS-backed clipboard adapter using the system general pasteboard. */
class IosClipboardService : ClipboardService {
    override suspend fun readText(): String? = UIPasteboard.generalPasteboard.string

    override suspend fun writeText(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }
}
