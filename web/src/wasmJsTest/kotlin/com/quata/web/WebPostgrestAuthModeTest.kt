@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

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
