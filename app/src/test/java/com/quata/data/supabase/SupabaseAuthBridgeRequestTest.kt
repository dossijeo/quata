package com.quata.data.supabase

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseAuthBridgeRequestTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun recoveryQuestionPayloadDoesNotContainPasswordOrSecretAnswer() {
        val payload = json.encodeToString(
            SupabaseAuthBridgeRequest(
                action = "recovery_question",
                country_code = "34",
                phone = "600000000"
            )
        )

        assertTrue(payload.contains("\"action\":\"recovery_question\""))
        assertFalse(payload.contains("\"password\":\""))
        assertFalse(payload.contains("\"secret_answer\":\""))
    }

    @Test
    fun registrationPayloadExactlyMatchesSharedServerContractFixture() {
        val payload = json.encodeToString(
            QuataRegistrationRequest(
                challenge_token = "challenge",
                client_instance_id = "android-instance-123",
                idempotency_key = "0123456789abcdef0123456789abcdef",
                country_code = "34",
                phone_local = "600000000",
                password = "LongPassword7",
                display_name = "Test",
                neighborhood = "Centro",
                secret_question = "barrio",
                secret_answer = "answer"
            )
        )
        val fixture = requireNotNull(javaClass.classLoader?.getResource("android-registration-payload.json"))
            .readText()

        assertEquals(json.parseToJsonElement(fixture), json.parseToJsonElement(payload))
        assertTrue(payload.contains("\"phone_local\":\"600000000\""))
        assertFalse(payload.contains("\"phone\":"))
    }
}
