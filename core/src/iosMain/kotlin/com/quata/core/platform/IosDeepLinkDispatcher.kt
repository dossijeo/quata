package com.quata.core.platform

import com.quata.core.navigation.QuataDeepLinkTarget
import com.quata.core.navigation.quataDeepLinkTargetOrNull
import com.quata.core.navigation.quataNotificationDeepLinkTargetOrNull
import kotlin.concurrent.Volatile

/**
 * UIKit navigation boundary for a target already resolved by the portable deep-link contract.
 *
 * A Swift/UIKit composition root owns the actual route transition. Keeping it as an injected
 * host lets the iOS application defer a target until its authenticated feature host exists,
 * without making common Compose screens depend on UIKit or APNs.
 */
fun interface IosDeepLinkDestinationHost {
    fun open(target: QuataDeepLinkTarget)
}

/**
 * Translates public Quata URLs and provider-normalized notification payloads into common
 * [QuataDeepLinkTarget] values. It owns neither URL lifecycle callbacks nor APNs registration.
 */
class IosDeepLinkDispatcher {
    @Volatile
    private var host: IosDeepLinkDestinationHost? = null

    fun attachHost(host: IosDeepLinkDestinationHost) {
        this.host = host
    }

    fun detachHost(host: IosDeepLinkDestinationHost) {
        if (this.host === host) this.host = null
    }

    fun handleUrl(url: String): PlatformResult<Unit> =
        dispatch(url.quataDeepLinkTargetOrNull())

    /**
     * `payload` must already be normalized at the provider boundary; APNs dictionary traversal
     * remains in [IosNotificationDeepLinkAdapter].
     */
    fun handleNotificationPayload(payload: Map<String, String?>): PlatformResult<Unit> =
        dispatch(payload.quataNotificationDeepLinkTargetOrNull())

    private fun dispatch(target: QuataDeepLinkTarget?): PlatformResult<Unit> {
        target ?: return PlatformResult.Failure("deep_link_target_missing")
        val activeHost = host ?: return PlatformResult.Unsupported
        return runCatching {
            activeHost.open(target)
            PlatformResult.Success(Unit)
        }.getOrElse { PlatformResult.Failure(it.message) }
    }
}
