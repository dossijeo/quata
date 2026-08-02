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

    /**
     * Validates a persisted session before it is allowed to select an authenticated UI runtime.
     *
     * Unlike [ensureFreshSession], a failed renewal must not return an expired session: callers
     * use this at a public-first composition boundary and must keep anonymous dependencies until
     * a fresh access token is available. The stored session is intentionally retained so a later
     * foreground attempt or interactive recovery can retry with the same refresh token.
     */
    suspend fun validateFreshSession(
        refresh: suspend (AuthSession) -> AuthSession?
    ): AuthSession? = refreshMutex.withLock {
        val current = currentSession() ?: return null
        if (!current.shouldRefresh()) return current
        val refreshed = refresh(current) ?: return null
        if (refreshed.shouldRefresh()) return null
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
