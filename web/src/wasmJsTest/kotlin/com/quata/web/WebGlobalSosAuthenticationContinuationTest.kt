package com.quata.web

import com.quata.core.navigation.AuthenticationContinuationCoordinator
import com.quata.core.navigation.AuthenticationContinuationIntent
import com.quata.core.navigation.AuthenticationContinuationKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebGlobalSosAuthenticationContinuationTest {
    @Test
    fun clearingPendingStateDoesNotCancelTransferredDispatch() = runTest {
        val appScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val coordinator = AuthenticationContinuationCoordinator()
        val pending = coordinator.request(
            AuthenticationContinuationIntent(
                kind = AuthenticationContinuationKind.GlobalSosDispatch,
                originRoute = "official",
            ),
        )
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var completed = 0

        assertTrue(
            appScope.resumeGlobalSosAfterAuthentication(coordinator, pending, isAuthenticated = true) {
                started.complete(Unit)
                release.await()
                completed += 1
            },
        )
        assertNull(coordinator.pending.value)
        testScheduler.runCurrent()
        assertTrue(started.isCompleted)

        release.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(1, completed)
        assertFalse(
            appScope.resumeGlobalSosAfterAuthentication(coordinator, pending, isAuthenticated = true) {
                completed += 1
            },
        )
        testScheduler.advanceUntilIdle()
        assertEquals(1, completed)
        appScope.cancel()
    }
}
