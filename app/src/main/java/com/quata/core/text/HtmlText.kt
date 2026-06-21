package com.quata.core.text

import android.os.Build
import android.text.Html

fun String.decodeHtmlEntities(): String {
    val value = this
    if ('&' !in value) return value
    return value.fromHtmlCompat().toString()
}

fun String.stripHtmlTagsAndDecode(): String =
    replace(Regex("<[^>]*>"), "")
        .decodeHtmlEntities()
        .trim()

@Suppress("DEPRECATION")
private fun String.fromHtmlCompat(): CharSequence =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY)
    } else {
        Html.fromHtml(this)
    }
