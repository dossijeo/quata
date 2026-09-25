package com.quata.core.session

import com.quata.core.model.AuthSession
import com.quata.core.preferences.SessionStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class SessionManager(
    private val preferences: SessionStorage,
    private val useMockBackend: Boolean = false
) {
    private val _authState = MutableStateFlow(readInitialState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    private val refreshMutex = Mutex()
    private val sessionMutationInProgress = AtomicBoolean(false)

    fun isLoggedIn(): Boolean = currentSession() != null

    fun currentSession(): AuthSession? = withSessionMutationLock { currentSessionUnlocked() }

    fun setSession(session: AuthSession) {
        withSessionMutationLock { setSessionUnlocked(session) }
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
            return current
        }
        withSessionMutationLock {
            val latest = currentSessionUnlocked()
            if (latest != current) return@withSessionMutationLock latest
            setSessionUnlocked(refreshed)
            refreshed
        }
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
        withSessionMutationLock {
            if (currentSessionUnlocked() != current) return@withSessionMutationLock null
            setSessionUnlocked(refreshed)
            refreshed
        }
    }

    fun clearSession() {
        withSessionMutationLock { clearSessionUnlocked() }
    }

    private fun readInitialState(): AuthState {
        val stored = preferences.getSession()
        val session = if (stored != null && !useMockBackend && !stored.isSupabaseAuthenticated()) {
            preferences.clear()
            null
        } else {
            stored
        }
        return if (session == null) AuthState.LoggedOut else AuthState.LoggedIn(session.userId, session.displayName)
    }

    private fun usableSession(session: AuthSession?): AuthSession? {
        if (session == null) return null
        if (!useMockBackend && !session.isSupabaseAuthenticated()) {
            clearSessionUnlocked()
            return null
        }
        return session
    }

    private fun currentSessionUnlocked(): AuthSession? = usableSession(preferences.getSession())

    private fun setSessionUnlocked(session: AuthSession) {
        preferences.saveSession(session)
        _authState.value = AuthState.LoggedIn(session.userId, session.displayName)
    }

    private fun clearSessionUnlocked() {
        preferences.clear()
        _authState.value = AuthState.LoggedOut
    }

    private inline fun <T> withSessionMutationLock(block: () -> T): T {
        while (!sessionMutationInProgress.compareAndSet(expectedValue = false, newValue = true)) {
            // Session persistence is synchronous and the critical sections contain no suspension.
        }
        return try {
            block()
        } finally {
            sessionMutationInProgress.store(false)
        }
    }
}
