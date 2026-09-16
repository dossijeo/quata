package com.quata.core.platform

import com.quata.core.model.AuthSession
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class JournaledApnsRegistrationTransportTest {
    private val actor = AuthSession("bearer", "actor", "", "", authUserId = "auth")
    private val registration = ApnsRegistration(actor, "abcdef", ApnsEnvironment.Sandbox)

    private class Journal : ApnsRegistrationJournal {
        var records = emptyList<ApnsPendingRegistration>()
        var writable = true
        var readable = true
        override fun read(): List<ApnsPendingRegistration> {
            check(readable) { "unavailable" }
            return records
        }
        override fun write(records: List<ApnsPendingRegistration>): Boolean {
            if (!writable) return false
            this.records = records.toList()
            return true
        }
    }

    private class Remote(val journal: Journal) : ApnsRegistrationTransport {
        val events = mutableListOf<String>()
        var registrationResult = true
        override suspend fun register(registration: ApnsRegistration): Boolean {
            assertEquals(registration.token, journal.records.single().token)
            events += "register"
            return registrationResult
        }
        override suspend fun unregister(registration: ApnsRegistration): Boolean {
            events += "remove:${registration.session.userId}:${registration.token}"
            return true
        }
    }

    @Test fun emptyCleanupDoesNotRefreshCredentialsOrContactRemote() = runTest {
        val journal = Journal()
        val remote = Remote(journal)
        assertTrue(JournaledApnsRegistrationTransport(remote, journal).recoverWithSession {
            error("Offline credential refresh must not run")
        })
        assertTrue(remote.events.isEmpty())
    }

    @Test fun pendingCleanupPreservesRecordWhenCredentialsCannotRefresh() = runTest {
        val journal = Journal()
        val remote = Remote(journal)
        val transport = JournaledApnsRegistrationTransport(remote, journal)
        assertTrue(transport.register(registration))
        assertFalse(transport.recoverWithSession { error("offline") })
        assertEquals(1, journal.records.size)
        assertEquals(listOf("register"), remote.events)
        assertTrue(transport.recoverWithSession { actor })
        assertTrue(journal.records.isEmpty())
    }

    @Test fun uncertainRemoteRegistrationSurvivesANewCoordinatorInstance() = runTest {
        val journal = Journal()
        val remote = Remote(journal).apply { registrationResult = false }
        assertFalse(JournaledApnsRegistrationTransport(remote, journal).register(registration))
        assertEquals(1, journal.records.size)
        val restarted = JournaledApnsRegistrationTransport(remote, journal)
        assertTrue(restarted.recover(actor))
        assertTrue(journal.records.isEmpty())
        assertEquals(listOf("register", "remove:actor:abcdef"), remote.events)
    }

    @Test fun failureToPersistPreventsRemoteRegistration() = runTest {
        val journal = Journal().apply { writable = false }
        val remote = Remote(journal)
        assertFalse(JournaledApnsRegistrationTransport(remote, journal).register(registration))
        assertTrue(remote.events.isEmpty())
    }

    @Test fun failedLocalRemovalKeepsRecordForSafeRetry() = runTest {
        val journal = Journal()
        val remote = Remote(journal)
        val transport = JournaledApnsRegistrationTransport(remote, journal)
        assertTrue(transport.register(registration))
        journal.writable = false
        assertFalse(transport.unregister(registration))
        assertEquals(1, journal.records.size)
        journal.writable = true
        assertTrue(transport.recover(actor))
        assertTrue(journal.records.isEmpty())
    }

    @Test fun foreignActorCannotRetireOrReplaceStoredRegistration() = runTest {
        val journal = Journal()
        val remote = Remote(journal)
        val transport = JournaledApnsRegistrationTransport(remote, journal)
        assertTrue(transport.register(registration))
        val foreign = actor.copy(userId = "other", authUserId = "other-auth")
        assertFalse(transport.recover(foreign))
        assertFalse(transport.register(ApnsRegistration(foreign, "1234", ApnsEnvironment.Production)))
        assertFalse(transport.unregister(ApnsRegistration(foreign, "abcdef", ApnsEnvironment.Sandbox)))
        assertEquals(listOf("register"), remote.events)
        assertEquals(1, journal.records.size)
    }

    @Test fun unreadableStorageIsNotAnEmptyJournal() = runTest {
        val journal = Journal().apply { readable = false }
        val remote = Remote(journal)
        val transport = JournaledApnsRegistrationTransport(remote, journal)
        assertFalse(transport.recover(actor))
        assertFalse(transport.register(registration))
        assertTrue(remote.events.isEmpty())
    }

    @Test fun recoveryRetiresOldTokenBeforeRegisteringTheNewOsToken() = runTest {
        val journal = Journal()
        val remote = Remote(journal)
        assertTrue(JournaledApnsRegistrationTransport(remote, journal).register(registration))
        val restarted = JournaledApnsRegistrationTransport(remote, journal)
        assertTrue(restarted.register(ApnsRegistration(actor, "1234", ApnsEnvironment.Production)))
        assertEquals(listOf("register", "remove:actor:abcdef", "register"), remote.events)
        assertEquals("1234", journal.records.single().token)
        assertFalse(journal.records.single().toString().contains("1234"))
    }
}
