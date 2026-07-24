package com.quata.core.navigation

/**
 * Provider-neutral notification data used by FCM, APNs and Web Push adapters.
 * Platform receivers translate their payload into this type before navigating.
 */
data class QuataNotificationLaunchPayload(
    val deepLink: String? = null,
    val conversationId: String? = null,
    val threadId: String? = null,
    val messageId: String? = null,
)

/** Normalizes the field names emitted by the current Android/Web notification backends. */
fun Map<String, String?>.toQuataNotificationLaunchPayload(): QuataNotificationLaunchPayload =
    QuataNotificationLaunchPayload(
        deepLink = valueFor("deep_link", "deeplink", "url"),
        conversationId = valueFor("conversation_id", "conversationId"),
        threadId = valueFor("thread_id", "threadId"),
        messageId = valueFor("message_id", "messageId"),
    )

/** Resolves a chat target without coupling receivers to FCM, APNs or browser payload classes. */
fun QuataNotificationLaunchPayload.quataChatDeepLinkOrNull(): QuataChatDeepLink? {
    deepLink?.trim()?.takeIf { it.isNotEmpty() }?.quataChatDeepLinkOrNull()?.let { return it }
    val conversation = conversationId.normalizedNotificationValue()
        ?: threadId.normalizedNotificationValue()?.let { "sb:$it" }
        ?: return null
    return QuataChatDeepLink(
        conversationId = conversation,
        messageId = messageId.normalizedNotificationValue(),
    )
}

fun Map<String, String?>.quataNotificationChatDeepLinkOrNull(): QuataChatDeepLink? =
    toQuataNotificationLaunchPayload().quataChatDeepLinkOrNull()

private fun Map<String, String?>.valueFor(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { this[it].normalizedNotificationValue() }

private fun String?.normalizedNotificationValue(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
