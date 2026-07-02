package com.quata.core.navigation

import android.net.Uri

private const val QUATA_WEB_HOST = "egquata.com"
private const val QUATA_WEB_WWW_HOST = "www.egquata.com"
private const val POST_FRAGMENT_PREFIX = "post-"
private const val OFFICIAL_POST_FRAGMENT_PREFIX = "official-"
private const val CHAT_FRAGMENT_PREFIX = "chat-"
private const val RICH_TEXT_EDITOR_QA_FRAGMENT = "editor-qa"

fun quataPostUrl(postId: String): String = "https://$QUATA_WEB_HOST/#$POST_FRAGMENT_PREFIX$postId"

fun quataOfficialPostUrl(postId: String): String = "https://$QUATA_WEB_HOST/#$OFFICIAL_POST_FRAGMENT_PREFIX$postId"

fun quataChatUrl(conversationId: String): String =
    "https://$QUATA_WEB_HOST/#$CHAT_FRAGMENT_PREFIX${Uri.encode(conversationId)}"

fun Uri.quataFragmentOrNull(): String? {
    if (!isQuataWebLink()) return null
    return fragment
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

fun Uri.quataPostIdOrNull(): String? {
    return quataFragmentOrNull()
        ?.takeIf { it.startsWith(POST_FRAGMENT_PREFIX) }
        ?.removePrefix(POST_FRAGMENT_PREFIX)
        ?.takeIf { it.isNotBlank() }
}

fun Uri.quataOfficialPostIdOrNull(): String? {
    return quataFragmentOrNull()
        ?.takeIf { it.startsWith(OFFICIAL_POST_FRAGMENT_PREFIX) }
        ?.removePrefix(OFFICIAL_POST_FRAGMENT_PREFIX)
        ?.takeIf { it.isNotBlank() }
}

fun Uri.isQuataRichTextEditorQaLink(): Boolean =
    quataFragmentOrNull() == RICH_TEXT_EDITOR_QA_FRAGMENT

fun Uri.quataConversationIdOrNull(): String? {
    return quataFragmentOrNull()
        ?.takeIf { it.startsWith(CHAT_FRAGMENT_PREFIX) }
        ?.removePrefix(CHAT_FRAGMENT_PREFIX)
        ?.let(Uri::decode)
        ?.takeIf { it.isNotBlank() }
}

private fun Uri.isQuataWebLink(): Boolean =
    host.equals(QUATA_WEB_HOST, ignoreCase = true) ||
        host.equals(QUATA_WEB_WWW_HOST, ignoreCase = true)
