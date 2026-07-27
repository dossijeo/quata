package com.quata.feature.auth.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Client representation of the versioned, opaque registration Edge contract.
 *
 * This is deliberately limited to fields accepted by the existing endpoint. It neither adds
 * privileged attributes nor exposes registration input through diagnostics.
 */
internal fun buildRegistrationEdgeRequest(
    request: RegisterAccountRequest,
    channel: String,
    clientInstanceId: String,
    idempotencyKey: String,
    challengeToken: String,
): JsonObject = buildJsonObject {
    put("version", 1)
    put("channel", channel)
    put("display_name", request.displayName.trim())
    put("neighborhood", request.neighborhood.trim())
    put("country_code", request.countryCode.filter(Char::isDigit))
    put("phone_local", request.phone.filter(Char::isDigit))
    put("password", request.password)
    put("secret_question", request.secretQuestion.trim())
    put("secret_answer", request.secretAnswer.trim())
    put("client_instance_id", clientInstanceId)
    put("idempotency_key", idempotencyKey)
    put("challenge_token", challengeToken)
}

/** The Edge function returns no account data; acceptance is the only successful response. */
internal fun isRegistrationEdgeAccepted(response: String): Boolean = runCatching {
    Json.parseToJsonElement(response).jsonObject["status"]?.jsonPrimitive?.contentOrNull == "accepted"
}.getOrDefault(false)

/** Default-deny gate for registration launchers. All inputs must be explicitly present. */
internal fun isRegistrationTransportEnabled(
    enabled: Boolean,
    apiKey: String?,
    clientInstanceId: String?,
    challengeToken: String?,
): Boolean = enabled &&
    !apiKey.isNullOrBlank() &&
    !clientInstanceId.isNullOrBlank() &&
    !challengeToken.isNullOrBlank()
