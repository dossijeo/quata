package com.quata.core.platform

import com.quata.core.navigation.QuataChatDeepLink
import com.quata.core.navigation.quataNotificationChatDeepLinkOrNull

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
}
