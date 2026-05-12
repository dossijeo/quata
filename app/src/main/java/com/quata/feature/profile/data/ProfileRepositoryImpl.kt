package com.quata.feature.profile.data

import android.content.Context
import com.quata.R
import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.network.supabase.SupabaseProfileDto
import com.quata.core.network.supabase.SupabaseProfileUpdateRequest
import com.quata.core.session.SessionManager
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
        val config = buildProfileConfig()
        if (AppConfig.USE_MOCK_BACKEND) {
            return@runCatching ProfileEditModel(
                profile = buildMockProfile(),
                config = config
            )
        }

        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val profile = remote.getProfile(session.userId)?.toProfile(session.displayName) ?: buildMockProfile()
        val candidates = remote.getEmergencyCandidates().map { it.toEmergencyCandidate() }
        ProfileEditModel(
            profile = profile,
            config = config.copy(
                emergencyCandidates = candidates.ifEmpty { config.emergencyCandidates }
            )
        )
    }

    override suspend fun saveProfile(update: ProfileUpdate): Result<Unit> = runCatching {
        if (!AppConfig.USE_MOCK_BACKEND) {
            val session = sessionManager.currentSession() ?: error("No hay sesion activa")
            remote.saveProfile(session.userId, update.toRemoteRequest())
            return@runCatching
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
    }

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

    private fun SupabaseProfileDto.toProfile(fallbackName: String): UserProfile {
        val displayName = displayName?.takeIf { it.isNotBlank() } ?: fallbackName
        return UserProfile(
            displayName = displayName,
            neighborhood = neighborhood.orEmpty(),
            countryCode = countryCode?.takeIf { it.isNotBlank() } ?: "240",
            phone = phone.orEmpty(),
            avatarUri = avatarUrl,
            selectedSecretQuestion = secretQuestion.orEmpty(),
            emergencyContactIds = emergencyContactIds.orEmpty(),
            emergencyMessage = emergencyMessage?.takeIf { it.isNotBlank() }
                ?: defaultEmergencyMessage(displayName),
            emergencyMessageIsDefault = emergencyMessageIsDefault ?: emergencyMessage.isNullOrBlank()
        )
    }

    private fun SupabaseProfileDto.toEmergencyCandidate(): EmergencyContactCandidate =
        EmergencyContactCandidate(
            id = id,
            displayName = displayName?.takeIf { it.isNotBlank() } ?: email.orEmpty(),
            email = email.orEmpty(),
            neighborhood = neighborhood.orEmpty(),
            phone = phone.orEmpty()
        )

    private fun ProfileUpdate.toRemoteRequest(): SupabaseProfileUpdateRequest =
        SupabaseProfileUpdateRequest(
            displayName = displayName,
            neighborhood = neighborhood,
            countryCode = countryCode,
            phone = phone,
            avatarUrl = avatarUri,
            secretQuestion = secretQuestion,
            secretAnswer = secretAnswer.takeIf { it.isNotBlank() },
            emergencyContactIds = emergencyContactIds,
            emergencyMessage = emergencyMessage,
            emergencyMessageIsDefault = emergencyMessageIsDefault
        )

    private fun defaultEmergencyMessage(displayName: String): String =
        context.getString(
            R.string.sos_default_message,
            displayName.ifBlank { context.getString(R.string.user_fallback_name) }
        )

    private fun buildProfileConfig(): ProfileEditConfig {
        val currentId = sessionManager.currentSession()?.userId ?: MockData.currentUser.id
        return ProfileEditConfig(
            countryPrefixes = context.countryPrefixOptions(),
            secretQuestions = context.profileSecretQuestionOptions(),
            emergencyCandidates = MockData.mockAuthProfiles
                .filterNot { it.id == currentId }
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
    }
}
