package com.quata.data.supabase

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    fun registrationPayloadUsesServerBoundaryAndNeverRequestsPlainPasswordStorage() {
        val payload = json.encodeToString(
            QuataRegistrationRequest(
                challenge_token = "challenge",
                challenge_action = "register_android",
                client_instance_id = "instance-id",
                idempotency_key = "idempotency-id",
                country_code = "34",
                phone = "600000000",
                password = "not-a-real-password",
                display_name = "Test",
                neighborhood = "Centro",
                secret_question = "question",
                secret_answer = "answer"
            )
        )

        assertTrue(payload.contains("\"channel\":\"android\""))
        assertTrue(payload.contains("\"challenge_action\":\"register_android\""))
        assertFalse(payload.contains("pass_plain"))
        assertFalse(payload.contains("pass_hash"))
    }
}
