package com.quata.feature.profile.data

import com.quata.feature.profile.presentation.iosProfileAvatarUploadReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosProfileRecoverySecretContractTest {
    @Test
    fun recoverySecretBodyCarriesVersionOneAndEscapesSecrets() {
        assertEquals(
            "{\"version\":1,\"action\":\"update_recovery_secret\",\"secret_question\":\"pet\",\"secret_answer\":\"Lu\\\"na\"}",
            iosProfileRecoverySecretBody(" pet ", "Lu\"na"),
        )
    }

    @Test
    fun existingRemoteAvatarPassesThroughButANewLocalReferenceFailsClosed() {
        assertEquals("https://cdn.example/avatar.jpg", iosProfileAvatarUploadReference(" https://cdn.example/avatar.jpg "))
        assertEquals(null, iosProfileAvatarUploadReference(null))
        val failure = assertFailsWith<UnsupportedOperationException> {
            iosProfileAvatarUploadReference("file:///tmp/avatar.jpg")
        }
        assertEquals("ios_profile_avatar_upload_not_verified", failure.message)
    }

    @Test
    fun authenticated_function_requires_an_explicit_true_result() {
        assertTrue(iosProfileFunctionResponseIsOk("{\"ok\":true}"))
        assertFalse(iosProfileFunctionResponseIsOk("{\"ok\":false}"))
        assertFalse(iosProfileFunctionResponseIsOk("{\"ok\":\"true\"}"))
        assertFalse(iosProfileFunctionResponseIsOk("{malformed"))
    }
}
