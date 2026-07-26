package com.quata.web

import com.quata.feature.auth.domain.RegisterAccountRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WebRegistrationRequestTest {
    @Test
    fun emitsOnlyVersionedPublicRegistrationFields() {
        val body = buildWebRegistrationRequest(
            request = RegisterAccountRequest(
                displayName = " Gabriela ",
                neighborhood = " Centro ",
                countryCode = "+34",
                phone = "600 100 200",
                password = "LongPassword7",
                secretQuestion = "barrio",
                secretAnswer = " Malasaña ",
            ),
            clientInstanceId = "browser-instance-123",
            idempotencyKey = "0123456789abcdef0123456789abcdef",
            challengeToken = "turnstile-token",
        )

        assertEquals(
            setOf(
                "version",
                "channel",
                "display_name",
                "neighborhood",
                "country_code",
                "phone_local",
                "password",
                "secret_question",
                "secret_answer",
                "client_instance_id",
                "idempotency_key",
                "challenge_token",
            ),
            body.keys,
        )
        assertEquals("Gabriela", body["display_name"].toString().trim('"'))
        assertEquals("34", body["country_code"].toString().trim('"'))
        assertEquals("600100200", body["phone_local"].toString().trim('"'))
        listOf("auth_user_id", "profile_id", "is_admin", "is_official", "pass_hash", "pass_plain")
            .forEach { assertFalse(it in body) }
    }
}
