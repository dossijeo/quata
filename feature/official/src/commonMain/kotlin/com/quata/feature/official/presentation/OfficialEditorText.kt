package com.quata.feature.official.presentation

import com.quata.core.text.decodeHtmlEntities

fun String.escapePreviewHtml(): String = replace("&", "&amp;")
    .replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

fun String.extractOfficialEditorBlocks(): List<String> {
    val blocks = officialHtmlBlockRegex.findAll(this)
        .map { it.groupValues.getOrNull(3).orEmpty().stripHtmlForOfficialEditor().normalizeOfficialPlainText() }
        .filter { it.isNotBlank() }
        .toList()
    return blocks.ifEmpty { listOf(stripHtmlForOfficialEditor().normalizeOfficialPlainText()).filter { it.isNotBlank() } }
}

fun String.ellipsizeOfficialSummary(maxChars: Int): String {
    val normalized = normalizeOfficialPlainText()
    return if (normalized.length <= maxChars) normalized else normalized.take(maxChars).trimEnd('.', ',', ';', ':', ' ') + "..."
}

fun String.normalizeOfficialPlainText(): String = decodeHtmlEntities().replace(Regex("\\s+"), " ").trim()

fun String.stripHtmlForOfficialEditor(): String = replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ").trim()

val officialHtmlBlockRegex = Regex(
    "<(h[1-6]|p|blockquote|li)([^>]*)>([\\s\\S]*?)</\\1>",
    RegexOption.IGNORE_CASE
)
