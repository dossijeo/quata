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
            apply()
        }
    }

    override fun getSession(): AuthSession? {
        val storedToken = prefs.getString(KEY_TOKEN, null) ?: return null
        val storedUserId = prefs.getString(KEY_USER_ID, null) ?: return null
        return try {
            val rawValues = listOfNotNull(
                storedToken, storedUserId, prefs.getString(KEY_EMAIL, null),
                prefs.getString(KEY_DISPLAY_NAME, null), prefs.getString(KEY_AUTH_USER_ID, null),
                prefs.getString(KEY_ACCESS_TOKEN, null), prefs.getString(KEY_REFRESH_TOKEN, null)
            )
            val session = AuthSession(
                token = decryptOrLegacy(storedToken),
                userId = decryptOrLegacy(storedUserId),
                email = prefs.getString(KEY_EMAIL, null)?.let(::decryptOrLegacy).orEmpty(),
                displayName = prefs.getString(KEY_DISPLAY_NAME, null)?.let(::decryptOrLegacy) ?: DEFAULT_DISPLAY_NAME,
                authUserId = prefs.getString(KEY_AUTH_USER_ID, null)?.let(::decryptOrLegacy),
                accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)?.let(::decryptOrLegacy),
                refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)?.let(::decryptOrLegacy),
                expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L).takeIf { it > 0L }
            )
            if (rawValues.any { !cipher.isEncrypted(it) }) saveSession(session)
            session
        } catch (error: Exception) {
            Log.w(TAG, "Discarding unreadable Android session", error)
            clear()
            null
        }
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private fun decryptOrLegacy(value: String): String =
        if (cipher.isEncrypted(value)) cipher.decrypt(value) else value

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
    }
}
