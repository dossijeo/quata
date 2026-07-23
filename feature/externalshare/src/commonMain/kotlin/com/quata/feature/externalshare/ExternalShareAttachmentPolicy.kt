package com.quata.feature.externalshare

import com.quata.core.platform.DocumentSupport

fun isSupportedSharedAttachment(
    source: String,
    fileName: String,
    mimeType: String?
): Boolean {
    val normalizedMime = mimeType?.substringBefore(';')?.lowercase().orEmpty()
    return normalizedMime.startsWith("image/") ||
        normalizedMime.startsWith("audio/") ||
        normalizedMime.startsWith("video/") ||
        DocumentSupport.canPreview(source, fileName, normalizedMime)
}
