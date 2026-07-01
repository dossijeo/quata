package com.quata.core.text

import androidx.core.text.HtmlCompat

fun String.decodeHtmlEntities(): String {
    val value = this
    if ('&' !in value) return value
    return value.fromHtmlCompat().toString()
}

fun String.stripHtmlTagsAndDecode(): String =
    replace(Regex("<[^>]*>"), "")
        .decodeHtmlEntities()
        .trim()

private fun String.fromHtmlCompat(): CharSequence =
    HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_LEGACY)
