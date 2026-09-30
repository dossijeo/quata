package com.quata.core.session

import com.quata.core.model.AuthSession
import com.quata.core.model.currentEpochSeconds
import com.quata.core.preferences.SessionStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosRenewableAuthSessionRejectionTest {
    @Test
    fun terminalRefreshRejectionClearsTheExactPersistedSession() = runTest {
        val expired = session("expired").copy(expiresAt = currentEpochSeconds() - 1)
        val storage = MemoryStorage(expired)
        val renewable = IosRenewableAuthSession(
            IosAuthSessionRefresher { throw IosAuthSessionRejectedException(it) },
            storage,
        )

        assertNull(renewable.currentSession())
        assertNull(storage.value)
        assertEquals(AuthState.LoggedOut, renewable.authState.value)
    }

    @Test
    fun transientRefreshFailureRetainsTheSessionForRetry() = runTest {
        val expired = session("expired").copy(expiresAt = currentEpochSeconds() - 1)
        val storage = MemoryStorage(expired)
        val renewable = IosRenewableAuthSession(IosAuthSessionRefresher { null }, storage)

        assertEquals(expired, renewable.currentSession())
        assertEquals(expired, storage.value)
        assertEquals(AuthState.LoggedIn(expired.userId, expired.displayName), renewable.authState.value)
    }

    @Test
    fun lateTerminalRejectionReturnsTheReplacementSessionWithoutClearingIt() = runTest {
        val rejected = session("rejected").copy(expiresAt = currentEpochSeconds() - 1)
        val replacement = session("replacement")
        val refreshStarted = CompletableDeferred<Unit>()
        val releaseRejection = CompletableDeferred<Unit>()
        val storage = MemoryStorage(rejected)
        val renewable = IosRenewableAuthSession(
            IosAuthSessionRefresher {
                refreshStarted.complete(Unit)
                releaseRejection.await()
                throw IosAuthSessionRejectedException(it)
            },
            storage,
        )

        val result = async { renewable.currentSession() }
        refreshStarted.await()
        renewable.save(replacement)
        releaseRejection.complete(Unit)

        assertEquals(replacement, result.await())
        assertEquals(replacement, storage.value)
        assertEquals(AuthState.LoggedIn(replacement.userId, replacement.displayName), renewable.authState.value)
    }

    private fun session(token: String) = AuthSession(
        token = token,
        accessToken = token,
        refreshToken = "refresh-token",
        expiresAt = currentEpochSeconds() + 3_600,
        userId = "member-7",
        email = "member@quata.test",
        displayName = "Member",
    )

    private class MemoryStorage(initial: AuthSession?) : SessionStorage {
        var value = initial
        override fun saveSession(session: AuthSession) { value = session }
        override fun getSession(): AuthSession? = value
        override fun clear() { value = null }
    }
}
