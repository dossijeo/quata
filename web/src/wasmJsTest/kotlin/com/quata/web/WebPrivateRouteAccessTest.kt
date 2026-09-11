@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.quata.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebPrivateRouteAccessTest {
    private val session = WebLocalSession("access", "refresh", "web", "profile", 4102444800)
    private fun gate(nav: WebNavigationController) =
        WebPrivateRouteAccess({ nav.accessRevision }, { nav.accessFragment })

    @Test
    fun pendingSessionCannotAuthorizeAndOnlyCurrentSuccessOpensRoute() = runTest {
        val nav = WebNavigationController("chat-sb%3A1?message=2", {})
        val access = gate(nav)
        val pending = CompletableDeferred<WebLocalSession?>()
        var applied = 0
        val job = async { access.resolve(access.ticket, { pending.await() }) { applied++ } }
        runCurrent()
        assertFalse(access.isAllowed)
        assertEquals(0, applied)
        pending.complete(session)
        job.await()
        assertTrue(access.isAllowed)
        assertEquals(1, applied)
    }

    @Test
    fun oldResponseCannotAffectAnotherPrivateOrPublicDestination() = runTest {
        for (next in listOf("chat-sb%3A3?message=4", "feed")) {
            val nav = WebNavigationController("chat-sb%3A1?message=2", {})
            val access = gate(nav)
            val pending = CompletableDeferred<WebLocalSession?>()
            var applied = 0
            val job = async { access.resolve(access.ticket, { pending.await() }) { applied++ } }
            runCurrent()
            nav.acceptBrowserFragment(next)
            pending.complete(session)
            job.await()
            assertEquals(0, applied)
            assertEquals(next, nav.fragment)
            assertFalse(access.isAllowed)
        }
    }

    @Test
    fun authenticationDecisionInvalidatesLateRejection() = runTest {
        val nav = WebNavigationController("chat-sb%3A1?message=2", {})
        val access = gate(nav)
        val pending = CompletableDeferred<WebLocalSession?>()
        var rejected = false
        val job = async { access.resolve(access.ticket, { pending.await() }) { rejected = true } }
        runCurrent()
        access.invalidateAuthentication()
        access.resolve(access.ticket, { session }) {}
        pending.complete(null)
        job.await()
        assertFalse(rejected)
        assertTrue(access.isAllowed)
    }

    @Test
    fun cancelledResolutionCannotApplyEvenIfTransportReturnsLate() = runTest {
        val nav = WebNavigationController("chat-sb%3A1?message=2", {})
        val access = gate(nav)
        val pending = CompletableDeferred<WebLocalSession?>()
        var applied = false
        val job = async {
            access.resolve(access.ticket, { withContext(NonCancellable) { pending.await() } }) { applied = true }
        }
        runCurrent()
        job.cancel()
        pending.complete(session)
        job.join()
        assertFalse(applied)
        assertFalse(access.isAllowed)
    }

    @Test
    fun rejectionKeepsExactPendingFragmentWithoutAuthorizingRoute() = runTest {
        val nav = WebNavigationController("chat-sb%3A1?message=2", {})
        val access = gate(nav)
        val ticket = access.ticket
        var pending: String? = null
        access.resolve(ticket, { null }) { pending = ticket.fragment; nav.navigate("feed") }
        assertEquals("chat-sb%3A1?message=2", pending)
        assertEquals("feed", nav.fragment)
        assertFalse(access.isAllowed)
    }

    @Test
    fun internalFocusConsumptionRetainsPermissionButExternalMessageRequiresValidation() = runTest {
        val nav = WebNavigationController("chat-sb%3A1?message=2", {})
        val access = gate(nav)
        access.resolve(access.ticket, { session }) {}
        val before = access.ticket
        nav.consumeFocusedMessage()
        nav.acceptBrowserFragment(nav.fragment) // Browser echo of the internal acknowledgement.
        assertEquals(before, access.ticket)
        assertTrue(access.isAllowed)
        assertEquals("sb:1", nav.chatConversationId)
        assertEquals(null, nav.chatMessageId)
        nav.acceptBrowserFragment("chat-sb%3A1?message=3")
        assertFalse(access.isAllowed)
    }
}
