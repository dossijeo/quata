package com.quata.core.moderation

import com.quata.core.localization.QuataLanguage
import com.quata.core.platform.PlatformFile
import platform.Foundation.NSBundle

fun iosLegalDocumentFile(document: LegalDocument, language: QuataLanguage): PlatformFile? {
    val assetName = document.assetName(language)
    val path = NSBundle.mainBundle.pathForResource(
        name = assetName.substringBeforeLast('.'),
        ofType = assetName.substringAfterLast('.'),
    ) ?: return null
    return PlatformFile(
        reference = path,
        displayName = assetName,
        mimeType = LegalDocumentDocxMimeType,
    )
}

fun iosLegalDocumentPlaceholderFile(document: LegalDocument, language: QuataLanguage): PlatformFile {
    val assetName = document.assetName(language)
    return PlatformFile(
        reference = assetName,
        displayName = assetName,
        mimeType = LegalDocumentDocxMimeType,
    )
}

private const val LegalDocumentDocxMimeType =
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
