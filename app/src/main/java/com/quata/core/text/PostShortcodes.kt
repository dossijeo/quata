package com.quata.core.text

data class PostShortcodeContent(
    val cleanText: String,
    val documentText: String?
)

fun String.parsePostShortcodeContent(): PostShortcodeContent {
    val documentText = PostShortcodeRegex
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    val cleanText = replace(PostShortcodeRegex, "")
        .replace(Regex("[ \\t]{2,}"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
    return PostShortcodeContent(cleanText = cleanText, documentText = documentText)
}

fun String.withoutPostShortcodes(): String = parsePostShortcodeContent().cleanText

private val PostShortcodeRegex = Regex("""\[[A-Za-z0-9_]+:([^\]]+)]""")
