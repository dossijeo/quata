package com.quata.core.moderation

import com.quata.core.localization.QuataLanguage

enum class LegalDocument {
    Privacy,
    ChildSafety,
}

data class LegalDocumentLabelSet(
    val privacy: String,
    val childSafety: String,
)

fun legalDocumentLabels(language: QuataLanguage): LegalDocumentLabelSet = when (language) {
    QuataLanguage.Spanish -> LegalDocumentLabelSet(
        privacy = "Politica de privacidad",
        childSafety = "Seguridad infantil y normas de la comunidad",
    )
    QuataLanguage.French -> LegalDocumentLabelSet(
        privacy = "Politique de confidentialite",
        childSafety = "Securite des enfants et regles de la communaute",
    )
    QuataLanguage.English -> LegalDocumentLabelSet(
        privacy = "Privacy policy",
        childSafety = "Child safety and community standards",
    )
}

fun LegalDocument.label(labels: LegalDocumentLabelSet): String = when (this) {
    LegalDocument.Privacy -> labels.privacy
    LegalDocument.ChildSafety -> labels.childSafety
}

fun LegalDocument.publicUrl(): String = when (this) {
    LegalDocument.Privacy -> LegalLinks.Privacy
    LegalDocument.ChildSafety -> LegalLinks.ChildSafety
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
