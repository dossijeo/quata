package com.quata.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Native-only contract coverage for the APNs bridge. These tests deliberately use plain Kotlin
 * hosts: they must not contact UIApplication, APNs, a provider, or the Quata backend.
 */
class IosApnsRegistrationAdapterTest {
    @Test
    fun deviceTokenIsNormalizedBeforeItReachesTheAttachedHost() {
        val adapter = IosApnsRegistrationAdapter()
        val host = RecordingTokenHost()
        adapter.attachTokenHost(host)

        val result = adapter.handleDeviceToken("<A1 b2-C3:d4>")

        assertIs<PlatformResult.Success<Unit>>(result)
        assertEquals(listOf("a1b2c3d4"), host.tokens)
    }

    @Test
    fun invalidDeviceTokensFailClosedWithoutCallingTheHost() {
        val adapter = IosApnsRegistrationAdapter()
        val host = RecordingTokenHost()
        adapter.attachTokenHost(host)

        assertEquals(
            "apns_device_token_invalid",
            assertIs<PlatformResult.Failure>(adapter.handleDeviceToken("<A1B>")).reason,
        )
        assertEquals(
            "apns_device_token_invalid",
            assertIs<PlatformResult.Failure>(adapter.handleDeviceToken("<A1B2-G3D4>")).reason,
        )
        assertEquals(emptyList(), host.tokens)
    }

    @Test
    fun absentHostsAreUnsupportedInsteadOfPretendingRegistrationSucceeded() {
        val adapter = IosApnsRegistrationAdapter()

        assertIs<PlatformResult.Unsupported>(adapter.requestRegistration())
        assertIs<PlatformResult.Unsupported>(adapter.handleDeviceToken("a1b2"))
        assertIs<PlatformResult.Unsupported>(adapter.handleRegistrationFailure("not_authorized"))
    }

    @Test
    fun registrationHostDetachesOnlyWhenTheSameInstanceIsProvided() {
        val adapter = IosApnsRegistrationAdapter()
        val attached = RecordingRegistrationHost()
        val other = RecordingRegistrationHost()
        adapter.attachRegistrationHost(attached)

        adapter.detachRegistrationHost(other)
        assertIs<PlatformResult.Success<Unit>>(adapter.requestRegistration())
        assertEquals(1, attached.calls)

        adapter.detachRegistrationHost(attached)
        assertIs<PlatformResult.Unsupported>(adapter.requestRegistration())
    }

    @Test
    fun tokenAndFailureHostsDetachByIdentityAndPreserveNormalizedValues() {
        val adapter = IosApnsRegistrationAdapter()
        val tokenHost = RecordingTokenHost()
        val otherTokenHost = RecordingTokenHost()
        val failureHost = RecordingFailureHost()
        val otherFailureHost = RecordingFailureHost()
        adapter.attachTokenHost(tokenHost)
        adapter.attachFailureHost(failureHost)

        adapter.detachTokenHost(otherTokenHost)
        adapter.detachFailureHost(otherFailureHost)
        assertIs<PlatformResult.Success<Unit>>(adapter.handleDeviceToken("A1B2"))
        assertIs<PlatformResult.Success<Unit>>(adapter.handleRegistrationFailure(" Not_Authorized "))
        assertEquals(listOf("a1b2"), tokenHost.tokens)
        assertEquals(listOf("not_authorized"), failureHost.codes)

        adapter.detachTokenHost(tokenHost)
        adapter.detachFailureHost(failureHost)
        assertIs<PlatformResult.Unsupported>(adapter.handleDeviceToken("a1b2"))
        assertIs<PlatformResult.Unsupported>(adapter.handleRegistrationFailure("not_authorized"))
    }

    @Test
    fun throwingHostsBecomeFailuresRatherThanEscapingOrReportingSuccess() {
        val registrationAdapter = IosApnsRegistrationAdapter().apply {
            attachRegistrationHost { error("registration_host_failure") }
        }
        val tokenAdapter = IosApnsRegistrationAdapter().apply {
            attachTokenHost { error("token_host_failure") }
        }
        val failureAdapter = IosApnsRegistrationAdapter().apply {
            attachFailureHost { error("failure_host_failure") }
        }

        assertEquals(
            "registration_host_failure",
            assertIs<PlatformResult.Failure>(registrationAdapter.requestRegistration()).reason,
        )
        assertEquals(
            "token_host_failure",
            assertIs<PlatformResult.Failure>(tokenAdapter.handleDeviceToken("a1b2")).reason,
        )
        assertEquals(
            "failure_host_failure",
            assertIs<PlatformResult.Failure>(failureAdapter.handleRegistrationFailure("not_authorized")).reason,
        )
    }

    private class RecordingRegistrationHost : IosApnsRegistrationHost {
        var calls = 0
        override fun registerForRemoteNotifications() { calls += 1 }
    }

    private class RecordingTokenHost : IosApnsTokenHost {
        val tokens = mutableListOf<String>()
        override fun onApnsToken(token: String) { tokens += token }
    }

    private class RecordingFailureHost : IosApnsRegistrationFailureHost {
        val codes = mutableListOf<String>()
        override fun onApnsRegistrationFailure(code: String) { codes += code }
    }
}
