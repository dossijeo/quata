package com.quata.web

import com.quata.core.model.AuthSession

/**
 * Web must not emulate Android's privileged profile creation path. Keeping this decision in a
 * small, testable boundary prevents a future host from sending registration fields to the login
 * bridge and mistaking a local result for an account.
 */
internal const val WebRegistrationUnavailableCode = "web_auth_registration_contract_unavailable"

internal fun webRegistrationUnavailable(): Result<AuthSession> =
    Result.failure(UnsupportedOperationException(WebRegistrationUnavailableCode))

internal const val WebRegistrationUnavailableMessage =
    "El registro todavía no está disponible en Quata Web. Usa una cuenta creada desde el flujo autorizado."
