package com.quata.feature.auth.data

import com.quata.core.model.AuthSession
import com.quata.core.preferences.SessionStorage
import com.quata.core.session.IosRenewableAuthSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosAuthLogoutOrderingTest {
    @Test
    fun incorrectLifecyclePasswordKeepsTheKeychainSessionAndStableErrorCode() = runTest {
        val storage = MemorySessionStorage()
        val session = renewableSession(storage)
        session.save(authSession())
        val repository = repository(session, object : IosAuthHttpTransport {
            override suspend fun post(endpoint: String, headers: Map<String, String>, body: String) =
                IosAuthHttpResponse(403, "{\"error\":\"invalid_password\"}")

            override suspend fun get(endpoint: String, headers: Map<String, String>): IosAuthHttpResponse =
                error("lifecycle_must_not_get")
        })

        val result = repository.deactivateAccount("incorrect-password")

        assertTrue(result.isFailure)
        assertEquals("ios_auth_invalid_password", result.exceptionOrNull()?.message)
        assertNotNull(session.restoredSession())
    }

    @Test
    fun lifecycleTransportFailureKeepsTheKeychainSessionForRetry() = runTest {
        val storage = MemorySessionStorage()
        val session = renewableSession(storage)
        session.save(authSession())
        val repository = repository(session, object : IosAuthHttpTransport {
            override suspend fun post(endpoint: String, headers: Map<String, String>, body: String): IosAuthHttpResponse =
                error("transport_offline")

            override suspend fun get(endpoint: String, headers: Map<String, String>): IosAuthHttpResponse =
                error("lifecycle_must_not_get")
        })

        val result = repository.deleteAccountData("valid-but-offline-password")

        assertTrue(result.isFailure)
        assertEquals("transport_offline", result.exceptionOrNull()?.message)
        assertNotNull(session.restoredSession())
    }

    @Test
    fun remoteLogoutSettlesBeforeKeychainSessionIsCleared() = runTest {
        val storage = MemorySessionStorage()
        val session = renewableSession(storage)
        session.save(authSession())
        val remoteStarted = CompletableDeferred<Unit>()
        val releaseRemote = CompletableDeferred<Unit>()
        val repository = repository(session, object : IosAuthHttpTransport {
            override suspend fun post(endpoint: String, headers: Map<String, String>, body: String): IosAuthHttpResponse {
                remoteStarted.complete(Unit)
                releaseRemote.await()
                return IosAuthHttpResponse(204, "")
            }

            override suspend fun get(endpoint: String, headers: Map<String, String>): IosAuthHttpResponse =
                error("logout_must_not_get")
        })

        val logout = async { repository.logout() }
        remoteStarted.await()

        assertFalse(logout.isCompleted)
        assertNotNull(session.restoredSession())
        releaseRemote.complete(Unit)
        logout.await()
        assertNull(session.restoredSession())
    }

    @Test
    fun failedRemoteLogoutStillClearsTheLocalKeychainSessionAfterTheAttempt() = runTest {
        val storage = MemorySessionStorage()
        val session = renewableSession(storage)
        session.save(authSession())
        var remoteCalls = 0
        val repository = repository(session, object : IosAuthHttpTransport {
            override suspend fun post(endpoint: String, headers: Map<String, String>, body: String): IosAuthHttpResponse {
                remoteCalls += 1
                return IosAuthHttpResponse(503, "{\"code\":\"offline\"}")
            }

            override suspend fun get(endpoint: String, headers: Map<String, String>): IosAuthHttpResponse =
                error("logout_must_not_get")
        })

        repository.logout()

        kotlin.test.assertEquals(1, remoteCalls)
        assertNull(session.restoredSession())
    }

    @Test
    fun transportExceptionStillClearsTheLocalKeychainSessionAfterTheAttempt() = runTest {
        val storage = MemorySessionStorage()
        val session = renewableSession(storage)
        session.save(authSession())
        var remoteCalls = 0
        val repository = repository(session, object : IosAuthHttpTransport {
            override suspend fun post(endpoint: String, headers: Map<String, String>, body: String): IosAuthHttpResponse {
                remoteCalls += 1
                error("transport_offline")
            }

            override suspend fun get(endpoint: String, headers: Map<String, String>): IosAuthHttpResponse =
                error("logout_must_not_get")
        })

        repository.logout()

        kotlin.test.assertEquals(1, remoteCalls)
        assertNull(session.restoredSession())
    }

    @Test
    fun cancellationPropagatesAfterTheLocalKeychainSessionIsCleared() = runTest {
        val storage = MemorySessionStorage()
        val session = renewableSession(storage)
        session.save(authSession())
        val remoteStarted = CompletableDeferred<Unit>()
        val releaseRemote = CompletableDeferred<Unit>()
        val repository = repository(session, object : IosAuthHttpTransport {
            override suspend fun post(endpoint: String, headers: Map<String, String>, body: String): IosAuthHttpResponse {
                remoteStarted.complete(Unit)
                releaseRemote.await()
                return IosAuthHttpResponse(204, "")
            }

            override suspend fun get(endpoint: String, headers: Map<String, String>): IosAuthHttpResponse =
                error("logout_must_not_get")
        })

        val logout = launch { repository.logout() }
        remoteStarted.await()
        logout.cancelAndJoin()

        assertTrue(logout.isCancelled)
        assertNull(session.restoredSession())
    }

    private fun repository(session: IosRenewableAuthSession, transport: IosAuthHttpTransport) = IosAuthRepository(
        configuration = IosAuthRuntimeConfiguration(
            supabaseUrl = "https://example.supabase.co",
            supabasePublishableKey = "public-test-key",
        ),
        session = session,
        transport = transport,
    )

    private fun renewableSession(storage: SessionStorage) = IosRenewableAuthSession(
        refresher = { null },
        storage = storage,
    )

    private fun authSession() = AuthSession(
        token = "access-token",
        accessToken = "access-token",
        refreshToken = "refresh-token",
        userId = "profile-id",
        authUserId = "auth-id",
        email = "",
        displayName = "QADATA",
        expiresAt = Long.MAX_VALUE,
    )

    private class MemorySessionStorage : SessionStorage {
        private var value: AuthSession? = null
        override fun saveSession(session: AuthSession) { value = session }
        override fun getSession(): AuthSession? = value
        override fun clear() { value = null }
    }
}
