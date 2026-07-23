package com.quata.core.language

import com.quata.core.localization.QuataLanguage

fun interface TextLanguageIdentifier {
    suspend fun detect(text: String): QuataLanguageDetection
}

data class TranslatorBoxState(
    val originalText: String,
    val translation: String? = null,
    val directionLabel: String? = null,
    val showTranslation: Boolean = false,
    val isLoading: Boolean = false,
)

/** Shared policy behind the Fang overlay. Platform code supplies detection, cache and language preference. */
class FangOverlayTranslationUseCase(
    private val identifier: TextLanguageIdentifier,
    private val translator: TextTranslator,
    private val preferredLanguage: () -> QuataLanguage,
) {
    suspend fun translate(text: String): TranslatorBoxState? {
        val original = text.trim()
        if (original.isBlank()) return null
        val source = QuataTranslationLanguage.fromDetectedLanguage(identifier.detect(original).language) ?: return null
        val target = if (source == QuataTranslationLanguage.Fang) preferredLanguage().toTranslationLanguage() else QuataTranslationLanguage.Fang
        if (source == target) return TranslatorBoxState(originalText = original)
        val result = translator.translate(original, source, target)
        return TranslatorBoxState(
            originalText = original,
            translation = result.translation,
            directionLabel = "${source.shortCode()}→${target.shortCode()}",
            showTranslation = true,
        )
    }
}

fun QuataLanguage.toTranslationLanguage(): QuataTranslationLanguage = when (this) {
    QuataLanguage.Spanish -> QuataTranslationLanguage.Spanish
    QuataLanguage.French -> QuataTranslationLanguage.French
    QuataLanguage.English -> QuataTranslationLanguage.English
}

fun QuataTranslationLanguage.shortCode(): String = when (this) {
    QuataTranslationLanguage.Fang -> "FAN"
    QuataTranslationLanguage.Spanish -> "ES"
    QuataTranslationLanguage.English -> "EN"
    QuataTranslationLanguage.French -> "FR"
}
