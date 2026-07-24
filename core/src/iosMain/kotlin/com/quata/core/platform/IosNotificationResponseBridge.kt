package com.quata.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UserNotifications.UNNotificationResponse

/**
 * UIKit boundary for an already-delivered notification response. It intentionally does not own
 * APNs registration, token lifecycle or a `UNUserNotificationCenter` delegate; those remain in
 * the Swift application host. A navigation host must be attached before a parsed target is opened.
 */
@OptIn(ExperimentalForeignApi::class)
class IosNotificationResponseBridge(
    private val adapter: IosNotificationDeepLinkAdapter = IosNotificationDeepLinkAdapter(),
) {
    fun attachHost(host: IosNotificationDeepLinkHost) {
        adapter.attachHost(host)
    }

    fun detachHost(host: IosNotificationDeepLinkHost) {
        adapter.detachHost(host)
    }

    /** Normalizes `response.notification.request.content.userInfo` through the shared deep-link parser. */
    fun handle(response: UNNotificationResponse): PlatformResult<Unit> =
        adapter.handleApnsTap(response.notification.request.content.userInfo)
}
