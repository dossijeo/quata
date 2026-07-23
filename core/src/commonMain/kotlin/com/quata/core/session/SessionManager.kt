package com.quata.core.session

import com.quata.core.model.AuthSession
import com.quata.core.preferences.SessionStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SessionManager(
    private val preferences: SessionStorage,
    private val useMockBackend: Boolean = false
) {
    private val _authState = MutableStateFlow(readInitialState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    private val refreshMutex = Mutex()

    fun isLoggedIn(): Boolean = currentSession() != null

    fun currentSession(): AuthSession? = usableSession(preferences.getSession())

    fun setSession(session: AuthSession) {
        preferences.saveSession(session)
        _authState.value = AuthState.LoggedIn(session.userId, session.displayName)
    }

    fun updateSession(session: AuthSession) {
        setSession(session)
    }

    suspend fun ensureFreshSession(
        force: Boolean = false,
        refresh: suspend (AuthSession) -> AuthSession?
    ): AuthSession? = refreshMutex.withLock {
        val current = currentSession() ?: return null
        if (!force && !current.shouldRefresh()) return current
        val refreshed = refresh(current)
        if (refreshed == null) {
            return if (currentSession() == null) null else current
        }
        setSession(refreshed)
        refreshed
    }

    fun clearSession() {
        preferences.clear()
        _authState.value = AuthState.LoggedOut
    }

    private fun readInitialState(): AuthState {
        val session = usableSession(preferences.getSession())
        return if (session == null) AuthState.LoggedOut else AuthState.LoggedIn(session.userId, session.displayName)
    }

    private fun usableSession(session: AuthSession?): AuthSession? {
        if (session == null) return null
        if (!useMockBackend && !session.isSupabaseAuthenticated()) {
            preferences.clear()
            return null
        }
        return session
    }
}
