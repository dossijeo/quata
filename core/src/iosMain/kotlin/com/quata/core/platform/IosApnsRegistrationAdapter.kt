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

/** Receives a normalized APNs registration failure code; error reporting remains host-owned. */
fun interface IosApnsRegistrationFailureHost {
    fun onApnsRegistrationFailure(code: String)
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

    @Volatile
    private var failureHost: IosApnsRegistrationFailureHost? = null

    fun attachRegistrationHost(host: IosApnsRegistrationHost) {
        registrationHost = host
    }

    fun attachTokenHost(host: IosApnsTokenHost) {
        tokenHost = host
    }

    fun attachFailureHost(host: IosApnsRegistrationFailureHost) {
        failureHost = host
    }

    fun detachRegistrationHost(host: IosApnsRegistrationHost) {
        if (registrationHost === host) registrationHost = null
    }

    fun detachTokenHost(host: IosApnsTokenHost) {
        if (tokenHost === host) tokenHost = null
    }

    fun detachFailureHost(host: IosApnsRegistrationFailureHost) {
        if (failureHost === host) failureHost = null
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

    /**
     * Called from application(_:didFailToRegisterForRemoteNotificationsWithError:).
     *
     * The UIKit host supplies a stable public code rather than a provider response, token or
     * localized system error. Without an explicitly attached observer the failure is
     * unsupported, which prevents the bridge from pretending that telemetry or delivery exists.
     */
    fun handleRegistrationFailure(code: String): PlatformResult<Unit> {
        val normalized = code.trim().lowercase()
        if (normalized.isEmpty() || normalized.length > 120 || normalized.any { !it.isLetterOrDigit() && it !in "._-" }) {
            return PlatformResult.Failure("apns_registration_failure_code_invalid")
        }
        val activeHost = failureHost ?: return PlatformResult.Unsupported
        return runCatching {
            activeHost.onApnsRegistrationFailure(normalized)
            PlatformResult.Success(Unit)
        }.getOrElse { PlatformResult.Failure(it.message) }
    }
}

private const val HexDigits = "0123456789abcdef"
