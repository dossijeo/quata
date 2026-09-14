package com.quata.core.platform

import com.quata.core.model.AuthSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
data class ApnsPendingRegistration(
    val profileId: String,
    val authUserId: String?,
    val token: String,
    val environment: String,
) {
    override fun toString(): String = "ApnsPendingRegistration(redacted)"

    fun belongsTo(session: AuthSession): Boolean =
        profileId == session.userId && authUserId == session.authUserId

    fun registration(session: AuthSession): ApnsRegistration = ApnsRegistration(
        session, token, ApnsEnvironment.entries.single { it.wireValue == environment },
    )
}

interface ApnsRegistrationJournal {
    /** Throws on unreadable/corrupt storage; only an absent item represents an empty journal. */
    fun read(): List<ApnsPendingRegistration>
    fun write(records: List<ApnsPendingRegistration>): Boolean
}

/** Journal before the remote mutation; retain it until both remote removal and local write succeed. */
class JournaledApnsRegistrationTransport(
    private val delegate: ApnsRegistrationTransport,
    private val journal: ApnsRegistrationJournal,
) : ApnsRegistrationTransport {
    private val mutex = Mutex()

    override suspend fun register(registration: ApnsRegistration): Boolean = guarded {
        if (!recoverLocked(registration.session)) return@guarded false
        val record = ApnsPendingRegistration(registration.session.userId, registration.session.authUserId,
            registration.token, registration.environment.wireValue)
        if (!journal.write(listOf(record))) return@guarded false
        delegate.register(registration)
    }

    override suspend fun unregister(registration: ApnsRegistration): Boolean = guarded {
        val records = journal.read()
        if (records.any { !it.belongsTo(registration.session) }) return@guarded false
        if (!delegate.unregister(registration)) return@guarded false
        journal.write(records.filterNot {
            it.token == registration.token && it.environment == registration.environment.wireValue
        })
    }

    /** Run before logout completes, including when this process has not received a fresh OS token. */
    suspend fun recover(session: AuthSession?): Boolean = guarded { recoverLocked(session) }

    private suspend fun recoverLocked(session: AuthSession?): Boolean {
        var records = journal.read()
        if (records.isEmpty()) return true
        if (session == null || records.any { !it.belongsTo(session) }) return false
        while (records.isNotEmpty()) {
            if (!delegate.unregister(records.first().registration(session))) return false
            records = records.drop(1)
            if (!journal.write(records)) return false
        }
        return true
    }

    private suspend fun guarded(action: suspend () -> Boolean): Boolean = mutex.withLock {
        try { action() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { false }
    }
}
