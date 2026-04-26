package com.quata.core.preferences

import android.content.Context
import com.quata.core.model.AuthSession

class SessionPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("quata_session", Context.MODE_PRIVATE)

    fun saveSession(session: AuthSession) {
        prefs.edit()
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_DISPLAY_NAME, session.displayName)
            .apply()
    }

    fun getSession(): AuthSession? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, "") ?: ""
        val displayName = prefs.getString(KEY_DISPLAY_NAME, "Usuario") ?: "Usuario"
        return AuthSession(token, userId, email, displayName)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_DISPLAY_NAME = "display_name"
    }
}
