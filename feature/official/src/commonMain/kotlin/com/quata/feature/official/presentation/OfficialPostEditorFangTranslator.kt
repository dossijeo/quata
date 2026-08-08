package com.quata.feature.official.presentation

import com.quata.core.language.QuataTranslationLanguage
import com.quata.core.language.TextTranslator
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostLanguage

class OfficialPostEditorFangTranslator(
    private val translator: TextTranslator,
) : OfficialPostEditorTranslator {
    override suspend fun translate(
        draft: OfficialPostDraft,
        source: OfficialPostLanguage,
        target: OfficialPostLanguage,
        groupId: String,
    ): OfficialPostDraft {
        val sourceLanguage = source.toTranslationLanguage()
        val targetLanguage = target.toTranslationLanguage()
        return draft.copy(
            title = translateOfficialPlainText(draft.title, sourceLanguage, targetLanguage),
            summary = translateOfficialPlainText(draft.summary, sourceLanguage, targetLanguage),
            contentHtml = translateOfficialHtml(draft.contentHtml, sourceLanguage, targetLanguage),
            language = target,
            translationGroupId = groupId,
        )
    }

    private suspend fun translateOfficialHtml(
        html: String,
        source: QuataTranslationLanguage,
        target: QuataTranslationLanguage,
    ): String {
        val matches = officialHtmlBlockRegex.findAll(html).toList()
        if (matches.isEmpty()) {
            val plain = html.stripHtmlForOfficialEditor()
            if (plain.isBlank()) return ""
            return "<p>${translateOfficialPlainText(plain, source, target).escapePreviewHtml()}</p>"
        }

        val translated = StringBuilder()
        var cursor = 0
        matches.forEach { match ->
            translated.append(html.substring(cursor, match.range.first))
            val tag = match.groupValues[1]
            val attributes = match.groupValues.getOrNull(2).orEmpty()
            val inner = match.groupValues.getOrNull(3).orEmpty().stripHtmlForOfficialEditor()
            translated.append('<')
                .append(tag)
                .append(attributes)
                .append('>')
                .append(translateOfficialPlainText(inner, source, target).escapePreviewHtml())
                .append("</")
                .append(tag)
                .append('>')
            cursor = match.range.last + 1
        }
        translated.append(html.substring(cursor))
        return translated.toString()
    }

    private suspend fun translateOfficialPlainText(
        text: String,
        source: QuataTranslationLanguage,
        target: QuataTranslationLanguage,
    ): String {
        val normalized = text.trim()
        if (normalized.isBlank()) return ""
        if (source == target) return normalized
        return translator.translate(normalized, source, target).translation.ifBlank { normalized }
    }
}

private fun OfficialPostLanguage.toTranslationLanguage(): QuataTranslationLanguage = when (this) {
    OfficialPostLanguage.Spanish -> QuataTranslationLanguage.Spanish
    OfficialPostLanguage.English -> QuataTranslationLanguage.English
    OfficialPostLanguage.French -> QuataTranslationLanguage.French
}
