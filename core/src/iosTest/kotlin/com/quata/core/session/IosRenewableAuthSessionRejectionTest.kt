package com.quata.core.session

import com.quata.core.model.AuthSession
import com.quata.core.model.currentEpochSeconds
import com.quata.core.preferences.SessionStorage
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
