package com.quata.feature.auth.data

import android.content.Context
import com.quata.R
import com.quata.core.auth.GoogleAuthHelper
import com.quata.core.common.mapFailureToUserFacing
import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.model.AuthSession
import com.quata.core.session.SessionManager
import com.quata.data.supabase.CommunityProfile
import com.quata.data.supabase.CommunityProfileCreate
import com.quata.data.supabase.SupabaseCommunityApi
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.auth.domain.PasswordRecoveryQuestion
import com.quata.feature.auth.domain.RegisterAccountRequest
import java.security.MessageDigest

class AuthRepositoryImpl(
    private val appContext: Context,
    private val supabaseApi: SupabaseCommunityApi,
    private val sessionManager: SessionManager,
    private val googleAuthHelper: GoogleAuthHelper
) : AuthRepository {

    override suspend fun login(countryCode: String, phone: String, password: String): Result<AuthSession> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            val profile = MockData.profileByPhone(countryCode, phone) ?: error("Telefono no registrado")
            if (!MockData.validatePassword(profile, password)) error("Contrasena incorrecta")
            profile.toSession(token = "mock-phone-token")
        } else {
            val auth = supabaseApi.loginWithAuthBridge(countryCode, phone, password)
            auth.toSession()
        }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)
        .onSuccess { sessionManager.setSession(it) }

    override suspend fun register(request: RegisterAccountRequest): Result<AuthSession> = runCatching {
        require(request.displayName.isNotBlank()) { "Introduce tu nombre" }
        require(request.neighborhood.isNotBlank()) { "Introduce tu barrio y comunidad" }
        require(request.phone.isNotBlank()) { "Introduce tu telefono" }
        require(request.password.isNotBlank()) { "Introduce una contrasena" }
        require(request.secretQuestion.isNotBlank()) { "Selecciona una pregunta secreta" }
        require(request.secretAnswer.isNotBlank()) { "Introduce tu respuesta secreta" }

        if (AppConfig.USE_MOCK_BACKEND) {
            if (MockData.profileByPhone(request.countryCode, request.phone) != null) {
                error("Ya existe una cuenta con ese telefono")
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
            val phoneLocal = request.phone.onlyDigits()
            val countryCode = request.countryCode.onlyDigits()
            val phoneE164 = "+$countryCode$phoneLocal"

            val existingProfile = supabaseApi.getProfileByPhoneIdentity(countryCode, phoneLocal)
            if (existingProfile != null) {
                if (existingProfile.matchesPassword(request.password)) {
                    return@runCatching supabaseApi.loginWithAuthBridge(countryCode, phoneLocal, request.password)
                        .toSession(fallbackProfile = existingProfile)
                }
                error("Ya existe una cuenta con ese telefono")
            }

            val profile = supabaseApi.createProfile(
                CommunityProfileCreate(
                    display_name = request.displayName,
                    phone = phoneE164,
                    pass_hash = sha256(request.password),
                    phone_normalized = phoneLocal,
                    country_code = countryCode,
                    phone_local = phoneLocal,
                    phone_e164 = phoneE164,
                    barrio = request.neighborhood,
                    neighborhood = request.neighborhood,
                    code = countryCode,
                    telefono = phoneLocal,
                    nombre = request.displayName,
                    secret_question = request.secretQuestion,
                    secret_answer = request.secretAnswer,
                    pass_plain = request.password
                )
            ) ?: error("No se pudo crear el perfil")

            supabaseApi.loginWithAuthBridge(countryCode, phoneLocal, request.password).toSession(
                fallbackProfile = profile
            )
        }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)
        .onSuccess { sessionManager.setSession(it) }

    override suspend fun getPasswordRecoveryQuestion(countryCode: String, phone: String): Result<PasswordRecoveryQuestion?> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.profileByPhone(countryCode, phone)?.let {
                PasswordRecoveryQuestion(userId = it.id, secretQuestion = it.secretQuestion)
            }
        } else {
            supabaseApi.getProfileByPhoneLocal(phone)?.let { profile ->
                profile.secret_question?.takeIf { it.isNotBlank() }?.let {
                    PasswordRecoveryQuestion(userId = profile.id, secretQuestion = it)
                }
            }
        }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun resetPassword(
        countryCode: String,
        phone: String,
        secretAnswer: String,
        newPassword: String
    ): Result<Unit> = runCatching {
        require(newPassword.isNotBlank()) { "Introduce una nueva contrasena" }
        if (AppConfig.USE_MOCK_BACKEND) {
            val profile = MockData.profileByPhone(countryCode, phone) ?: error("Telefono no registrado")
            if (!MockData.validateSecretAnswer(profile, secretAnswer)) {
                error("La respuesta secreta no es correcta")
            }
            MockData.updatePassword(profile.id, newPassword)
        } else {
            val profile = supabaseApi.getProfileByPhoneLocal(phone) ?: error("Telefono no registrado")
            if (!profile.secret_answer.orEmpty().trim().equals(secretAnswer.trim(), ignoreCase = true)) {
                error("La respuesta secreta no es correcta")
            }
            supabaseApi.updateProfile(
                profileId = profile.id,
                patch = mapOf(
                    "pass_hash" to sha256(newPassword),
                    "pass_plain" to newPassword
                )
            )
        }
        Unit
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

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

    private fun CommunityProfile.toSession(token: String): AuthSession =
        AuthSession(
            token = token,
            userId = id,
            email = phoneEmail(country_code ?: code ?: "", phone_local ?: phone ?: telefono ?: ""),
            displayName = display_name?.takeIf { it.isNotBlank() }
                ?: nombre?.takeIf { it.isNotBlank() }
                ?: phone_local?.takeIf { it.isNotBlank() }
                ?: "Usuario"
        )

    private fun com.quata.data.supabase.SupabaseAuthBridgeResponse.toSession(
        fallbackProfile: CommunityProfile? = null
    ): AuthSession {
        val profileId = profile.id
        val countryCode = profile.country_code ?: fallbackProfile?.country_code ?: fallbackProfile?.code ?: ""
        val phoneLocal = profile.phone_local ?: fallbackProfile?.phone_local ?: fallbackProfile?.phone ?: fallbackProfile?.telefono ?: ""
        val displayName = profile.display_name
            ?.takeIf { it.isNotBlank() }
            ?: fallbackProfile?.display_name?.takeIf { it.isNotBlank() }
            ?: fallbackProfile?.nombre?.takeIf { it.isNotBlank() }
            ?: phoneLocal.takeIf { it.isNotBlank() }
            ?: "Usuario"
        return AuthSession(
            token = session.access_token,
            userId = profileId,
            authUserId = profile.auth_user_id ?: user.id,
            accessToken = session.access_token,
            refreshToken = session.refresh_token,
            expiresAt = session.expires_at ?: session.expires_in?.let { System.currentTimeMillis() / 1000L + it },
            email = user.email ?: phoneEmail(countryCode, phoneLocal),
            displayName = displayName
        )
    }

    private fun CommunityProfile.matchesPassword(password: String): Boolean =
        pass_hash.equals(sha256(password), ignoreCase = true) || pass_plain == password

    private fun phoneEmail(countryCode: String, phone: String): String =
        "${countryCode.onlyDigits()}${phone.onlyDigits()}@phone.quata.app"

    private fun String.onlyDigits(): String = filter(Char::isDigit)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
