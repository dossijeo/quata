package com.quata.core.session

import com.quata.core.model.AuthSession
import com.quata.core.model.currentEpochSeconds
import com.quata.core.preferences.SessionStorage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionManagerPublicationRaceTest {
    @Test
    fun logoutDuringRefreshPublicationWinsAfterTheAtomicCommit() = runBlocking {
        val refreshedSaveStarted = CountDownLatch(1)
        val releaseRefreshedSave = CountDownLatch(1)
        val storage = BlockingSessionStorage(expiredSession(), refreshedSaveStarted, releaseRefreshedSave)
        val manager = SessionManager(storage)

        val refresh = async(Dispatchers.Default) {
            manager.ensureFreshSession(force = true) { freshSession("late-refresh-token") }
        }
        assertTrue(refreshedSaveStarted.await(5, TimeUnit.SECONDS))

        val logout = async(Dispatchers.Default) { manager.clearSession() }
        assertFalse(logout.isCompleted)
        releaseRefreshedSave.countDown()

        refresh.await()
        logout.await()
        assertNull(storage.getSession())
        assertEquals(AuthState.LoggedOut, manager.authState.value)
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

    private class BlockingSessionStorage(
        initial: AuthSession?,
        private val refreshedSaveStarted: CountDownLatch,
        private val releaseRefreshedSave: CountDownLatch,
    ) : SessionStorage {
        @Volatile
        private var storedSession = initial

        override fun saveSession(session: AuthSession) {
            if (session.token == "late-refresh-token") {
                refreshedSaveStarted.countDown()
                check(releaseRefreshedSave.await(5, TimeUnit.SECONDS))
            }
            storedSession = session
        }

        override fun getSession(): AuthSession? = storedSession

        override fun clear() {
            storedSession = null
        }
    }
}
