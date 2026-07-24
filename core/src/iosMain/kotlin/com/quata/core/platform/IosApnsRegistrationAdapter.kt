package com.quata.core.platform

import kotlin.concurrent.Volatile

/** Implemented by the UIKit/SwiftUI application delegate that calls UIApplication APNs APIs. */
fun interface IosApnsRegistrationHost {
    fun registerForRemoteNotifications()
}

/** Receives a normalized APNs device token; upload/provider registration remains host-owned. */
fun interface IosApnsTokenHost {
    fun onApnsToken(token: String)
}

/**
 * Injectable bridge between the iOS application delegate and platform-neutral notification code.
 * It deliberately does not declare entitlements, invoke a push provider, or retain credentials.
 */
class IosApnsRegistrationAdapter {
    @Volatile
    private var registrationHost: IosApnsRegistrationHost? = null

    @Volatile
    private var tokenHost: IosApnsTokenHost? = null

    fun attachRegistrationHost(host: IosApnsRegistrationHost) {
        registrationHost = host
    }

    fun attachTokenHost(host: IosApnsTokenHost) {
        tokenHost = host
    }

    fun detachRegistrationHost(host: IosApnsRegistrationHost) {
        if (registrationHost === host) registrationHost = null
    }

    fun detachTokenHost(host: IosApnsTokenHost) {
        if (tokenHost === host) tokenHost = null
    }

    /** Called after notification permission is granted; the host calls UIApplication registration. */
    fun requestRegistration(): PlatformResult<Unit> {
        val activeHost = registrationHost ?: return PlatformResult.Unsupported
        return runCatching {
            activeHost.registerForRemoteNotifications()
            PlatformResult.Success(Unit)
        }.getOrElse { PlatformResult.Failure(it.message) }
    }

    /** Called from application(_:didRegisterForRemoteNotificationsWithDeviceToken:). */
    fun handleDeviceToken(token: String): PlatformResult<Unit> {
        val normalized = token.filter(Char::isLetterOrDigit).lowercase()
        if (normalized.isEmpty() || normalized.length % 2 != 0 || normalized.any { it !in HexDigits }) {
            return PlatformResult.Failure("apns_device_token_invalid")
        }
        val activeHost = tokenHost ?: return PlatformResult.Unsupported
        return runCatching {
            activeHost.onApnsToken(normalized)
            PlatformResult.Success(Unit)
        }.getOrElse { PlatformResult.Failure(it.message) }
    }
}

private const val HexDigits = "0123456789abcdef"
