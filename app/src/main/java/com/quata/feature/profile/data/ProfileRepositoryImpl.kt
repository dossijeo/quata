package com.quata.feature.profile.data

import android.content.Context
import com.quata.R
import com.quata.core.common.mapFailureToUserFacing
import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.media.MediaUploadOptimizer
import com.quata.core.session.SessionManager
import com.quata.feature.profile.domain.EmergencyContactCandidate
import com.quata.feature.profile.domain.ProfileEditConfig
import com.quata.feature.profile.domain.ProfileEditModel
import com.quata.feature.profile.domain.ProfileRepository
import com.quata.feature.profile.domain.ProfileUpdate
import com.quata.feature.profile.domain.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ProfileRepositoryImpl(
    private val remote: ProfileRemoteDataSource,
    private val sessionManager: SessionManager,
    private val context: Context,
    private val mediaUploadOptimizer: MediaUploadOptimizer
) : ProfileRepository {
    private val emergencyMessageStore = EmergencyMessageStore(context)
    private val emergencyContactsStore = EmergencyContactsStore(context)
    private val portableRepository: ProfileRepository by lazy {
        KmpProfileRepository(
            remote = AndroidProfileRemoteGateway(remote, remote.api),
            sessions = AndroidProfileSessionProvider(sessionManager),
            avatarUploader = AndroidProfileAvatarUploader(remote, mediaUploadOptimizer),
            emergencyMessages = AndroidProfileEmergencyMessageStore(context),
            emergencyContacts = AndroidProfileEmergencyContactsStore(context),
            catalog = AndroidProfilePresentationCatalog(context)
        )
    }

    override fun observeProfileEditModel(): Flow<Result<ProfileEditModel>> {
        if (AppConfig.USE_MOCK_BACKEND) {
            return flowOf(getMockProfileEditModel())
        }
        return portableRepository.observeProfileEditModel().map {
            it.mapFailureToUserFacing(context, R.string.error_load_profile)
        }
    }

    override suspend fun getProfileEditModel(): Result<ProfileEditModel> {
        if (!AppConfig.USE_MOCK_BACKEND) {
            return portableRepository.getProfileEditModel()
                .mapFailureToUserFacing(context, R.string.error_load_profile)
        }
        return getMockProfileEditModel().mapFailureToUserFacing(context, R.string.error_load_profile)
    }

    override suspend fun saveProfile(update: ProfileUpdate): Result<Unit> {
        if (!AppConfig.USE_MOCK_BACKEND) {
            return portableRepository.saveProfile(update)
                .mapFailureToUserFacing(context, R.string.profile_save_error)
        }
        return runCatching {
            val session = sessionManager.currentSession()
            val profileId = session?.userId ?: MockData.currentUser.id
            emergencyMessageStore.save(
                profileId = profileId,
                message = update.emergencyMessage,
                isDefault = update.emergencyMessageIsDefault
            )
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
    }

    override suspend fun saveEmergencySettings(
        contactIds: List<String>,
        message: String,
        messageIsDefault: Boolean
    ): Result<Unit> {
        val normalizedContactIds = contactIds.distinct().take(5)

        if (!AppConfig.USE_MOCK_BACKEND) {
            return portableRepository.saveEmergencySettings(normalizedContactIds, message, messageIsDefault)
                .mapFailureToUserFacing(context, R.string.profile_save_error)
        }

        return runCatching {
            val session = sessionManager.currentSession()
            val profileId = session?.userId ?: MockData.currentUser.id

            emergencyMessageStore.save(
                profileId = profileId,
                message = message,
                isDefault = messageIsDefault
            )

            MockData.profileById(profileId)?.let { profile ->
                MockData.updateProfile(
                    profileId = profileId,
                    displayName = profile.displayName,
                    neighborhood = profile.neighborhood,
                    countryCode = profile.countryCode,
                    phone = profile.phone,
                    avatarUrl = profile.avatarUrl,
                    secretQuestion = profile.secretQuestion,
                    secretAnswer = "",
                    emergencyContactIds = normalizedContactIds,
                    emergencyMessage = message,
                    emergencyMessageIsDefault = messageIsDefault
                )
            }
            Unit
        }.mapFailureToUserFacing(context, R.string.profile_save_error)
    }

    override fun defaultEmergencyMessage(displayName: String): String =
        AndroidProfilePresentationCatalog(context).defaultEmergencyMessage(displayName)

    override fun changesSavedMessage(): String =
        AndroidProfilePresentationCatalog(context).changesSavedMessage()

    override fun emergencyContactsSavedMessage(): String =
        AndroidProfilePresentationCatalog(context).emergencyContactsSavedMessage()

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
        val profileId = source.id
        val storedEmergencyMessage = emergencyMessageStore.get(profileId)
        val emergencyMessageIsDefault = storedEmergencyMessage?.isDefault ?: source.emergencyMessageIsDefault
        return UserProfile(
            displayName = displayName,
            neighborhood = source.neighborhood,
            countryCode = source.countryCode,
            phone = source.phone,
            avatarUri = source.avatarUrl,
            selectedSecretQuestion = source.secretQuestion,
            emergencyContactIds = source.emergencyContactIds,
            emergencyMessage = storedEmergencyMessage
                ?.takeUnless { it.isDefault }
                ?.message
                ?: source.emergencyMessage
                ?: defaultEmergencyMessage(displayName),
            emergencyMessageIsDefault = emergencyMessageIsDefault
        )
    }

    private fun buildProfileConfig(
        emergencyCandidates: List<EmergencyContactCandidate>
    ): ProfileEditConfig =
        ProfileEditConfig(
            countryPrefixes = context.countryPrefixOptions(),
            secretQuestions = context.profileSecretQuestionOptions(),
            emergencyCandidates = emergencyCandidates
        )
}
