package com.quata.core.session

import com.quata.core.model.AuthSession
import com.quata.core.preferences.IosKeychainSessionStorage
import com.quata.core.preferences.SessionStorage

/**
 * iOS composition-root boundary for renewing an authenticated session.
 *
 * The Swift host supplies the real transport (for example, a URLSession call to its configured
 * Supabase auth endpoint). This module deliberately owns neither a backend URL nor credentials;
 * it only coordinates the common refresh policy with the iOS Keychain storage adapter.
 */
fun interface IosAuthSessionRefresher {
    suspend fun refresh(session: AuthSession): AuthSession?
}

/** A 400/401 refresh response proves that this exact persisted session is no longer usable. */
class IosAuthSessionRejectedException(
    val rejectedSession: AuthSession,
) : RuntimeException("ios_auth_session_rejected")

/**
 * Reusable iOS-facing session owner that persists through [IosKeychainSessionStorage] and uses
 * [SessionManager] for expiration policy, refresh serialization, and auth-state updates.
 */
class IosRenewableAuthSession(
    private val refresher: IosAuthSessionRefresher,
    storage: SessionStorage = IosKeychainSessionStorage(),
) {
    private val manager = SessionManager(storage)

    val authState = manager.authState

    fun restoredSession(): AuthSession? = manager.currentSession()

    fun save(session: AuthSession) {
        manager.setSession(session)
    }

    /** Returns a valid persisted session or asks the injected host to renew it when required. */
    suspend fun currentSession(forceRefresh: Boolean = false): AuthSession? = resolveTerminalRejection {
        manager.ensureFreshSession(force = forceRefresh, refresh = refresher::refresh)
    }

    /**
     * Strict launch-time validation for public-first iOS composition.
     *
     * A failed refresh deliberately leaves Keychain untouched but does not expose the expired
     * session to authenticated factories. Runtime request paths continue to use [currentSession]
     * so their existing retry policy is unchanged.
     */
    suspend fun validatedRestoredSession(): AuthSession? = resolveTerminalRejection {
        manager.validateFreshSession(refresh = refresher::refresh)
    }

    fun clear() {
        manager.clearSession()
    }

    private suspend fun resolveTerminalRejection(block: suspend () -> AuthSession?): AuthSession? {
        while (true) {
            try {
                return block()
            } catch (rejected: IosAuthSessionRejectedException) {
                if (manager.clearSessionIfMatches(rejected.rejectedSession)) return null
                // A newer session won the race. Validate that session through the same policy
                // instead of treating the stale rejection as either success or logout.
            }
        }
    }
}
