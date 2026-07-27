package com.quata.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WebPostgrestAuthModeTest {
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
