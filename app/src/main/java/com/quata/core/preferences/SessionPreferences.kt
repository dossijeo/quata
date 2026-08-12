package com.quata.core.preferences

import android.content.Context
import android.util.Log
import com.quata.core.model.AuthSession

/**
 * Android implementation of the KMP [SessionStorage] boundary.
 *
 * Existing plaintext sessions are read once and rewritten with an Android Keystore-backed
 * AES-GCM envelope. A key invalidation or malformed value safely signs the user out.
 */
class SessionPreferences private constructor(
    context: Context,
    preferencesName: String,
    private val cipher: PreferenceValueCipher
) : SessionStorage {
    constructor(context: Context) : this(context, PREFERENCES_NAME, AndroidKeystorePreferenceValueCipher())

    internal constructor(context: Context, preferencesName: String, keyAlias: String) : this(
        context,
        preferencesName,
        AndroidKeystorePreferenceValueCipher(keyAlias)
    )

    private val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val cacheLock = Any()
    private var cachedSnapshot: SessionSnapshot? = null
    private var cachedSession: AuthSession? = null

    override fun saveSession(session: AuthSession) {
        prefs.edit().apply {
            putString(KEY_TOKEN, cipher.encrypt(session.token))
            putString(KEY_USER_ID, cipher.encrypt(session.userId))
            putString(KEY_EMAIL, cipher.encrypt(session.email))
            putString(KEY_DISPLAY_NAME, cipher.encrypt(session.displayName))
            session.authUserId?.let { putString(KEY_AUTH_USER_ID, cipher.encrypt(it)) } ?: remove(KEY_AUTH_USER_ID)
            session.accessToken?.let { putString(KEY_ACCESS_TOKEN, cipher.encrypt(it)) } ?: remove(KEY_ACCESS_TOKEN)
            session.refreshToken?.let { putString(KEY_REFRESH_TOKEN, cipher.encrypt(it)) } ?: remove(KEY_REFRESH_TOKEN)
            putLong(KEY_EXPIRES_AT, session.expiresAt ?: 0L)
            putBoolean(KEY_IS_OFFICIAL, session.isOfficial)
            apply()
        }
        synchronized(cacheLock) {
            cachedSnapshot = snapshot()
            cachedSession = session
        }
    }

    override fun getSession(): AuthSession? {
        val currentSnapshot = snapshot()
        synchronized(cacheLock) {
            if (currentSnapshot == cachedSnapshot) return cachedSession
        }
        val storedToken = currentSnapshot.token ?: return cache(null, currentSnapshot)
        val storedUserId = currentSnapshot.userId ?: return cache(null, currentSnapshot)
        return try {
            val rawValues = listOfNotNull(
                storedToken, storedUserId, currentSnapshot.email,
                currentSnapshot.displayName, currentSnapshot.authUserId,
                currentSnapshot.accessToken, currentSnapshot.refreshToken
            )
            val session = AuthSession(
                token = decryptOrLegacy(storedToken),
                userId = decryptOrLegacy(storedUserId),
                email = currentSnapshot.email?.let(::decryptOrLegacy).orEmpty(),
                displayName = currentSnapshot.displayName?.let(::decryptOrLegacy) ?: DEFAULT_DISPLAY_NAME,
                authUserId = currentSnapshot.authUserId?.let(::decryptOrLegacy),
                accessToken = currentSnapshot.accessToken?.let(::decryptOrLegacy),
                refreshToken = currentSnapshot.refreshToken?.let(::decryptOrLegacy),
                expiresAt = currentSnapshot.expiresAt.takeIf { it > 0L },
                isOfficial = currentSnapshot.isOfficial,
            )
            if (rawValues.any { !cipher.isEncrypted(it) }) {
                saveSession(session)
                session
            } else {
                cache(session, currentSnapshot)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Discarding unreadable Android session", error)
            clear()
            null
        }
    }

    override fun clear() {
        prefs.edit().clear().apply()
        synchronized(cacheLock) {
            cachedSnapshot = snapshot()
            cachedSession = null
        }
    }

    private fun decryptOrLegacy(value: String): String =
        if (cipher.isEncrypted(value)) cipher.decrypt(value) else value

    private fun cache(session: AuthSession?, snapshot: SessionSnapshot): AuthSession? {
        synchronized(cacheLock) {
            cachedSnapshot = snapshot
            cachedSession = session
        }
        return session
    }

    private fun snapshot() = SessionSnapshot(
        token = prefs.getString(KEY_TOKEN, null),
        userId = prefs.getString(KEY_USER_ID, null),
        email = prefs.getString(KEY_EMAIL, null),
        displayName = prefs.getString(KEY_DISPLAY_NAME, null),
        authUserId = prefs.getString(KEY_AUTH_USER_ID, null),
        accessToken = prefs.getString(KEY_ACCESS_TOKEN, null),
        refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null),
        expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L),
        isOfficial = prefs.getBoolean(KEY_IS_OFFICIAL, false),
    )

    private data class SessionSnapshot(
        val token: String?,
        val userId: String?,
        val email: String?,
        val displayName: String?,
        val authUserId: String?,
        val accessToken: String?,
        val refreshToken: String?,
        val expiresAt: Long,
        val isOfficial: Boolean,
    )

    companion object {
        private const val TAG = "SessionPreferences"
        private const val PREFERENCES_NAME = "quata_session"
        private const val DEFAULT_DISPLAY_NAME = "Usuario"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_AUTH_USER_ID = "auth_user_id"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_IS_OFFICIAL = "is_official"
    }
}
