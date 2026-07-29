package com.quata.feature.auth.data

import com.quata.feature.auth.domain.PasswordRecoveryQuestion
import com.quata.feature.auth.domain.PasswordRecoveryRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Platform transports perform HTTP only; auth-bridge response validation stays portable. */
interface PasswordRecoveryTransport {
    suspend fun getQuestion(countryCode: String, phone: String): PasswordRecoveryHttpResponse
    suspend fun resetPassword(
        countryCode: String,
        phone: String,
        secretAnswer: String,
        newPassword: String,
    ): PasswordRecoveryHttpResponse
}

data class PasswordRecoveryHttpResponse(val status: Int, val body: String)

class RemotePasswordRecoveryRepository(
    private val transport: PasswordRecoveryTransport,
) : PasswordRecoveryRepository {
    override suspend fun getPasswordRecoveryQuestion(
        countryCode: String,
        phone: String,
    ): Result<PasswordRecoveryQuestion?> = runCatching {
        val normalizedCountryCode = countryCode.filter(Char::isDigit)
        val normalizedPhone = phone.filter(Char::isDigit)
        require(normalizedCountryCode.isNotBlank()) { "Introduce un prefijo válido" }
        require(normalizedPhone.length >= 5) { "Introduce un teléfono válido" }
        val response = transport.getQuestion(normalizedCountryCode, normalizedPhone)
        if (response.status == 404) return@runCatching null
        require(response.status in 200..299) { "recovery_question_http_${response.status}" }
        val question = Json.parseToJsonElement(response.body).jsonObject["secret_question"]
            ?.jsonPrimitive?.content?.trim()
        require(!question.isNullOrEmpty()) { "recovery_question_invalid_response" }
        PasswordRecoveryQuestion(secretQuestion = question)
    }

    override suspend fun resetPassword(
        countryCode: String,
        phone: String,
        secretAnswer: String,
        newPassword: String,
    ): Result<Unit> = runCatching {
        val normalizedCountryCode = countryCode.filter(Char::isDigit)
        val normalizedPhone = phone.filter(Char::isDigit)
        require(normalizedCountryCode.isNotBlank()) { "Introduce un prefijo válido" }
        require(normalizedPhone.length >= 5) { "Introduce un teléfono válido" }
        val response = transport.resetPassword(normalizedCountryCode, normalizedPhone, secretAnswer, newPassword)
        require(response.status in 200..299) { "reset_password_http_${response.status}" }
        val ok = Json.parseToJsonElement(response.body).jsonObject["ok"]?.jsonPrimitive?.content == "true"
        require(ok) { "password_reset_failed" }
    }
}
