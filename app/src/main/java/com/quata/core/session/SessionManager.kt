package com.quata.core.session

import com.quata.core.model.AuthSession
import com.quata.core.preferences.SessionPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionManager(private val preferences: SessionPreferences) {
    private val _authState = MutableStateFlow(readInitialState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun isLoggedIn(): Boolean = preferences.getSession() != null

    fun currentSession(): AuthSession? = preferences.getSession()

    fun setSession(session: AuthSession) {
        preferences.saveSession(session)
        _authState.value = AuthState.LoggedIn(session.userId, session.displayName)
    }

    fun clearSession() {
        preferences.clear()
        _authState.value = AuthState.LoggedOut
    }

    private fun readInitialState(): AuthState {
        val session = preferences.getSession()
        return if (session == null) AuthState.LoggedOut else AuthState.LoggedIn(session.userId, session.displayName)
    }
}
