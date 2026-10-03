package com.quata.core.auth

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quata.feature.auth.domain.GoogleOAuthUserCancellation
import com.quata.core.model.AuthSession
import com.quata.core.preferences.SessionStorage
import com.quata.core.session.SessionManager
import com.quata.feature.auth.data.publishLinkedGoogleOAuthRecovery
import com.quata.shouldPublishGoogleOAuthRecoveryNavigation
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidGoogleOAuthPendingStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = AndroidGoogleOAuthPendingStore(context)

    @Before
    @After
    fun clearPendingRecord() {
        store.clear()
    }

    @Test
    fun pendingPkceStateSurvivesStoreRecreationAndIsEncryptedAtRest() {
        val verifier = "process-recreation-verifier"
        store.prepare(
            AndroidGoogleOAuthPending(
                mode = AndroidGoogleOAuthMode.LINK_IDENTITY,
                codeVerifier = verifier,
                expectedAuthUserId = "auth-1",
                expectedProfileId = "profile-1",
            ),
        )

        val encrypted = context.getSharedPreferences("quata_google_oauth_pending", Context.MODE_PRIVATE)
            .getString("pending_record", null)
        assertNotNull(encrypted)
        assertFalse(encrypted!!.contains(verifier))

        val restored = AndroidGoogleOAuthPendingStore(context).read()
        assertNotNull(restored)
        assertEquals(AndroidGoogleOAuthMode.LINK_IDENTITY, restored!!.mode)
        assertEquals(verifier, restored.codeVerifier)
        assertEquals(verifier, restored.callbackToken)
        assertEquals("auth-1", restored.expectedAuthUserId)
        assertEquals("profile-1", restored.expectedProfileId)
    }

    @Test
    fun callbackRecognitionDoesNotDependOnAnInMemoryContinuation() {
        assertEquals(
            true,
            AndroidGoogleOAuthCallbackCoordinator.isCallback(Uri.parse("quata://oauth/callback?code=returned")),
        )
        assertEquals(false, AndroidGoogleOAuthCallbackCoordinator.isCallback(Uri.parse("quata://post/1")))
    }

    @Test
    fun callbackMustCarryTheOpaqueTokenOfItsOriginatingAttempt() {
        val pending = AndroidGoogleOAuthPending(
            mode = AndroidGoogleOAuthMode.SIGN_IN,
            codeVerifier = "verifier",
            callbackToken = "attempt-b",
        )
        assertFalse(
            callbackMatchesPendingGoogleOAuth(
                Uri.parse("quata://oauth/callback?attempt=attempt-a&code=old"),
                pending,
            ),
        )
        assertEquals(
            true,
            callbackMatchesPendingGoogleOAuth(
                Uri.parse("quata://oauth/callback?attempt=attempt-b&code=current"),
                pending,
            ),
        )
    }

    @Test
    fun explicitCancellationClearsPendingStateButLifecycleCancellationPreservesIt() {
        assertEquals(true, shouldClearPendingGoogleOAuth(GoogleOAuthUserCancellation()))
        assertEquals(false, shouldClearPendingGoogleOAuth(CancellationException("activity_recreated")))
        assertEquals(true, shouldClearPendingGoogleOAuth(IllegalStateException("oauth_failed")))
    }

    @Test
    fun onlyTheMatchingAttemptCanConsumePersistedPkceState() {
        val first = AndroidGoogleOAuthPending(
            mode = AndroidGoogleOAuthMode.SIGN_IN,
            codeVerifier = "first-attempt",
        )
        store.prepare(first)

        assertFalse(store.consume(first.copy(codeVerifier = "superseding-attempt")))
        assertEquals(first, store.read())
        assertEquals(true, store.consume(first))
        assertEquals(null, store.read())
    }

    @Test
    fun staleCallbackCannotCompleteTheWaiterForANewerAttempt() = runBlocking {
        val first = AndroidGoogleOAuthPending(AndroidGoogleOAuthMode.SIGN_IN, "first-attempt")
        val second = AndroidGoogleOAuthPending(AndroidGoogleOAuthMode.SIGN_IN, "second-attempt")
        val firstWaiter = async(start = CoroutineStart.UNDISPATCHED) {
            AndroidGoogleOAuthCallbackCoordinator.awaitCompletion(first) {}
        }
        firstWaiter.cancelAndJoin()
        val secondWaiter = async(start = CoroutineStart.UNDISPATCHED) {
            AndroidGoogleOAuthCallbackCoordinator.awaitCompletion(second) {}
        }

        assertFalse(
            AndroidGoogleOAuthCallbackCoordinator.complete(
                first,
                Result.success(
                    AuthSession(
                        token = "first",
                        userId = "first-profile",
                        email = "first@example.test",
                        displayName = "First",
                    ),
                ),
            ),
        )
        assertFalse(secondWaiter.isCompleted)
        assertEquals(
            true,
            AndroidGoogleOAuthCallbackCoordinator.complete(
                second,
                Result.success(
                    AuthSession(
                        token = "second",
                        userId = "second-profile",
                        email = "second@example.test",
                        displayName = "Second",
                    ),
                ),
            ),
        )
        assertEquals("second-profile", secondWaiter.await().userId)
    }

    @Test
    fun linkedCallbackCannotOverwriteCredentialsRefreshedWhileExchangeWasRunning() {
        val retained = session("retained-token")
        val refreshed = session("refreshed-token")
        val linked = session("linked-token")
        val storage = MemorySessionStorage(retained)
        val manager = SessionManager(storage, useMockBackend = true)
        val completion = AndroidGoogleOAuthCompletion(
            pending = AndroidGoogleOAuthPending(
                mode = AndroidGoogleOAuthMode.LINK_IDENTITY,
                codeVerifier = "verifier",
                expectedAuthUserId = retained.authUserId,
                expectedProfileId = retained.userId,
            ),
            session = linked,
            retainedSession = retained,
        )

        manager.setSession(refreshed)

        assertSame(refreshed, manager.publishLinkedGoogleOAuthRecovery(completion))
        assertSame(refreshed, storage.storedSession)
    }

    @Test
    fun onlyAnUndeliveredRecoveredSignInPublishesLoginNavigation() {
        assertEquals(
            true,
            shouldPublishGoogleOAuthRecoveryNavigation(
                AndroidGoogleOAuthMode.SIGN_IN,
                deliveredToActiveLogin = false,
                publicationSucceeded = true,
            ),
        )
        assertFalse(
            shouldPublishGoogleOAuthRecoveryNavigation(
                AndroidGoogleOAuthMode.LINK_IDENTITY,
                deliveredToActiveLogin = false,
                publicationSucceeded = true,
            ),
        )
        assertFalse(
            shouldPublishGoogleOAuthRecoveryNavigation(
                AndroidGoogleOAuthMode.SIGN_IN,
                deliveredToActiveLogin = true,
                publicationSucceeded = true,
            ),
        )
        assertFalse(
            shouldPublishGoogleOAuthRecoveryNavigation(
                AndroidGoogleOAuthMode.SIGN_IN,
                deliveredToActiveLogin = false,
                publicationSucceeded = false,
            ),
        )
    }

    private fun session(token: String) = AuthSession(
        token = token,
        accessToken = token,
        refreshToken = "refresh-token",
        expiresAt = Long.MAX_VALUE,
        userId = "profile-1",
        authUserId = "auth-1",
        email = "member@example.test",
        displayName = "Member",
    )

    private class MemorySessionStorage(initial: AuthSession?) : SessionStorage {
        var storedSession: AuthSession? = initial
        override fun saveSession(session: AuthSession) { storedSession = session }
        override fun getSession(): AuthSession? = storedSession
        override fun clear() { storedSession = null }
    }
}
