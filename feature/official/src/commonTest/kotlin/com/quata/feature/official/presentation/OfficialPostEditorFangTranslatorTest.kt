package com.quata.feature.official.presentation

import com.quata.core.language.QuataTranslationLanguage
import com.quata.core.language.QuataTranslationResult
import com.quata.core.language.TextTranslator
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostLanguage
import com.quata.feature.official.domain.OfficialPostType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OfficialPostEditorFangTranslatorTest {
    @Test
    fun translatesDraftFieldsAndPreservesHtmlBlocks() = runTest {
        val translator = OfficialPostEditorFangTranslator(RecordingTranslator())
        val draft = OfficialPostDraft(
            title = "Alerta",
            summary = "Corte de calle",
            contentHtml = "<h1 data-id=\"1\">Alerta</h1><p>Corte de calle</p>",
            readMoreLabel = "details",
            language = OfficialPostLanguage.Spanish,
            type = OfficialPostType.Urgent,
            linkUrl = "https://quata.test/alerta",
        )

        val translated = translator.translate(
            draft = draft,
            source = OfficialPostLanguage.Spanish,
            target = OfficialPostLanguage.English,
            groupId = "group-1",
        )

        assertEquals("Alerta[ES->EN]", translated.title)
        assertEquals("Corte de calle[ES->EN]", translated.summary)
        assertEquals(
            "<h1 data-id=\"1\">Alerta[ES-&gt;EN]</h1><p>Corte de calle[ES-&gt;EN]</p>",
            translated.contentHtml,
        )
        assertEquals(OfficialPostLanguage.English, translated.language)
        assertEquals("group-1", translated.translationGroupId)
        assertEquals("details", translated.readMoreLabel)
        assertEquals("https://quata.test/alerta", translated.linkUrl)
    }

    @Test
    fun blankFieldsStayBlankAndPlainHtmlFallsBackToParagraph() = runTest {
        val translator = OfficialPostEditorFangTranslator(RecordingTranslator())
        val draft = OfficialPostDraft(
            title = "",
            summary = "",
            contentHtml = "Texto suelto",
            language = OfficialPostLanguage.Spanish,
            type = OfficialPostType.Announcement,
        )

        val translated = translator.translate(draft, OfficialPostLanguage.Spanish, OfficialPostLanguage.French, "group-2")

        assertEquals("", translated.title)
        assertEquals("", translated.summary)
        assertEquals("<p>Texto suelto[ES-&gt;FR]</p>", translated.contentHtml)
    }

    private class RecordingTranslator : TextTranslator {
        override suspend fun translate(
            text: String,
            sourceLanguage: QuataTranslationLanguage,
            targetLanguage: QuataTranslationLanguage,
        ): QuataTranslationResult = QuataTranslationResult(
            translation = "$text[${sourceLanguage.short()}->${targetLanguage.short()}]",
            pivotUsed = false,
            route = listOf(sourceLanguage, targetLanguage),
            pivotLanguage = null,
            pivotText = null,
            pivotEngine = null,
        )
    }
}

private fun QuataTranslationLanguage.short(): String = when (this) {
    QuataTranslationLanguage.Fang -> "FAN"
    QuataTranslationLanguage.Spanish -> "ES"
    QuataTranslationLanguage.English -> "EN"
    QuataTranslationLanguage.French -> "FR"
}
