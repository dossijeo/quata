package com.quata.core.moderation

import com.quata.core.platform.PreferenceStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UgcTermsGateContractTest {
    @Test
    fun localAcceptancePreservesAccessWhenRemoteFlushFails() = runTest {
        val preferences = MemoryPreferenceStore()
        var syncAttempts = 0
        val gateway = LocalFirstUgcTermsGateway(
            profileIdProvider = { "profile-1" },
            store = PreferenceUgcTermsAcceptanceStore(preferences),
            remote = UgcTermsRemoteGateway { _, _ -> Result.success(false) },
            acceptance = UgcTermsAcceptanceGateway {
                _, _ ->
                syncAttempts += 1
                Result.failure(IllegalStateException("offline"))
            },
        )

        assertTrue(gateway.acceptTerms().isSuccess)
        assertEquals(0, syncAttempts)

        assertEquals(true, gateway.hasAcceptedTerms().getOrThrow())
        assertEquals(0, syncAttempts)
        assertEquals(false, gateway.flushPendingAcceptance().isSuccess)
        assertEquals(1, syncAttempts)
        assertEquals("true", preferences.getString("ugc_terms:pending:profile-1:$CurrentUgcTermsVersion"))
    }

    @Test
    fun pendingLocalAcceptanceIsRetriedAndClearedWhenRemoteRecovers() = runTest {
        val preferences = MemoryPreferenceStore()
        var syncAttempts = 0
        val gateway = LocalFirstUgcTermsGateway(
            profileIdProvider = { "profile-1" },
            store = PreferenceUgcTermsAcceptanceStore(preferences),
            remote = UgcTermsRemoteGateway { _, _ -> Result.success(false) },
            acceptance = UgcTermsAcceptanceGateway {
                _, _ ->
                syncAttempts += 1
                if (syncAttempts == 1) {
                    Result.failure(IllegalStateException("offline"))
                } else {
                    Result.success(Unit)
                }
            },
        )

        assertTrue(gateway.acceptTerms().isSuccess)
        assertEquals("true", preferences.getString("ugc_terms:pending:profile-1:$CurrentUgcTermsVersion"))
        assertEquals(0, syncAttempts)

        assertEquals(true, gateway.hasAcceptedTerms().getOrThrow())
        assertEquals(0, syncAttempts)
        assertEquals(false, gateway.flushPendingAcceptance().isSuccess)
        assertEquals(1, syncAttempts)
        assertEquals("true", preferences.getString("ugc_terms:pending:profile-1:$CurrentUgcTermsVersion"))
        assertEquals(true, gateway.flushPendingAcceptance().getOrThrow())
        assertEquals(2, syncAttempts)
        assertEquals(null, preferences.getString("ugc_terms:pending:profile-1:$CurrentUgcTermsVersion"))
    }

    @Test
    fun remoteAcceptanceIsCachedAsSynced() = runTest {
        val preferences = MemoryPreferenceStore()
        val gateway = LocalFirstUgcTermsGateway(
            profileIdProvider = { "profile-1" },
            store = PreferenceUgcTermsAcceptanceStore(preferences),
            remote = UgcTermsRemoteGateway { _, _ -> Result.success(true) },
            acceptance = UgcTermsAcceptanceGateway { _, _ -> Result.success(Unit) },
        )

        assertEquals(true, gateway.hasAcceptedTerms().getOrThrow())

        assertEquals("true", preferences.getString("ugc_terms:accepted:profile-1:$CurrentUgcTermsVersion"))
        assertEquals(null, preferences.getString("ugc_terms:pending:profile-1:$CurrentUgcTermsVersion"))
    }

    @Test
    fun localAcceptanceCanLaunchBackgroundPendingSyncWithoutBlockingAccept() = runTest {
        val preferences = MemoryPreferenceStore()
        val pendingTasks = mutableListOf<suspend () -> Unit>()
        var syncAttempts = 0
        val gateway = LocalFirstUgcTermsGateway(
            profileIdProvider = { "profile-1" },
            store = PreferenceUgcTermsAcceptanceStore(preferences),
            remote = UgcTermsRemoteGateway { _, _ -> Result.success(false) },
            acceptance = UgcTermsAcceptanceGateway {
                _, _ ->
                syncAttempts += 1
                Result.success(Unit)
            },
            pendingSyncLauncher = { task -> pendingTasks += task },
        )

        assertTrue(gateway.acceptTerms().isSuccess)
        assertEquals(0, syncAttempts)
        assertEquals(1, pendingTasks.size)

        pendingTasks.single().invoke()

        assertEquals(1, syncAttempts)
        assertEquals(null, preferences.getString("ugc_terms:pending:profile-1:$CurrentUgcTermsVersion"))
    }

    @Test
    fun missingSessionFailsClosed() = runTest {
        val gateway = LocalFirstUgcTermsGateway(
            profileIdProvider = { null },
            store = PreferenceUgcTermsAcceptanceStore(MemoryPreferenceStore()),
            remote = UgcTermsRemoteGateway { _, _ -> Result.success(true) },
            acceptance = UgcTermsAcceptanceGateway { _, _ -> Result.success(Unit) },
        )

        assertTrue(gateway.hasAcceptedTerms().isFailure)
        assertTrue(gateway.acceptTerms().isFailure)
    }
}

private class MemoryPreferenceStore : PreferenceStore {
    private val values = mutableMapOf<String, String>()

    override suspend fun getString(key: String): String? = values[key]

    override suspend fun putString(key: String, value: String) {
        values[key] = value
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }
}
