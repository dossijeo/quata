package com.quata.core.language

import com.quata.core.localization.QuataLanguage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FangOverlayTranslationUseCaseTest {
    @Test
    fun translatesFangToThePreferredLanguage() = runTest {
        val useCase = FangOverlayTranslationUseCase(
            identifier = TextLanguageIdentifier { QuataLanguageDetection(QuataDetectedLanguage.Fang, "fan", 1f, emptyList()) },
            translator = FakeTranslator(),
            preferredLanguage = { QuataLanguage.French },
        )

        assertEquals(
            TranslatorBoxState("Mbolo", "Bonjour", "FAN→FR", showTranslation = true),
            useCase.translate("  Mbolo  "),
        )
    }

    @Test
    fun doesNotTranslateUnknownText() = runTest {
        val useCase = FangOverlayTranslationUseCase(
            identifier = TextLanguageIdentifier { QuataLanguageDetection(QuataDetectedLanguage.Unknown, "und", 1f, emptyList()) },
            translator = FakeTranslator(),
            preferredLanguage = { QuataLanguage.English },
        )

        assertNull(useCase.translate("???"))
    }

    private class FakeTranslator : TextTranslator {
        override suspend fun translate(text: String, sourceLanguage: QuataTranslationLanguage, targetLanguage: QuataTranslationLanguage) =
            QuataTranslationResult("Bonjour", false, listOf(sourceLanguage, targetLanguage), null, null, null)
    }
}
