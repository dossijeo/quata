package com.quata.core.platform

import com.quata.core.model.AuthSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ApnsSessionCoordinatorTest {
    private val a = AuthSession("secret-a", "a", "", "", authUserId = "auth-a")
    private val b = AuthSession("secret-b", "b", "", "", authUserId = "auth-b")
    private val environment = ApnsEnvironment.Sandbox

    @Test fun registrationAndRemovalUseTheSameCanonicalToken() = runTest {
        val transport = Transport()
        val coordinator = ApnsSessionCoordinator(transport)
        coordinator.synchronize(a, "  ABCDEF  ", environment)
        coordinator.synchronize(null, null, environment)
        assertEquals(listOf("register:a:abcdef", "remove:a:abcdef"), transport.events)
    }

    private class Transport : ApnsRegistrationTransport {
        val events = mutableListOf<String>()
        var registerResult = true
        var unregisterResult = true
        var waitForRegistration: CompletableDeferred<Unit>? = null
        var requiredRemovalBearer: String? = null
        override suspend fun register(registration: ApnsRegistration): Boolean {
            events += "register:${registration.session.userId}:${registration.token}"
            waitForRegistration?.await()
            return registerResult
        }
        override suspend fun unregister(registration: ApnsRegistration): Boolean {
            events += "remove:${registration.session.userId}:${registration.token}"
            return unregisterResult && (requiredRemovalBearer == null || registration.session.bearerToken == requiredRemovalBearer)
        }
    }

    @Test fun rotationAndLogoutRemoveBeforeRegisteringAnotherToken() = runTest {
        val transport = Transport()
        val coordinator = ApnsSessionCoordinator(transport)
        assertEquals(ApnsSynchronization.Applied, coordinator.synchronize(a, "old", environment))
        assertEquals(ApnsSynchronization.Applied, coordinator.synchronize(a, "new", environment))
        assertEquals(ApnsSynchronization.Applied, coordinator.synchronize(null, null, environment))
        assertEquals(listOf("register:a:old", "remove:a:old", "register:a:new", "remove:a:new"), transport.events)
    }

    @Test fun failedRemovalCannotForgetRegistrationOrRegisterAnotherActor() = runTest {
        val transport = Transport()
        val coordinator = ApnsSessionCoordinator(transport)
        coordinator.synchronize(a, "token", environment)
        transport.unregisterResult = false
        assertEquals(ApnsSynchronization.RemoteFailure, coordinator.synchronize(b, "token", environment))
        assertFalse(transport.events.contains("register:b:token"))
        transport.unregisterResult = true
        assertEquals(ApnsSynchronization.Applied, coordinator.synchronize(null, null, environment))
        assertEquals("remove:a:token", transport.events.last())
    }

    @Test fun uncertainRegistrationIsRetiredOnLogout() = runTest {
        val transport = Transport().apply { registerResult = false }
        val coordinator = ApnsSessionCoordinator(transport)
        assertEquals(ApnsSynchronization.RemoteFailure, coordinator.synchronize(a, "token", environment))
        assertEquals(ApnsSynchronization.Applied, coordinator.synchronize(null, null, environment))
        assertEquals(listOf("register:a:token", "remove:a:token"), transport.events)
    }

    @Test fun logoutDuringRegistrationWaitsAndRetiresItsResult() = runTest {
        val gate = CompletableDeferred<Unit>()
        val transport = Transport().apply { waitForRegistration = gate }
        val coordinator = ApnsSessionCoordinator(transport)
        val registration = async { coordinator.synchronize(a, "token", environment) }
        runCurrent()
        val logout = async { coordinator.synchronize(null, null, environment) }
        runCurrent()
        assertFalse(logout.isCompleted)
        gate.complete(Unit)
        assertEquals(ApnsSynchronization.Superseded, registration.await())
        assertEquals(ApnsSynchronization.Applied, logout.await())
        assertEquals(listOf("register:a:token", "remove:a:token"), transport.events)
    }

    @Test fun queuedOutdatedActorNeverRegistersAfterLatestLogout() = runTest {
        val gate = CompletableDeferred<Unit>()
        val transport = Transport().apply { waitForRegistration = gate }
        val coordinator = ApnsSessionCoordinator(transport)
        val first = async { coordinator.synchronize(a, "token", environment) }
        runCurrent()
        val outdated = async { coordinator.synchronize(b, "token", environment) }
        runCurrent()
        val last = async { coordinator.synchronize(null, null, environment) }
        runCurrent()
        gate.complete(Unit)
        first.await()
        assertEquals(ApnsSynchronization.Superseded, outdated.await())
        assertEquals(ApnsSynchronization.Applied, last.await())
        assertFalse(transport.events.contains("register:b:token"))
    }

    @Test fun refreshedBearerIsUsedBeforeTokenRotation() = runTest {
        val transport = Transport()
        val coordinator = ApnsSessionCoordinator(transport)
        coordinator.synchronize(a, "old", environment)
        transport.requiredRemovalBearer = "fresh"
        assertEquals(ApnsSynchronization.Applied, coordinator.synchronize(a.copy(token = "fresh"), "new", environment))
        assertEquals(ApnsSynchronization.Applied, coordinator.synchronize(null, null, environment))
    }

    @Test fun credentialRefreshBeforeLogoutDoesNotRegisterAgainOrChangeActor() = runTest {
        val transport = Transport()
        val coordinator = ApnsSessionCoordinator(transport)
        coordinator.synchronize(a, "token", environment)
        coordinator.refreshCredential(a.copy(token = "fresh"))
        coordinator.refreshCredential(b)
        transport.requiredRemovalBearer = "fresh"
        assertEquals(ApnsSynchronization.Applied, coordinator.synchronize(null, null, environment))
        assertEquals(listOf("register:a:token", "remove:a:token"), transport.events)
    }

    @Test fun supersededRefreshStillSuppliesFreshBearerForLogout() = runTest {
        val gate = CompletableDeferred<Unit>()
        val transport = Transport().apply { waitForRegistration = gate }
        val coordinator = ApnsSessionCoordinator(transport)
        val first = async { coordinator.synchronize(a, "token", environment) }
        runCurrent()
        val refreshed = async { coordinator.synchronize(a.copy(token = "fresh"), "token", environment) }
        runCurrent()
        val logout = async { coordinator.synchronize(null, null, environment) }
        runCurrent()
        transport.requiredRemovalBearer = "fresh"
        gate.complete(Unit)
        first.await()
        assertEquals(ApnsSynchronization.Superseded, refreshed.await())
        assertEquals(ApnsSynchronization.Applied, logout.await())
    }
}
