package com.quata.feature.auth.data

import android.content.Context
import com.quata.R
import com.quata.core.auth.GoogleAuthHelper
import com.quata.core.auth.AndroidRegistrationChallengeService
import com.quata.core.auth.RegistrationClientIdentityStore
import com.quata.core.common.mapFailureToUserFacing
import com.quata.core.common.UserFacingException
import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.model.AuthSession
import com.quata.core.session.SessionManager
import com.quata.core.notifications.PushTokenManager
import com.quata.core.moderation.UgcTermsAcceptanceStore
import com.quata.core.preferences.TouchFlowPreferences
import com.quata.data.supabase.CommunityProfile
import com.quata.data.supabase.SupabaseCommunityApi
import com.quata.data.supabase.SupabaseApiException
import com.quata.data.supabase.SupabaseResponseCacheStore
import com.quata.data.supabase.QuataRegistrationRequest
import com.quata.feature.chat.data.ChatAttachmentFileCache
import com.quata.feature.chat.data.SupabaseChatCacheStore
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.auth.domain.PasswordRecoveryQuestion
import com.quata.feature.auth.domain.RegisterAccountRequest
import androidx.core.app.NotificationManagerCompat

class AuthRepositoryImpl(
    private val appContext: Context,
    private val supabaseApi: SupabaseCommunityApi,
    private val sessionManager: SessionManager,
    private val googleAuthHelper: GoogleAuthHelper,
    private val pushTokenManager: PushTokenManager,
    private val registrationChallengeService: AndroidRegistrationChallengeService,
    private val registrationIdentityStore: RegistrationClientIdentityStore
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
            val identity = "$countryCode$phoneLocal"
            val challenge = registrationChallengeService.acquire()
            supabaseApi.requestRegistration(
                QuataRegistrationRequest(
                challenge_token = challenge.token,
                challenge_action = challenge.action,
                client_instance_id = registrationIdentityStore.clientInstanceId(),
                idempotency_key = registrationIdentityStore.idempotencyKey(identity),
                country_code = countryCode,
                phone = phoneLocal,
                password = request.password,
                display_name = request.displayName,
                neighborhood = request.neighborhood,
                secret_question = request.secretQuestion,
                secret_answer = request.secretAnswer
                )
            )
            val session = supabaseApi.loginWithAuthBridge(countryCode, phoneLocal, request.password).toSession()
            registrationIdentityStore.complete(identity)
            session
        }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)
        .onSuccess { sessionManager.setSession(it) }

    override suspend fun getPasswordRecoveryQuestion(countryCode: String, phone: String): Result<PasswordRecoveryQuestion?> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.profileByPhone(countryCode, phone)?.let {
                PasswordRecoveryQuestion(userId = it.id, secretQuestion = it.secretQuestion)
            }
        } else {
            try {
                PasswordRecoveryQuestion(
                    secretQuestion = supabaseApi.getRecoveryQuestionWithAuthBridge(countryCode, phone)
                )
            } catch (error: SupabaseApiException) {
                if (error.responseBody?.contains("recovery_profile_not_found", ignoreCase = true) == true) {
                    null
                } else {
                    throw error
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
            supabaseApi.resetPasswordWithAuthBridge(
                countryCode = countryCode,
                phone = phone,
                secretAnswer = secretAnswer,
                newPassword = newPassword
            )
        }
        Unit
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    suspend fun loginWithGoogle(context: Context): Result<AuthSession> {
        return googleAuthHelper.signIn(context).onSuccess { sessionManager.setSession(it) }
    }

    override suspend fun deactivateAccount(password: String): Result<Unit> = runCatching {
        require(password.isNotBlank()) { appContext.getString(R.string.account_password_required) }
        val profileId = sessionManager.currentSession()?.userId ?: error("No hay sesion activa")
        if (!AppConfig.USE_MOCK_BACKEND) {
            check(performAccountLifecycle("deactivate", password)) {
                "No se pudo desactivar la cuenta"
            }
        }
        clearLocalAccountData(profileId)
        sessionManager.clearSession()
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun deleteAccountData(password: String): Result<Unit> = runCatching {
        require(password.isNotBlank()) { appContext.getString(R.string.account_password_required) }
        val profileId = sessionManager.currentSession()?.userId ?: error("No hay sesion activa")
        if (!AppConfig.USE_MOCK_BACKEND) {
            check(performAccountLifecycle("delete", password)) {
                "No se pudieron eliminar los datos de la cuenta"
            }
        }
        clearLocalAccountData(profileId)
        sessionManager.clearSession()
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun logout() {
        pushTokenManager.unregisterCurrentToken()
        sessionManager.clearSession()
    }

    private suspend fun clearLocalAccountData(profileId: String) {
        SupabaseChatCacheStore(appContext).clearProfile(profileId)
        ChatAttachmentFileCache(appContext).clearProfile(profileId)
        SupabaseResponseCacheStore(appContext).clearAll()
        UgcTermsAcceptanceStore(appContext).clearUser(profileId)
        TouchFlowPreferences(appContext).clear(profileId)
        NotificationManagerCompat.from(appContext).cancelAll()
    }

    private suspend fun performAccountLifecycle(action: String, password: String): Boolean = try {
        supabaseApi.performAccountLifecycle(action, password).ok
    } catch (error: SupabaseApiException) {
        if (error.responseBody?.contains("invalid_password", ignoreCase = true) == true) {
            throw UserFacingException(appContext.getString(R.string.account_password_incorrect), error)
        }
        throw error
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

    private fun phoneEmail(countryCode: String, phone: String): String =
        "${countryCode.onlyDigits()}${phone.onlyDigits()}@phone.quata.app"

    private fun String.onlyDigits(): String = filter(Char::isDigit)

}
