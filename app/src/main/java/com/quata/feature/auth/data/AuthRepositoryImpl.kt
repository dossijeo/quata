package com.quata.feature.auth.data

import android.content.Context
import com.quata.core.auth.GoogleAuthHelper
import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.model.AuthSession
import com.quata.core.session.SessionManager
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.auth.domain.PasswordRecoveryQuestion
import com.quata.feature.auth.domain.RegisterAccountRequest

class AuthRepositoryImpl(
    private val remoteDataSource: AuthRemoteDataSource,
    private val sessionManager: SessionManager,
    private val googleAuthHelper: GoogleAuthHelper
) : AuthRepository {

    override suspend fun login(countryCode: String, phone: String, password: String): Result<AuthSession> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            val profile = MockData.profileByPhone(countryCode, phone) ?: error("Teléfono no registrado")
            if (!MockData.validatePassword(profile, password)) error("Contraseña incorrecta")
            profile.toSession(token = "mock-phone-token")
        } else {
            val identity = phoneIdentity(countryCode, phone)
            val response = remoteDataSource.login(identity, password)
            AuthSession(
                token = response.token ?: error("WordPress no devolvió token"),
                userId = response.userNiceName ?: response.userEmail ?: identity,
                email = response.userEmail ?: phoneEmail(countryCode, phone),
                displayName = response.userDisplayName ?: response.userNiceName ?: identity
            )
        }
    }.onSuccess { sessionManager.setSession(it) }

    override suspend fun register(request: RegisterAccountRequest): Result<AuthSession> = runCatching {
        require(request.displayName.isNotBlank()) { "Introduce tu nombre" }
        require(request.neighborhood.isNotBlank()) { "Introduce tu barrio y comunidad" }
        require(request.phone.isNotBlank()) { "Introduce tu teléfono" }
        require(request.password.isNotBlank()) { "Introduce una contraseña" }
        require(request.secretQuestion.isNotBlank()) { "Selecciona una pregunta secreta" }
        require(request.secretAnswer.isNotBlank()) { "Introduce tu respuesta secreta" }

        if (AppConfig.USE_MOCK_BACKEND) {
            if (MockData.profileByPhone(request.countryCode, request.phone) != null) {
                error("Ya existe una cuenta con ese teléfono")
            }
            MockData.createProfile(
                displayName = request.displayName,
                neighborhood = request.neighborhood,
                countryCode = request.countryCode,
                phone = request.phone,
                password = request.password,
                secretQuestion = request.secretQuestion,
                secretAnswer = request.secretAnswer
            ).toSession(token = "mock-register-token")
        } else {
            val response = remoteDataSource.register(phoneEmail(request.countryCode, request.phone), request.password, request.displayName)
            AuthSession(
                token = response.token ?: "",
                userId = response.id ?: response.email ?: phoneIdentity(request.countryCode, request.phone),
                email = response.email ?: phoneEmail(request.countryCode, request.phone),
                displayName = response.displayName ?: request.displayName
            )
        }
    }.onSuccess { sessionManager.setSession(it) }

    override suspend fun getPasswordRecoveryQuestion(countryCode: String, phone: String): Result<PasswordRecoveryQuestion?> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.profileByPhone(countryCode, phone)?.let {
                PasswordRecoveryQuestion(userId = it.id, secretQuestion = it.secretQuestion)
            }
        } else {
            null
        }
    }

    override suspend fun resetPassword(
        countryCode: String,
        phone: String,
        secretAnswer: String,
        newPassword: String
    ): Result<Unit> = runCatching {
        require(newPassword.isNotBlank()) { "Introduce una nueva contraseña" }
        if (AppConfig.USE_MOCK_BACKEND) {
            val profile = MockData.profileByPhone(countryCode, phone) ?: error("Teléfono no registrado")
            if (!MockData.validateSecretAnswer(profile, secretAnswer)) {
                error("La respuesta secreta no es correcta")
            }
            MockData.updatePassword(profile.id, newPassword)
        } else {
            error("Recuperación de contraseña pendiente de conectar con Supabase")
        }
    }

    override suspend fun loginWithGoogle(context: Context): Result<AuthSession> {
        return googleAuthHelper.signIn(context).onSuccess { sessionManager.setSession(it) }
    }

    override suspend fun logout() {
        sessionManager.clearSession()
    }

    private fun MockData.MockUserProfile.toSession(token: String): AuthSession =
        AuthSession(
            token = token,
            userId = id,
            email = email,
            displayName = displayName
        )

    private fun phoneIdentity(countryCode: String, phone: String): String =
        "+${countryCode.onlyDigits()}${phone.onlyDigits()}"

    private fun phoneEmail(countryCode: String, phone: String): String =
        "${countryCode.onlyDigits()}${phone.onlyDigits()}@phone.quata.app"

    private fun String.onlyDigits(): String = filter(Char::isDigit)
}
