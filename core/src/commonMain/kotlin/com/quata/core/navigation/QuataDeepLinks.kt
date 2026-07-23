package com.quata.core.navigation

private const val QuataWebHost = "egquata.com"
private const val QuataWebWwwHost = "www.egquata.com"
private const val PostFragmentPrefix = "post-"
private const val OfficialPostFragmentPrefix = "official-"
private const val ChatFragmentPrefix = "chat-"
private const val RichTextEditorQaFragment = "editor-qa"

fun quataPostUrl(postId: String): String = "https://$QuataWebHost/#$PostFragmentPrefix$postId"

fun quataOfficialPostUrl(postId: String): String = "https://$QuataWebHost/#$OfficialPostFragmentPrefix$postId"

fun quataChatUrl(conversationId: String, messageId: String? = null): String = buildString {
    append("https://$QuataWebHost/#$ChatFragmentPrefix")
    append(quataUrlEncode(conversationId))
    messageId?.takeIf { it.isNotBlank() }?.let {
        append("?message=")
        append(quataUrlEncode(it))
    }
}

data class QuataChatDeepLink(
    val conversationId: String,
    val messageId: String?,
)

fun String.quataPostIdOrNull(): String? = quataFragmentOrNull()
    ?.takeIf { it.startsWith(PostFragmentPrefix) }
    ?.removePrefix(PostFragmentPrefix)
    ?.takeIf { it.isNotBlank() }

fun String.quataOfficialPostIdOrNull(): String? = quataFragmentOrNull()
    ?.takeIf { it.startsWith(OfficialPostFragmentPrefix) }
    ?.removePrefix(OfficialPostFragmentPrefix)
    ?.takeIf { it.isNotBlank() }

fun String.isQuataRichTextEditorQaLink(): Boolean =
    quataFragmentOrNull() == RichTextEditorQaFragment

fun String.quataChatDeepLinkOrNull(): QuataChatDeepLink? {
    val payload = quataFragmentOrNull()
    ?.takeIf { it.startsWith(ChatFragmentPrefix) }
    ?.removePrefix(ChatFragmentPrefix)
        ?: return null
    val conversationId = quataUrlDecode(payload.substringBefore('?')).takeIf { it.isNotBlank() } ?: return null
    val messageId = payload.substringAfter('?', missingDelimiterValue = "")
        .split('&')
        .firstOrNull { it.substringBefore('=') == "message" }
        ?.substringAfter('=', missingDelimiterValue = "")
        ?.let(::quataUrlDecode)
        ?.takeIf { it.isNotBlank() }
    return QuataChatDeepLink(conversationId, messageId)
}

fun String.quataConversationIdOrNull(): String? = quataChatDeepLinkOrNull()?.conversationId

private fun String.quataFragmentOrNull(): String? {
    val schemeBoundary = indexOf("://")
    if (schemeBoundary <= 0) return null
    val hostStart = schemeBoundary + 3
    val hostEnd = indexOfAny(charArrayOf('/', '#', '?'), hostStart).let { if (it == -1) length else it }
    val host = substring(hostStart, hostEnd)
    if (!host.equals(QuataWebHost, ignoreCase = true) && !host.equals(QuataWebWwwHost, ignoreCase = true)) return null
    return substringAfter('#', missingDelimiterValue = "")
        .trim()
        .takeIf { it.isNotBlank() }
}

internal fun quataUrlEncode(value: String): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val code = byte.toInt() and 0xFF
        if ((code in 'a'.code..'z'.code) || (code in 'A'.code..'Z'.code) || (code in '0'.code..'9'.code) || code in intArrayOf('-'.code, '_'.code, '.'.code, '~'.code)) {
            append(code.toChar())
        } else {
            append('%')
            append("0123456789ABCDEF"[code shr 4])
            append("0123456789ABCDEF"[code and 0xF])
        }
    }
}

private fun quataUrlDecode(value: String): String = buildString {
    var index = 0
    while (index < value.length) {
        if (value[index] != '%') {
            append(value[index++])
            continue
        }
        val bytes = ArrayList<Byte>()
        while (index + 2 < value.length && value[index] == '%') {
            val high = value[index + 1].digitToIntOrNull(16) ?: break
            val low = value[index + 2].digitToIntOrNull(16) ?: break
            bytes += ((high shl 4) or low).toByte()
            index += 3
        }
        if (bytes.isEmpty()) append(value[index++]) else append(bytes.toByteArray().decodeToString())
    }
}
