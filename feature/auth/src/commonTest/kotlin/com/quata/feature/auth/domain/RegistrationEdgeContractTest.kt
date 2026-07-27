package com.quata.feature.auth.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegistrationEdgeContractTest {
    @Test
    fun iOSPayloadUsesOnlyTheExistingVersionedEdgeFields() {
        val payload = buildRegistrationEdgeRequest(
            request = RegisterAccountRequest(
                displayName = " Gabriela ",
                neighborhood = " Centro ",
                countryCode = "+34",
                phone = "600 100 200",
                password = "LongPassword7",
                secretQuestion = "barrio",
                secretAnswer = " Malasana ",
            ),
            channel = "web",
            clientInstanceId = "ios-install-123",
            idempotencyKey = "0123456789abcdef0123456789abcdef",
            challengeToken = "challenge-token",
        )

        assertEquals(
            setOf(
                "version", "channel", "display_name", "neighborhood", "country_code", "phone_local",
                "password", "secret_question", "secret_answer", "client_instance_id", "idempotency_key",
                "challenge_token",
            ),
            payload.keys,
        )
        assertEquals("34", payload["country_code"].toString().trim('"'))
        assertEquals("600100200", payload["phone_local"].toString().trim('"'))
        listOf("auth_user_id", "profile_id", "is_admin", "is_official", "pass_hash", "pass_plain")
            .forEach { assertFalse(it in payload) }
    }

    @Test
    fun acceptsOnlyOpaqueAcceptedResponse() {
        assertTrue(isRegistrationEdgeAccepted("{\"status\":\"accepted\"}"))
        assertFalse(isRegistrationEdgeAccepted("{\"status\":\"created\",\"profile\":{}}"))
        assertFalse(isRegistrationEdgeAccepted("not-json"))
    }

    @Test
    fun transportIsDisabledUnlessEveryExplicitGateIsPresent() {
        assertFalse(isRegistrationTransportEnabled(false, "public-key", "ios-install", "challenge"))
        assertFalse(isRegistrationTransportEnabled(true, null, "ios-install", "challenge"))
        assertFalse(isRegistrationTransportEnabled(true, "public-key", null, "challenge"))
        assertFalse(isRegistrationTransportEnabled(true, "public-key", "ios-install", null))
        assertTrue(isRegistrationTransportEnabled(true, "public-key", "ios-install", "challenge"))
    }
}
