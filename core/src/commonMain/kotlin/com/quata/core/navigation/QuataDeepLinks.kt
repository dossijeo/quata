package com.quata.core.navigation

private const val QuataWebHost = "egquata.com"
private const val QuataWebWwwHost = "www.egquata.com"
private const val QuataWebScheme = "https"
private const val QuataIosCustomScheme = "quata"
private const val PostFragmentPrefix = "post-"
private const val OfficialPostFragmentPrefix = "official-"
private const val ChatFragmentPrefix = "chat-"
private const val RichTextEditorQaFragment = "editor-qa"
private const val WhatsNewFragment = "whats-new"
private const val AboutFragment = "about"
private const val ReleaseHistoryFragment = "release-history"

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

fun quataWhatsNewUrl(): String = "https://$QuataWebHost/#$WhatsNewFragment"

fun quataAboutUrl(): String = "https://$QuataWebHost/#$AboutFragment"

fun quataReleaseHistoryUrl(): String = "https://$QuataWebHost/#$ReleaseHistoryFragment"

data class QuataChatDeepLink(
    val conversationId: String,
    val messageId: String?,
)

/**
 * Platform-neutral result of resolving one public Quata URL or notification launch payload.
 *
 * This deliberately carries the identifiers that a feature host needs instead of prescribing a
 * platform navigation implementation. Hosts can use [destination] for their primary route and
 * retain the target to focus the corresponding post or message when that feature is available.
 */
sealed interface QuataDeepLinkTarget {
    val destination: AppDestinations

    data class FeedPost(val postId: String) : QuataDeepLinkTarget {
        override val destination: AppDestinations = AppDestinations.Feed
    }

    data class OfficialPost(val postId: String) : QuataDeepLinkTarget {
        override val destination: AppDestinations = AppDestinations.Official
    }

    data class Chat(val target: QuataChatDeepLink) : QuataDeepLinkTarget {
        override val destination: AppDestinations = AppDestinations.Chat
    }

    data object RichTextEditorQa : QuataDeepLinkTarget {
        override val destination: AppDestinations = AppDestinations.RichTextEditorQa
    }

    data object WhatsNew : QuataDeepLinkTarget {
        override val destination: AppDestinations = AppDestinations.WhatsNew
    }

    data object About : QuataDeepLinkTarget {
        override val destination: AppDestinations = AppDestinations.About
    }

    data object ReleaseHistory : QuataDeepLinkTarget {
        override val destination: AppDestinations = AppDestinations.ReleaseHistory
    }
}

/**
 * Resolves public Quata routes already supported by the shared navigation contract.
 *
 * `https://egquata.com/#…` remains the web/share form. iOS app delivery is explicitly the
 * registered custom form `quata://egquata.com/#…`, not an HTTPS Universal Link. Both forms
 * intentionally resolve through this one parser so post, official and chat fragments cannot
 * drift between the platform boundary and public links.
 */
fun String.quataDeepLinkTargetOrNull(): QuataDeepLinkTarget? =
    quataChatDeepLinkOrNull()?.let(QuataDeepLinkTarget::Chat)
        ?: quataPostIdOrNull()?.let(QuataDeepLinkTarget::FeedPost)
        ?: quataOfficialPostIdOrNull()?.let(QuataDeepLinkTarget::OfficialPost)
        ?: takeIf { it.isQuataRichTextEditorQaLink() }?.let { QuataDeepLinkTarget.RichTextEditorQa }
        ?: takeIf { it.isQuataWhatsNewLink() }?.let { QuataDeepLinkTarget.WhatsNew }
        ?: takeIf { it.isQuataAboutLink() }?.let { QuataDeepLinkTarget.About }
        ?: takeIf { it.isQuataReleaseHistoryLink() }?.let { QuataDeepLinkTarget.ReleaseHistory }

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

fun String.isQuataWhatsNewLink(): Boolean = quataFragmentOrNull() == WhatsNewFragment

fun String.isQuataAboutLink(): Boolean = quataFragmentOrNull() == AboutFragment

fun String.isQuataReleaseHistoryLink(): Boolean = quataFragmentOrNull() == ReleaseHistoryFragment

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
    val scheme = substring(0, schemeBoundary)
    if (!scheme.equals(QuataWebScheme, ignoreCase = true) &&
        !scheme.equals(QuataIosCustomScheme, ignoreCase = true)
    ) return null
    val hostStart = schemeBoundary + 3
    val hostEnd = indexOfAny(charArrayOf('/', '#', '?'), hostStart).let { if (it == -1) length else it }
    val host = substring(hostStart, hostEnd)
    val acceptedHost = if (scheme.equals(QuataIosCustomScheme, ignoreCase = true)) {
        host.equals(QuataWebHost, ignoreCase = true)
    } else {
        host.equals(QuataWebHost, ignoreCase = true) || host.equals(QuataWebWwwHost, ignoreCase = true)
    }
    if (!acceptedHost) return null
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
