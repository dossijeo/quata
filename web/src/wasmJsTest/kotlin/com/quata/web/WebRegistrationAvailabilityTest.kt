package com.quata.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WebRegistrationAvailabilityTest {
    @Test
    fun refusesRegistrationWithoutContactingOrImitatingTheLoginBridge() {
        val failure = assertFailsWith<UnsupportedOperationException> {
            webRegistrationUnavailable().getOrThrow()
        }

        assertEquals(WebRegistrationUnavailableCode, failure.message)
    }
}
