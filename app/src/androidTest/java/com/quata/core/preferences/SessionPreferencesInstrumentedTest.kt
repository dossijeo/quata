package com.quata.core.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quata.core.model.AuthSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionPreferencesInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val rawPreferences = context.getSharedPreferences(TEST_PREFERENCES_NAME, Context.MODE_PRIVATE)
    private lateinit var preferences: SessionPreferences

    @Before
    fun setUp() {
        rawPreferences.edit().clear().commit()
        preferences = SessionPreferences(context, TEST_PREFERENCES_NAME, TEST_KEY_ALIAS)
    }

    @After
    fun tearDown() {
        rawPreferences.edit().clear().commit()
    }

    @Test
    fun saveSessionEncryptsValuesAndRestoresTheSameSession() {
        val session = testSession()

        preferences.saveSession(session)

        assertEquals(session, preferences.getSession())
        assertEncrypted(KEY_TOKEN, session.token)
        assertEncrypted(KEY_USER_ID, session.userId)
        assertEncrypted(KEY_EMAIL, session.email)
        assertEncrypted(KEY_DISPLAY_NAME, session.displayName)
        assertEncrypted(KEY_AUTH_USER_ID, session.authUserId.orEmpty())
        assertEncrypted(KEY_ACCESS_TOKEN, session.accessToken.orEmpty())
        assertEncrypted(KEY_REFRESH_TOKEN, session.refreshToken.orEmpty())
    }

    @Test
    fun getSessionMigratesLegacyPlaintextValues() {
        val session = testSession()
        rawPreferences.edit()
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_DISPLAY_NAME, session.displayName)
            .putString(KEY_AUTH_USER_ID, session.authUserId)
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .putLong(KEY_EXPIRES_AT, session.expiresAt ?: 0L)
            .commit()

        assertEquals(session, preferences.getSession())

        assertEncrypted(KEY_TOKEN, session.token)
        assertEncrypted(KEY_REFRESH_TOKEN, session.refreshToken.orEmpty())
    }

    @Test
    fun getSessionClearsCorruptedCiphertextInsteadOfCrashing() {
        rawPreferences.edit()
            .putString(KEY_TOKEN, "v1.invalid.invalid")
            .putString(KEY_USER_ID, "legacy-user")
            .commit()

        assertNull(preferences.getSession())
        assertTrue(rawPreferences.all.isEmpty())
    }

    private fun assertEncrypted(key: String, plaintext: String) {
        val stored = rawPreferences.getString(key, null).orEmpty()
        assertTrue(stored.startsWith("v1."))
        assertFalse(stored.contains(plaintext))
    }

    private fun testSession(): AuthSession = AuthSession(
        token = "wordpress-token",
        userId = "profile-123",
        email = "user@example.com",
        displayName = "Quata User",
        authUserId = "auth-456",
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresAt = 1_900_000_000L
    )

    private companion object {
        const val TEST_PREFERENCES_NAME = "quata_session_instrumented_test"
        const val TEST_KEY_ALIAS = "quata_session_instrumented_test_key"
        const val KEY_TOKEN = "token"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_AUTH_USER_ID = "auth_user_id"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
    }
}
