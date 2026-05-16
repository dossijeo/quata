package com.quata.feature.profile.data

import android.content.Context
import com.quata.R
import com.quata.core.common.mapFailureToUserFacing
import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.session.SessionManager
import com.quata.data.supabase.CommunityProfile
import com.quata.feature.profile.domain.EmergencyContactCandidate
import com.quata.feature.profile.domain.ProfileEditConfig
import com.quata.feature.profile.domain.ProfileEditModel
import com.quata.feature.profile.domain.ProfileRepository
import com.quata.feature.profile.domain.ProfileUpdate
import com.quata.feature.profile.domain.UserProfile

class ProfileRepositoryImpl(
    private val remote: ProfileRemoteDataSource,
    private val sessionManager: SessionManager,
    private val context: Context
) : ProfileRepository {
    override suspend fun getProfileEditModel(): Result<ProfileEditModel> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            return@runCatching ProfileEditModel(
                profile = buildMockProfile(),
                config = buildProfileConfig(
                    emergencyCandidates = MockData.mockAuthProfiles
                        .filterNot { it.id == (sessionManager.currentSession()?.userId ?: MockData.currentUser.id) }
                        .map {
                            EmergencyContactCandidate(
                                id = it.id,
                                displayName = it.displayName,
                                email = it.email,
                                neighborhood = it.neighborhood,
                                phone = it.phone
                            )
                        }
                )
            )
        }

        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val contactIds = remote.getEmergencyContactIds(session.userId)
        val profile = remote.getProfile(session.userId)?.toProfile(session.displayName, contactIds)
            ?: error("Perfil no encontrado")
        val candidates = remote.getEmergencyCandidates()
            .filterNot { it.id == session.userId }
            .map { it.toEmergencyCandidate() }
        ProfileEditModel(
            profile = profile,
            config = buildProfileConfig(emergencyCandidates = candidates)
        )
    }.mapFailureToUserFacing(context, R.string.error_load_profile)

    override suspend fun saveProfile(update: ProfileUpdate): Result<Unit> = runCatching {
        if (!AppConfig.USE_MOCK_BACKEND) {
            val session = sessionManager.currentSession() ?: error("No hay sesion activa")
            remote.saveProfile(session.userId, update.toRemotePatch())
            remote.saveEmergencyContacts(session.userId, update.emergencyContactIds)
            sessionManager.setSession(session.copy(displayName = update.displayName))
            return@runCatching Unit
        }

        val session = sessionManager.currentSession()
        val profileId = session?.userId ?: MockData.currentUser.id
        MockData.updateProfile(
            profileId = profileId,
            displayName = update.displayName,
            neighborhood = update.neighborhood,
            countryCode = update.countryCode,
            phone = update.phone,
            avatarUrl = update.avatarUri,
            secretQuestion = update.secretQuestion,
            secretAnswer = update.secretAnswer,
            emergencyContactIds = update.emergencyContactIds,
            emergencyMessage = update.emergencyMessage,
            emergencyMessageIsDefault = update.emergencyMessageIsDefault
        )
        session?.let { sessionManager.setSession(it.copy(displayName = update.displayName)) }
        Unit
    }.mapFailureToUserFacing(context, R.string.profile_save_error)

    private fun buildMockProfile(): UserProfile {
        val session = sessionManager.currentSession()
        val source = MockData.profileById(session?.userId ?: MockData.currentUser.id)
            ?: MockData.profileById(MockData.currentUser.id)
            ?: MockData.mockAuthProfiles.first()
        val displayName = source.displayName
        return UserProfile(
            displayName = displayName,
            neighborhood = source.neighborhood,
            countryCode = source.countryCode,
            phone = source.phone,
            avatarUri = source.avatarUrl,
            selectedSecretQuestion = source.secretQuestion,
            emergencyContactIds = source.emergencyContactIds,
            emergencyMessage = source.emergencyMessage ?: defaultEmergencyMessage(displayName),
            emergencyMessageIsDefault = source.emergencyMessageIsDefault
        )
    }

    private fun CommunityProfile.toProfile(fallbackName: String, emergencyContactIds: List<String>): UserProfile {
        val displayName = display_name?.takeIf { it.isNotBlank() }
            ?: nombre?.takeIf { it.isNotBlank() }
            ?: fallbackName
        return UserProfile(
            displayName = displayName,
            neighborhood = neighborhood?.takeIf { it.isNotBlank() } ?: barrio.orEmpty(),
            countryCode = country_code?.takeIf { it.isNotBlank() } ?: code?.takeIf { it.isNotBlank() } ?: "240",
            phone = phone_local?.takeIf { it.isNotBlank() } ?: phone.orEmpty(),
            avatarUri = avatar_url ?: avatar,
            selectedSecretQuestion = secret_question.orEmpty(),
            emergencyContactIds = emergencyContactIds,
            emergencyMessage = defaultEmergencyMessage(displayName),
            emergencyMessageIsDefault = true
        )
    }

    private fun CommunityProfile.toEmergencyCandidate(): EmergencyContactCandidate =
        EmergencyContactCandidate(
            id = id,
            displayName = display_name?.takeIf { it.isNotBlank() }
                ?: nombre?.takeIf { it.isNotBlank() }
                ?: phone_local.orEmpty(),
            email = "${country_code.orEmpty()}${phone_local.orEmpty()}@phone.quata.app",
            neighborhood = neighborhood?.takeIf { it.isNotBlank() } ?: barrio.orEmpty(),
            phone = phone_local?.takeIf { it.isNotBlank() } ?: phone.orEmpty()
        )

    private fun ProfileUpdate.toRemotePatch(): Map<String, String?> =
        buildMap {
            put("display_name", displayName)
            put("nombre", displayName)
            put("neighborhood", neighborhood)
            put("barrio", neighborhood)
            put("country_code", countryCode)
            put("code", countryCode)
            put("phone_local", phone)
            put("phone", "+${countryCode.filter(Char::isDigit)}${phone.filter(Char::isDigit)}")
            put("telefono", phone)
            put("avatar_url", avatarUri)
            put("secret_question", secretQuestion)
            if (secretAnswer.isNotBlank()) put("secret_answer", secretAnswer)
        }

    private fun defaultEmergencyMessage(displayName: String): String =
        context.getString(
            R.string.sos_default_message,
            displayName.ifBlank { context.getString(R.string.user_fallback_name) }
        )

    private fun buildProfileConfig(
        emergencyCandidates: List<EmergencyContactCandidate>
    ): ProfileEditConfig =
        ProfileEditConfig(
            countryPrefixes = context.countryPrefixOptions(),
            secretQuestions = context.profileSecretQuestionOptions(),
            emergencyCandidates = emergencyCandidates
        )
}
