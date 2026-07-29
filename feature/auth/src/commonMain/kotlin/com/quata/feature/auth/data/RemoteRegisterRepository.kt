package com.quata.feature.auth.data

import com.quata.core.model.AuthSession
import com.quata.feature.auth.domain.LoginRepository
import com.quata.feature.auth.domain.RegisterAccountRequest
import com.quata.feature.auth.domain.RegisterRepository

/** Platform transports execute the protected registration request, including its challenge. */
fun interface RegisterTransport {
    suspend fun register(request: RegisterAccountRequest)
}

/**
 * Shared registration workflow. A successful backend acceptance is followed by the normal
 * authentication bridge, so callers receive a real session rather than a synthetic success.
 */
class RemoteRegisterRepository(
    private val transport: RegisterTransport,
    private val loginRepository: LoginRepository,
) : RegisterRepository {
    override suspend fun register(request: RegisterAccountRequest): Result<AuthSession> = runCatching {
        require(request.displayName.isNotBlank()) { "Introduce tu nombre" }
        require(request.neighborhood.isNotBlank()) { "Introduce tu barrio" }
        require(request.countryCode.any(Char::isDigit)) { "Introduce un prefijo válido" }
        require(request.phone.any(Char::isDigit)) { "Introduce tu teléfono" }
        require(request.password.isNotBlank()) { "Introduce tu contraseña" }
        require(request.secretQuestion.isNotBlank()) { "Selecciona una pregunta secreta" }
        require(request.secretAnswer.isNotBlank()) { "Introduce tu respuesta secreta" }
        transport.register(request)
        loginRepository.login(request.countryCode, request.phone, request.password).getOrThrow()
    }
}
