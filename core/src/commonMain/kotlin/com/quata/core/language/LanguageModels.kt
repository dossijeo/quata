package com.quata.core.language

enum class QuataDetectedLanguage(val code: String) {
    Spanish("es"),
    French("fr"),
    English("en"),
    Fang("fan"),
    Unknown("und");

    companion object {
        fun fromCode(code: String): QuataDetectedLanguage = when (code.lowercase()) {
            "es" -> Spanish
            "fr" -> French
            "en" -> English
            "fan" -> Fang
            else -> Unknown
        }
    }
}

data class QuataLanguageScore(
    val language: QuataDetectedLanguage,
    val code: String,
    val confidence: Float
)

data class QuataLanguageDetection(
    val language: QuataDetectedLanguage,
    val code: String,
    val confidence: Float,
    val scores: List<QuataLanguageScore>
)

enum class QuataTranslationLanguage(val apiCode: String) {
    Fang("fan_Latn"),
    Spanish("spa_Latn"),
    English("eng_Latn"),
    French("fra_Latn");

    companion object {
        fun fromDetectedLanguage(language: QuataDetectedLanguage): QuataTranslationLanguage? = when (language) {
            QuataDetectedLanguage.Fang -> Fang
            QuataDetectedLanguage.Spanish -> Spanish
            QuataDetectedLanguage.English -> English
            QuataDetectedLanguage.French -> French
            QuataDetectedLanguage.Unknown -> null
        }

        fun fromApiCode(apiCode: String): QuataTranslationLanguage? = entries.firstOrNull {
            it.apiCode.equals(apiCode, ignoreCase = true)
        }
    }
}

data class QuataTranslationResult(
    val translation: String,
    val pivotUsed: Boolean,
    val route: List<QuataTranslationLanguage>,
    val pivotLanguage: QuataTranslationLanguage?,
    val pivotText: String?,
    val pivotEngine: String?
)

interface TextTranslator {
    suspend fun translate(
        text: String,
        sourceLanguage: QuataTranslationLanguage,
        targetLanguage: QuataTranslationLanguage
    ): QuataTranslationResult
}
