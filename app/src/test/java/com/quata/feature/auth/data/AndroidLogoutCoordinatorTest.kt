package com.quata.feature.auth.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidLogoutCoordinatorTest {
    @Test
    fun `remote effects finish before local credentials are cleared`() = runBlocking {
        val events = mutableListOf<String>()
        coordinator(events).logout()

        assertEquals(listOf("session-refresh", "cleanup-prepare", "push-retire", "auth-revoke", "private-clear", "local-retire"), events)
    }

    @Test
    fun `expired access token is refreshed before either remote logout effect`() = runBlocking {
        val events = mutableListOf<String>()
        var bearer = "expired"
        AndroidLogoutCoordinator(
            prepareRemoteSession = {
                events += "session-refresh"
                bearer = "fresh"
            },
            preparePrivateDataCleanup = { events += "cleanup-prepare" },
            cancelPrivateDataCleanup = { events += "cleanup-cancel" },
            retirePush = { events += "push:$bearer" },
            revokeAuthSession = { events += "auth:$bearer" },
            restorePush = { events += "restore:$bearer" },
            clearPrivateData = { events += "private-clear" },
            retireLocalSession = { events += "local-retire" },
            recordPrivateDataCleanupFailure = { events += "cleanup-recorded" },
        ).logout()

        assertEquals(listOf("session-refresh", "cleanup-prepare", "push:fresh", "auth:fresh", "private-clear", "local-retire"), events)
    }

    @Test
    fun `push failure preserves auth and local session`() = runBlocking {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("push unavailable")
        val coordinator = coordinator(events, retirePush = {
            events += "push-retire"
            throw failure
        })

        val observed = captureFailure { coordinator.logout() }

        assertEquals(failure, observed)
        assertEquals(listOf("session-refresh", "cleanup-prepare", "push-retire", "cleanup-cancel"), events)
    }

    @Test
    fun `auth failure restores push and preserves local session`() = runBlocking {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("auth unavailable")
        val coordinator = coordinator(events, revokeAuth = {
            events += "auth-revoke"
            throw failure
        })

        val observed = captureFailure { coordinator.logout() }

        assertEquals(failure, observed)
        assertEquals(listOf("session-refresh", "cleanup-prepare", "push-retire", "auth-revoke", "push-restore", "cleanup-cancel"), events)
    }

    @Test
    fun `restore failure never hides original auth failure`() = runBlocking {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("auth unavailable")
        val coordinator = coordinator(
            events,
            revokeAuth = {
                events += "auth-revoke"
                throw failure
            },
            restorePush = {
                events += "push-restore"
                error("restore unavailable")
            },
        )

        val observed = captureFailure { coordinator.logout() }

        assertEquals(failure, observed)
        assertEquals(listOf("session-refresh", "cleanup-prepare", "push-retire", "auth-revoke", "push-restore", "cleanup-cancel"), events)
    }

    @Test
    fun `private cleanup failure still retires local session and records recovery`() = runBlocking {
        val events = mutableListOf<String>()
        AndroidLogoutCoordinator(
            prepareRemoteSession = { events += "session-refresh" },
            preparePrivateDataCleanup = { events += "cleanup-prepare" },
            cancelPrivateDataCleanup = { events += "cleanup-cancel" },
            retirePush = { events += "push-retire" },
            revokeAuthSession = { events += "auth-revoke" },
            restorePush = { events += "push-restore" },
            clearPrivateData = {
                events += "private-clear"
                error("cache unavailable")
            },
            retireLocalSession = { events += "local-retire" },
            recordPrivateDataCleanupFailure = { events += "cleanup-recorded" },
        ).logout()

        assertEquals(
            listOf("session-refresh", "cleanup-prepare", "push-retire", "auth-revoke", "private-clear", "local-retire", "cleanup-recorded"),
            events,
        )
    }

    @Test
    fun `journal failure aborts before remote effects and local retirement`() = runBlocking {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("journal unavailable")
        val coordinator = coordinator(events, prepareCleanup = {
            events += "cleanup-prepare"
            throw failure
        })

        val observed = captureFailure { coordinator.logout() }

        assertEquals(failure, observed)
        assertEquals(listOf("session-refresh", "cleanup-prepare"), events)
    }

    private fun coordinator(
        events: MutableList<String>,
        prepareCleanup: suspend () -> Unit = { events += "cleanup-prepare" },
        retirePush: suspend () -> Unit = { events += "push-retire" },
        revokeAuth: suspend () -> Unit = { events += "auth-revoke" },
        restorePush: suspend () -> Unit = { events += "push-restore" },
    ) = AndroidLogoutCoordinator(
        prepareRemoteSession = { events += "session-refresh" },
        preparePrivateDataCleanup = prepareCleanup,
        cancelPrivateDataCleanup = { events += "cleanup-cancel" },
        retirePush = retirePush,
        revokeAuthSession = revokeAuth,
        restorePush = restorePush,
        clearPrivateData = { events += "private-clear" },
        retireLocalSession = { events += "local-retire" },
        recordPrivateDataCleanupFailure = { events += "cleanup-recorded" },
    )

    private suspend fun captureFailure(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (failure: Throwable) {
            return failure
        }
        throw AssertionError("Expected logout to fail")
    }
}
