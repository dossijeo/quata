package com.quata.core.navigation

import android.net.Uri

private const val QUATA_WEB_HOST = "egquata.com"
private const val POST_FRAGMENT_PREFIX = "post-"
private const val CHAT_FRAGMENT_PREFIX = "chat-"

fun quataPostUrl(postId: String): String = "https://$QUATA_WEB_HOST/#$POST_FRAGMENT_PREFIX$postId"

fun quataChatUrl(conversationId: String): String =
    "https://$QUATA_WEB_HOST/#$CHAT_FRAGMENT_PREFIX${Uri.encode(conversationId)}"

fun Uri.quataPostIdOrNull(): String? {
    val hostMatches = host.equals(QUATA_WEB_HOST, ignoreCase = true)
    if (!hostMatches) return null
    return fragment
        ?.takeIf { it.startsWith(POST_FRAGMENT_PREFIX) }
        ?.removePrefix(POST_FRAGMENT_PREFIX)
        ?.takeIf { it.isNotBlank() }
}

fun Uri.quataConversationIdOrNull(): String? {
    val hostMatches = host.equals(QUATA_WEB_HOST, ignoreCase = true)
    if (!hostMatches) return null
    return fragment
        ?.takeIf { it.startsWith(CHAT_FRAGMENT_PREFIX) }
        ?.removePrefix(CHAT_FRAGMENT_PREFIX)
        ?.let(Uri::decode)
        ?.takeIf { it.isNotBlank() }
}
