package com.quata.core.platform

import com.quata.core.model.AuthSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ApnsEnvironment(val wireValue: String) { Sandbox("sandbox"), Production("production") }

/** Credentials remain in memory and are never included in diagnostics. */
class ApnsRegistration(val session: AuthSession, val token: String, val environment: ApnsEnvironment) {
    override fun toString(): String = "ApnsRegistration(redacted)"
}

interface ApnsRegistrationTransport {
    suspend fun register(registration: ApnsRegistration): Boolean
    suspend fun unregister(registration: ApnsRegistration): Boolean
}

enum class ApnsSynchronization { Applied, Superseded, RemoteFailure }

/**
 * Call from one lifecycle dispatcher. Network operations are serialized even across suspension.
 * Logout must await Applied for a null session before clearing credentials. A failed removal
 * retains its registration so a later attempt cannot silently forget a still-active token.
 */
class ApnsSessionCoordinator(private val transport: ApnsRegistrationTransport) {
    private val mutex = Mutex()
    private var revision = 0L
    private var registered: ApnsRegistration? = null

    suspend fun synchronize(
        session: AuthSession?,
        token: String?,
        environment: ApnsEnvironment,
    ): ApnsSynchronization {
        val requestRevision = ++revision
        // Preserve a refreshed credential even if this request later becomes superseded.
        // Only the credential changes here; token/environment and remote operations stay serialized.
        registered?.let { prior ->
            if (session != null && prior.session.userId == session.userId &&
                prior.session.authUserId == session.authUserId) {
                registered = ApnsRegistration(session, prior.token, prior.environment)
            }
        }
        val desired = if (session != null && !token.isNullOrBlank()) {
            ApnsRegistration(session, token, environment)
        } else null
        return mutex.withLock {
            if (requestRevision != revision) return@withLock ApnsSynchronization.Superseded
            val previous = registered
            if (previous != null && !sameRegistration(previous, desired)) {
                if (!attempt { transport.unregister(previous) }) return@withLock ApnsSynchronization.RemoteFailure
                registered = null
                registrationConfirmed = false
            }
            // Another lifecycle request may have arrived while removal was suspended.
            if (requestRevision != revision) return@withLock ApnsSynchronization.Superseded
            if (desired != null) {
                if (registered == null) {
                    // Retain an uncertain registration too: a transport failure may follow a
                    // successful server write. Subsequent logout/change must retire it first.
                    registered = desired
                    registrationConfirmed = false
                    if (!attempt { transport.register(desired) }) {
                        registrationConfirmed = false
                        return@withLock ApnsSynchronization.RemoteFailure
                    }
                    registrationConfirmed = true
                } else if (!registrationConfirmed) {
                    registered = desired
                    if (!attempt { transport.register(desired) }) return@withLock ApnsSynchronization.RemoteFailure
                    registrationConfirmed = true
                } else {
                    // Same registration, refreshed bearer: use the latest credentials for logout.
                    registered = desired
                }
            }
            if (requestRevision == revision) ApnsSynchronization.Applied else ApnsSynchronization.Superseded
        }
    }

    private var registrationConfirmed = false

    private fun sameRegistration(a: ApnsRegistration, b: ApnsRegistration?): Boolean =
        b != null && a.session.userId == b.session.userId && a.session.authUserId == b.session.authUserId &&
            a.token == b.token && a.environment == b.environment

    private suspend fun attempt(action: suspend () -> Boolean): Boolean = try {
        action()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }
}
