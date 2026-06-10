package com.quata.feature.profile.data

import android.content.Context
import com.quata.R
import com.quata.core.common.mapFailureToUserFacing
import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.media.ImageUploadOptions
import com.quata.core.media.MediaUploadOptimizer
import com.quata.core.session.SessionManager
import com.quata.data.supabase.CommunityProfile
import com.quata.feature.profile.domain.EmergencyContactCandidate
import com.quata.feature.profile.domain.ProfileEditConfig
import com.quata.feature.profile.domain.ProfileEditModel
import com.quata.feature.profile.domain.ProfileRepository
import com.quata.feature.profile.domain.ProfileUpdate
import com.quata.feature.profile.domain.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ProfileRepositoryImpl(
    private val remote: ProfileRemoteDataSource,
    private val sessionManager: SessionManager,
    private val context: Context,
    private val mediaUploadOptimizer: MediaUploadOptimizer
) : ProfileRepository {
    private val emergencyMessageStore = EmergencyMessageStore(context)

    override fun observeProfileEditModel(): Flow<Result<ProfileEditModel>> {
        if (AppConfig.USE_MOCK_BACKEND) {
            return flowOf(getMockProfileEditModel())
        }
        val session = sessionManager.currentSession()
            ?: return flowOf(Result.failure(IllegalStateException("No hay sesion activa")))
        val storedEmergencyMessage = emergencyMessageStore.get(session.userId)
        return combine(
            remote.observeEmergencyContactIds(session.userId),
            remote.observeProfile(session.userId),
            remote.observeEmergencyCandidates()
        ) { contactIds, profile, candidates ->
            val userProfile = profile?.toProfile(session.displayName, contactIds, storedEmergencyMessage)
                ?: error("Perfil no encontrado")
            ProfileEditModel(
                profile = userProfile,
                config = buildProfileConfig(
                    emergencyCandidates = candidates
                        .filterNot { it.id == session.userId }
                        .map { it.toEmergencyCandidate() }
                )
            )
        }
            .map { model -> Result.success(model) }
            .catch { error ->
                emit(Result.failure<ProfileEditModel>(error).mapFailureToUserFacing(context, R.string.error_load_profile))
            }
    }

    override suspend fun getProfileEditModel(): Result<ProfileEditModel> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            return@runCatching getMockProfileEditModel().getOrThrow()
        }

        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val contactIds = remote.getEmergencyContactIds(session.userId)
        val storedEmergencyMessage = emergencyMessageStore.get(session.userId)
        val profile = remote.getProfile(session.userId)?.toProfile(session.displayName, contactIds, storedEmergencyMessage)
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
            val avatarUrl = uploadAvatarIfNeeded(session.userId, update.avatarUri)
            val remoteUpdate = update.copy(avatarUri = avatarUrl)
            remote.saveProfile(session.userId, remoteUpdate.toRemotePatch())
            remote.saveEmergencyContacts(session.userId, update.emergencyContactIds)
            emergencyMessageStore.save(
                profileId = session.userId,
                message = update.emergencyMessage,
                isDefault = update.emergencyMessageIsDefault
            )
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

    override fun defaultEmergencyMessage(displayName: String): String =
        context.getString(
            R.string.sos_default_message,
            displayName.ifBlank { context.getString(R.string.user_fallback_name) }
        )

    override fun changesSavedMessage(): String =
        context.getString(R.string.profile_changes_saved)

    private fun getMockProfileEditModel(): Result<ProfileEditModel> = runCatching {
        ProfileEditModel(
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

    private fun CommunityProfile.toProfile(
        fallbackName: String,
        emergencyContactIds: List<String>,
        storedEmergencyMessage: StoredEmergencyMessage?
    ): UserProfile {
        val displayName = display_name?.takeIf { it.isNotBlank() }
            ?: nombre?.takeIf { it.isNotBlank() }
            ?: fallbackName
        val defaultMessage = defaultEmergencyMessage(displayName)
        val emergencyMessageIsDefault = storedEmergencyMessage?.isDefault ?: true
        return UserProfile(
            displayName = displayName,
            neighborhood = neighborhood?.takeIf { it.isNotBlank() } ?: barrio.orEmpty(),
            countryCode = country_code?.takeIf { it.isNotBlank() } ?: code?.takeIf { it.isNotBlank() } ?: "240",
            phone = phone_local?.takeIf { it.isNotBlank() } ?: phone.orEmpty(),
            avatarUri = avatar_url.cleanValue() ?: avatar.cleanValue(),
            selectedSecretQuestion = secret_question.orEmpty(),
            emergencyContactIds = emergencyContactIds,
            emergencyMessage = storedEmergencyMessage
                ?.takeUnless { it.isDefault }
                ?.message
                ?: defaultMessage,
            emergencyMessageIsDefault = emergencyMessageIsDefault
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
            put("avatar_url", avatarUri.cleanValue())
            put("secret_question", secretQuestion)
            if (secretAnswer.isNotBlank()) put("secret_answer", secretAnswer)
        }

    private suspend fun uploadAvatarIfNeeded(profileId: String, avatarUri: String?): String? {
        if (!mediaUploadOptimizer.isLocalUploadUri(avatarUri)) return avatarUri
        val media = mediaUploadOptimizer.prepareImageUpload(
            uriString = avatarUri ?: return null,
            fallbackMimeType = "image/jpeg",
            fallbackFileNameBase = "avatar",
            options = ImageUploadOptions.Avatar
        )
        return remote.uploadAvatar(
            profileId = profileId,
            bytes = media.bytes,
            extension = media.extension,
            mimeType = media.mimeType
        ).publicUrl ?: error("Supabase no devolvio URL de avatar")
    }

    private fun buildProfileConfig(
        emergencyCandidates: List<EmergencyContactCandidate>
    ): ProfileEditConfig =
        ProfileEditConfig(
            countryPrefixes = context.countryPrefixOptions(),
            secretQuestions = context.profileSecretQuestionOptions(),
            emergencyCandidates = emergencyCandidates
        )

    private fun String?.cleanValue(): String? =
        this?.trim()?.takeIf { it.isNotBlank() }
}
