package com.quata.core.platform

import com.quata.core.navigation.QuataChatDeepLink
import com.quata.core.navigation.quataNotificationChatDeepLinkOrNull
import kotlin.concurrent.Volatile
import platform.Foundation.NSNumber

/** UIKit/SwiftUI navigation boundary for an already-delivered APNs notification tap. */
fun interface IosNotificationDeepLinkHost {
    fun openChat(target: QuataChatDeepLink)
}

/**
 * Provider-agnostic iOS notification adapter. APNs registration, permission prompts and delegate
 * ownership remain in the Swift/UIKit host; this class only resolves the shared payload safely.
 */
class IosNotificationDeepLinkAdapter {
    @Volatile
    private var host: IosNotificationDeepLinkHost? = null

    fun attachHost(host: IosNotificationDeepLinkHost) {
        this.host = host
    }

    fun detachHost(host: IosNotificationDeepLinkHost) {
        if (this.host === host) this.host = null
    }

    fun handleTap(payload: Map<String, String?>): PlatformResult<Unit> {
        val target = payload.quataNotificationChatDeepLinkOrNull()
            ?: return PlatformResult.Failure("notification_chat_target_missing")
        val activeHost = host ?: return PlatformResult.Unsupported
        return runCatching {
            activeHost.openChat(target)
            PlatformResult.Success(Unit)
        }.getOrElse { PlatformResult.Failure(it.message) }
    }

    /**
     * Entry point for `UNNotificationResponse.notification.request.content.userInfo`.
     *
     * APNs/FCM bridges may expose identifiers as NSString, NSNumber or under a nested `data`,
     * `quata` or `payload` dictionary. Normalize only scalar values needed by the shared deep
     * link parser; alert/APS dictionaries are deliberately ignored.
     */
    fun handleApnsTap(userInfo: Map<*, *>): PlatformResult<Unit> =
        handleTap(userInfo.toQuataNotificationPayload())
}

private fun Map<*, *>.toQuataNotificationPayload(): Map<String, String?> {
    val nested = sequenceOf("data", "quata", "payload")
        .mapNotNull { key -> entries.firstOrNull { it.key?.toString() == key }?.value as? Map<*, *> }
        .firstOrNull()
        .orEmpty()
    return buildMap {
        putAll(nested.toNotificationStringValues())
        putAll(toNotificationStringValues())
    }
}

private fun Map<*, *>.toNotificationStringValues(): Map<String, String?> = buildMap {
    for ((rawKey, rawValue) in this@toNotificationStringValues) {
        val key = rawKey as? String ?: continue
        val value = when (rawValue) {
            null -> null
            is String -> rawValue
            is NSNumber -> rawValue.stringValue
            is Number, is Boolean -> rawValue.toString()
            else -> continue
        }
        put(key, value)
    }
}
