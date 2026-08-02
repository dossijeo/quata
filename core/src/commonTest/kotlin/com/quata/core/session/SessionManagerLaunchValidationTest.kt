package com.quata.core.session

import com.quata.core.model.AuthSession
import com.quata.core.model.currentEpochSeconds
import com.quata.core.preferences.SessionStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionManagerLaunchValidationTest {
    @Test
    fun freshSessionIsAcceptedWithoutRefreshing() = runTest {
        val storage = MemorySessionStorage(freshSession())
        val manager = SessionManager(storage)

        val accepted = manager.validateFreshSession { error("fresh session must not refresh") }

        assertEquals(storage.storedSession, accepted)
    }

    @Test
    fun expiredSessionIsAcceptedOnlyAfterSuccessfulRefresh() = runTest {
        val storage = MemorySessionStorage(expiredSession())
        val manager = SessionManager(storage)
        val refreshed = freshSession(token = "refreshed-token")

        val accepted = manager.validateFreshSession { refreshed }

        assertEquals(refreshed, accepted)
        assertEquals(refreshed, storage.storedSession)
    }

    @Test
    fun failedExpiredRefreshKeepsStoredCredentialsButRejectsAuthenticatedLaunch() = runTest {
        val expired = expiredSession()
        val storage = MemorySessionStorage(expired)
        val manager = SessionManager(storage)

        val accepted = manager.validateFreshSession { null }

        assertNull(accepted)
        assertEquals(expired, storage.storedSession)
    }

    @Test
    fun staleRefreshResponseKeepsOriginalCredentialsAndRejectsAuthenticatedLaunch() = runTest {
        val expired = expiredSession()
        val storage = MemorySessionStorage(expired)
        val manager = SessionManager(storage)
        val stillStale = freshSession(token = "still-stale-token").copy(
            expiresAt = currentEpochSeconds() + 60,
        )

        val accepted = manager.validateFreshSession { stillStale }

        assertNull(accepted)
        assertEquals(expired, storage.storedSession)
    }

    private fun freshSession(token: String = "fresh-token") = AuthSession(
        token = token,
        accessToken = token,
        refreshToken = "refresh-token",
        expiresAt = currentEpochSeconds() + 3_600,
        userId = "member-7",
        email = "member@quata.test",
        displayName = "Member",
    )

    private fun expiredSession() = freshSession().copy(expiresAt = currentEpochSeconds() - 1)

    private class MemorySessionStorage(initial: AuthSession?) : SessionStorage {
        var storedSession = initial
        override fun saveSession(session: AuthSession) { storedSession = session }
        override fun getSession(): AuthSession? = storedSession
        override fun clear() { storedSession = null }
    }
}
