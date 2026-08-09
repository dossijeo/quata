package com.quata.feature.auth.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RemotePasswordRecoveryRepositoryTest {
    @Test
    fun questionLookupNormalizesThePortableRecoveryRequest() = runTest {
        val transport = RecordingPasswordRecoveryTransport(
            questionResponse = PasswordRecoveryHttpResponse(200, """{"secret_question":" madre "}"""),
        )
        val repository = RemotePasswordRecoveryRepository(transport)

        val question = repository.getPasswordRecoveryQuestion("+240", "680 242 607").getOrThrow()

        assertEquals("madre", question?.secretQuestion)
        assertEquals(listOf("240" to "680242607"), transport.questionRequests)
    }

    @Test
    fun question404IsTheOnlyPortableAccountMissingResult() = runTest {
        val repository = RemotePasswordRecoveryRepository(
            RecordingPasswordRecoveryTransport(
                questionResponse = PasswordRecoveryHttpResponse(404, """{"error":"not_found"}"""),
            ),
        )

        assertEquals(null, repository.getPasswordRecoveryQuestion("240", "680242608").getOrThrow())
    }

    @Test
    fun malformedQuestionAndHttpFailuresRemainFailures() = runTest {
        val malformed = RemotePasswordRecoveryRepository(
            RecordingPasswordRecoveryTransport(
                questionResponse = PasswordRecoveryHttpResponse(200, """{"secret_question":" "}"""),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            malformed.getPasswordRecoveryQuestion("240", "680242608").getOrThrow()
        }

        val http = RemotePasswordRecoveryRepository(
            RecordingPasswordRecoveryTransport(
                questionResponse = PasswordRecoveryHttpResponse(500, """{"error":"down"}"""),
            ),
        )
        val failure = http.getPasswordRecoveryQuestion("240", "680242608").exceptionOrNull()
        assertEquals("recovery_question_http_500", failure?.message)
    }

    @Test
    fun resetRequiresAnExplicitOkResponseAndSendsNormalizedInputs() = runTest {
        val transport = RecordingPasswordRecoveryTransport(
            resetResponse = PasswordRecoveryHttpResponse(200, """{"ok":true}"""),
        )
        val repository = RemotePasswordRecoveryRepository(transport)

        repository.resetPassword("+240", "680 242 607", " Luna ", "21085800").getOrThrow()

        assertEquals(
            listOf(ResetTransportRequest("240", "680242607", " Luna ", "21085800")),
            transport.resetRequests,
        )

        val rejected = RemotePasswordRecoveryRepository(
            RecordingPasswordRecoveryTransport(
                resetResponse = PasswordRecoveryHttpResponse(200, """{"ok":false}"""),
            ),
        )
        assertEquals(
            "password_reset_failed",
            rejected.resetPassword("240", "680242607", "Luna", "21085800").exceptionOrNull()?.message,
        )
    }

    @Test
    fun validationFailsBeforeTouchingTransport() = runTest {
        val transport = RecordingPasswordRecoveryTransport()
        val repository = RemotePasswordRecoveryRepository(transport)

        assertTrue(repository.getPasswordRecoveryQuestion("", "1234").isFailure)
        assertTrue(repository.resetPassword("240", "1234", "Luna", "21085800").isFailure)
        assertTrue(transport.questionRequests.isEmpty())
        assertTrue(transport.resetRequests.isEmpty())
    }
}

private class RecordingPasswordRecoveryTransport(
    private val questionResponse: PasswordRecoveryHttpResponse = PasswordRecoveryHttpResponse(
        200,
        """{"secret_question":"madre"}""",
    ),
    private val resetResponse: PasswordRecoveryHttpResponse = PasswordRecoveryHttpResponse(
        200,
        """{"ok":true}""",
    ),
) : PasswordRecoveryTransport {
    val questionRequests = mutableListOf<Pair<String, String>>()
    val resetRequests = mutableListOf<ResetTransportRequest>()

    override suspend fun getQuestion(countryCode: String, phone: String): PasswordRecoveryHttpResponse {
        questionRequests += countryCode to phone
        return questionResponse
    }

    override suspend fun resetPassword(
        countryCode: String,
        phone: String,
        secretAnswer: String,
        newPassword: String,
    ): PasswordRecoveryHttpResponse {
        resetRequests += ResetTransportRequest(countryCode, phone, secretAnswer, newPassword)
        return resetResponse
    }
}

private data class ResetTransportRequest(
    val countryCode: String,
    val phone: String,
    val secretAnswer: String,
    val newPassword: String,
)
