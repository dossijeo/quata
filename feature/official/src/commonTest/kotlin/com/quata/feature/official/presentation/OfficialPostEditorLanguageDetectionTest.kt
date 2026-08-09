package com.quata.feature.official.presentation

import com.quata.core.language.QuataDetectedLanguage
import com.quata.core.language.QuataLanguageDetection
import com.quata.core.language.QuataLanguageScore
import com.quata.core.language.TextLanguageIdentifier
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostLanguage
import com.quata.feature.official.domain.OfficialPostType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfficialPostEditorLanguageDetectionTest {
    @Test
    fun mapsSupportedFastTextLanguagesToOfficialLanguages() = runTest {
        val draft = draft("Bonjour")

        val detection = detectOfficialPostLanguage(
            identifier = fixedIdentifier(QuataDetectedLanguage.French),
            draft = draft,
            fallback = OfficialPostLanguage.Spanish,
        )

        assertEquals(OfficialPostLanguage.French, detection.publicationLanguage)
        assertEquals(OfficialPostLanguage.French, detection.translationSourceLanguage)
    }

    @Test
    fun detectedFangPublishesFallbackWithoutPretendingItIsATranslatableOfficialLanguage() = runTest {
        val detection = detectOfficialPostLanguage(
            identifier = fixedIdentifier(QuataDetectedLanguage.Fang),
            draft = draft("Mbolo"),
            fallback = OfficialPostLanguage.Spanish,
        )

        assertEquals(OfficialPostLanguage.Spanish, detection.publicationLanguage)
        assertNull(detection.translationSourceLanguage)
    }

    @Test
    fun unknownLanguagePublishesFallbackWithoutAutomaticTranslation() = runTest {
        val detection = detectOfficialPostLanguage(
            identifier = fixedIdentifier(QuataDetectedLanguage.Unknown),
            draft = draft("???"),
            fallback = OfficialPostLanguage.English,
        )

        assertEquals(OfficialPostLanguage.English, detection.publicationLanguage)
        assertNull(detection.translationSourceLanguage)
    }

    @Test
    fun detectorFailureFallbackDoesNotPretendTheSourceIsTranslatable() {
        val detection = fallbackOfficialPostEditorLanguageDetection(OfficialPostLanguage.French)

        assertEquals(OfficialPostLanguage.French, detection.publicationLanguage)
        assertNull(detection.translationSourceLanguage)
    }

    private fun draft(text: String) = OfficialPostDraft(
        title = text,
        summary = "",
        contentHtml = "<p>$text</p>",
        language = OfficialPostLanguage.Spanish,
        type = OfficialPostType.Announcement,
    )

    private fun fixedIdentifier(language: QuataDetectedLanguage) = TextLanguageIdentifier {
        QuataLanguageDetection(
            language = language,
            code = language.code,
            confidence = 1f,
            scores = listOf(QuataLanguageScore(language, language.code, 1f)),
        )
    }
}
