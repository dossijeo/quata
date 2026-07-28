@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WebPostgrestAuthModeTest {
    @Test
    fun publicFeedDiagnosticsWriteBothStateAndErrorAsAValidBrowserExpression() {
        clearWebFeedDiagnostics()

        recordWebFeedReadState(
            table = "community_posts",
            state = "request_failed",
            errorCode = "network",
        )

        assertEquals("request_failed", readWebFeedDiagnostic("web.feed.remote_read_state"))
        assertEquals("network", readWebFeedDiagnostic("web.feed.remote_read_error"))
    }

    @Test
    fun sessionRequiredWithoutSessionFailsBeforeRequestConstruction() {
        val failure = assertFailsWith<IllegalStateException> {
            webPostgrestReadAccessToken(WebPostgrestAuthMode.SessionRequired, null).getOrThrow()
        }

        assertEquals("web_session_missing", failure.message)
    }

    @Test
    fun publicReadOmitsBearerEvenWhenSessionExists() {
        assertNull(webPostgrestReadAccessToken(WebPostgrestAuthMode.Public, "session-token").getOrThrow())
    }

    @Test
    fun publicReadDoesNotInvokeTheSessionProvider() = runSuspend {
        var invocations = 0

        val token = resolveWebPostgrestReadAccessToken(WebPostgrestAuthMode.Public) {
            invocations += 1
            "must-not-be-read"
        }.getOrThrow()

        assertNull(token)
        assertEquals(0, invocations)
    }

    @Test
    fun sessionRequiredReadInvokesTheSessionProvider() = runSuspend {
        var invocations = 0

        val token = resolveWebPostgrestReadAccessToken(WebPostgrestAuthMode.SessionRequired) {
            invocations += 1
            "session-token"
        }.getOrThrow()

        assertEquals("session-token", token)
        assertEquals(1, invocations)
    }

    @Test
    fun sessionRequiredReadRetainsBearerToken() {
        assertEquals(
            "session-token",
            webPostgrestReadAccessToken(WebPostgrestAuthMode.SessionRequired, "session-token").getOrThrow(),
        )
    }

    @Test
    fun mutationsRemainSessionOnly() {
        assertFailsWith<IllegalStateException> {
            webPostgrestSessionAccessToken(null).getOrThrow()
        }
        assertEquals("session-token", webPostgrestSessionAccessToken("session-token").getOrThrow())
    }
}

private fun clearWebFeedDiagnostics(): Unit = js("(globalThis.localStorage?.removeItem('web.feed.remote_read_state'), globalThis.localStorage?.removeItem('web.feed.remote_read_error'))")

private fun readWebFeedDiagnostic(key: String): String? = js("globalThis.localStorage?.getItem(key)")

private fun <T> runSuspend(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) { outcome = result }
    })
    return requireNotNull(outcome) { "test_coroutine_did_not_complete_synchronously" }.getOrThrow()
}
