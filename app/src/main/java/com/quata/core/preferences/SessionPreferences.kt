package com.quata.core.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.quata.core.model.AuthSession

class SessionPreferences private constructor(
    context: Context,
    preferencesName: String,
    private val cipher: PreferenceValueCipher
) {
    constructor(context: Context) : this(
        context = context,
        preferencesName = PREFERENCES_NAME,
        cipher = AndroidKeystorePreferenceValueCipher()
    )

    internal constructor(context: Context, preferencesName: String, keyAlias: String) : this(
        context = context,
        preferencesName = preferencesName,
        cipher = AndroidKeystorePreferenceValueCipher(keyAlias)
    )

    private val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun saveSession(session: AuthSession) {
        val encryptedValues = mapOf(
            KEY_TOKEN to encrypted(session.token),
            KEY_USER_ID to encrypted(session.userId),
            KEY_EMAIL to encrypted(session.email),
            KEY_DISPLAY_NAME to encrypted(session.displayName),
            KEY_AUTH_USER_ID to session.authUserId?.let(::encrypted),
            KEY_ACCESS_TOKEN to session.accessToken?.let(::encrypted),
            KEY_REFRESH_TOKEN to session.refreshToken?.let(::encrypted)
        )
        prefs.edit().apply {
            encryptedValues.forEach { (key, value) -> putEncryptedString(key, value) }
            putLong(KEY_EXPIRES_AT, session.expiresAt ?: 0L)
        }.apply()
    }

    fun getSession(): AuthSession? {
        val rawToken = prefs.getString(KEY_TOKEN, null) ?: return null
        val rawUserId = prefs.getString(KEY_USER_ID, null) ?: return null
        return try {
            val rawValues = listOfNotNull(
                rawToken,
                rawUserId,
                prefs.getString(KEY_EMAIL, null),
                prefs.getString(KEY_DISPLAY_NAME, null),
                prefs.getString(KEY_AUTH_USER_ID, null),
                prefs.getString(KEY_ACCESS_TOKEN, null),
                prefs.getString(KEY_REFRESH_TOKEN, null)
            )
            val session = AuthSession(
                token = decrypted(rawToken),
                userId = decrypted(rawUserId),
                email = prefs.getString(KEY_EMAIL, null)?.let(::decrypted).orEmpty(),
                displayName = prefs.getString(KEY_DISPLAY_NAME, null)?.let(::decrypted) ?: DEFAULT_DISPLAY_NAME,
                authUserId = prefs.getString(KEY_AUTH_USER_ID, null)?.let(::decrypted),
                accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)?.let(::decrypted),
                refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)?.let(::decrypted),
                expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L).takeIf { it > 0L }
            )
            if (rawValues.any { !cipher.isEncrypted(it) }) {
                saveSession(session)
            }
            session
        } catch (error: Exception) {
            Log.w(TAG, "Discarding an unreadable encrypted session", error)
            clear()
            null
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun encrypted(value: String): String = cipher.encrypt(value)

    private fun decrypted(value: String): String =
        if (cipher.isEncrypted(value)) cipher.decrypt(value) else value

    private fun SharedPreferences.Editor.putEncryptedString(key: String, value: String?) {
        if (value == null) remove(key) else putString(key, value)
    }

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
