package com.quata.core.moderation

import com.quata.core.localization.QuataLanguage

enum class LegalDocument {
    Privacy,
    ChildSafety,
}

fun LegalDocument.assetName(language: QuataLanguage): String {
    val languageCode = when (language) {
        QuataLanguage.Spanish -> "es"
        QuataLanguage.French -> "fr"
        QuataLanguage.English -> "en"
    }
    val prefix = when (this) {
        LegalDocument.Privacy -> "privacy"
        LegalDocument.ChildSafety -> "child_safety"
    }
    return "${prefix}_${languageCode}.docx"
}
