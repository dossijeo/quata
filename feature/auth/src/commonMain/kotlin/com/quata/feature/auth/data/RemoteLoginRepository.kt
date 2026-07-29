package com.quata.feature.auth.data

import com.quata.core.model.AuthSession
import com.quata.core.model.currentEpochSeconds
import com.quata.feature.auth.domain.LoginRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

fun interface LoginTransport {
    suspend fun login(countryCode: String, phone: String, password: String): String
}

/**
 * Shared login repository and auth-bridge response mapping.
 *
 * Only the HTTP transport is platform-specific.
 */
class RemoteLoginRepository(
    private val transport: LoginTransport,
) : LoginRepository {
    override suspend fun login(
        countryCode: String,
        phone: String,
        password: String,
    ): Result<AuthSession> = runCatching {
        require(countryCode.any(Char::isDigit)) { "Introduce un prefijo válido" }
        require(phone.any(Char::isDigit)) { "Introduce tu teléfono" }
        require(password.isNotBlank()) { "Introduce tu contraseña" }
        transport.login(
            countryCode = countryCode.filter(Char::isDigit),
            phone = phone.filter(Char::isDigit),
            password = password,
        ).toAuthSession()
    }
}

private fun String.toAuthSession(): AuthSession {
    val root = Json.parseToJsonElement(this).jsonObject
    val session = root.requiredObject("session")
    val profile = root.requiredObject("profile")
    val user = root["user"]?.jsonObject
    val accessToken = session.requiredString("access_token")
    val refreshToken = session.requiredString("refresh_token")
    val expiresAt = session["expires_at"]?.jsonPrimitive?.longOrNull
        ?: session["expires_in"]?.jsonPrimitive?.longOrNull?.let { currentEpochSeconds() + it }
    val userId = profile.requiredString("id")
    val countryCode = profile.stringOrNull("country_code").orEmpty().filter(Char::isDigit)
    val phone = profile.stringOrNull("phone_local").orEmpty().filter(Char::isDigit)
    return AuthSession(
        token = accessToken,
        userId = userId,
        authUserId = profile.stringOrNull("auth_user_id") ?: user?.stringOrNull("id"),
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAt = expiresAt,
        email = user?.stringOrNull("email") ?: "$countryCode$phone@phone.quata.app",
        displayName = profile.stringOrNull("display_name")
            ?: profile.stringOrNull("phone_local")
            ?: "Usuario",
    )
}

private fun JsonObject.requiredObject(name: String): JsonObject =
    this[name]?.jsonObject ?: error("Respuesta de autenticación incompleta: $name")

private fun JsonObject.requiredString(name: String): String =
    stringOrNull(name) ?: error("Respuesta de autenticación incompleta: $name")

private fun JsonObject.stringOrNull(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull
