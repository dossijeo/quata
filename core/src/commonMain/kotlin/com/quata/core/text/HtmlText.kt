package com.quata.core.text

private val htmlTagRegex = Regex("<[^>]*>")
private val htmlBreakRegex = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val decimalEntityRegex = Regex("&#(\\d+);")
private val hexadecimalEntityRegex = Regex("&#x([0-9a-fA-F]+);")

/** Platform-neutral HTML-to-plain-text normalization used by feed and editor mappers. */
fun String.decodeHtmlEntities(): String {
    if (isEmpty()) return this
    val withoutTags = replace(htmlBreakRegex, "\n").replace(htmlTagRegex, "")
    return withoutTags
        .replace(hexadecimalEntityRegex) { match -> match.groupValues[1].toIntOrNull(16)?.toCodePointString().orEmpty() }
        .replace(decimalEntityRegex) { match -> match.groupValues[1].toIntOrNull()?.toCodePointString().orEmpty() }
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
}

fun String.stripHtmlTagsAndDecode(): String = replace(htmlTagRegex, "").decodeHtmlEntities().trim()

private fun Int.toCodePointString(): String =
    if (this in 0..0xffff) this.toChar().toString() else ""
