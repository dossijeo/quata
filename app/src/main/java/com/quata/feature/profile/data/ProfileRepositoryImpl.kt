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
import com.quata.data.supabase.SupabaseCacheMode
import com.quata.feature.profile.domain.EmergencyContactCandidate
import com.quata.feature.profile.domain.ProfileEditConfig
import com.quata.feature.profile.domain.ProfileEditModel
import com.quata.feature.profile.domain.ProfileRepository
import com.quata.feature.profile.domain.ProfileUpdate
import com.quata.feature.profile.domain.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

class ProfileRepositoryImpl(
    private val remote: ProfileRemoteDataSource,
    private val sessionManager: SessionManager,
    private val context: Context,
    private val mediaUploadOptimizer: MediaUploadOptimizer
) : ProfileRepository {
    private val emergencyMessageStore = EmergencyMessageStore(context)
    private val emergencyContactsStore = EmergencyContactsStore(context)

    override fun observeProfileEditModel(): Flow<Result<ProfileEditModel>> {
        if (AppConfig.USE_MOCK_BACKEND) {
            return flowOf(getMockProfileEditModel())
        }
        val session = sessionManager.currentSession()
            ?: return flowOf(Result.failure(IllegalStateException("No hay sesion activa")))
        val storedEmergencyMessage = emergencyMessageStore.get(session.userId)
        val contactIdsFlow = flow {
            emit(emergencyContactsStore.get(session.userId))
            val networkContactIds = remote.getEmergencyContactIds(
                profileId = session.userId,
                cacheMode = SupabaseCacheMode.NETWORK_ONLY
            )
            emergencyContactsStore.save(session.userId, networkContactIds)
            emit(networkContactIds)
        }
            .catch { emit(emergencyContactsStore.get(session.userId)) }
            .distinctUntilChanged()
        return combine(
            contactIdsFlow,
            remote.observeProfile(session.userId),
            remote.observeEmergencyCandidates()
        ) { contactIds, profile, candidates ->
            val userProfile = profile?.toProfile(session.displayName, contactIds, storedEmergencyMessage)
                ?: error("Perfil no encontrado")
            ProfileEditModel(
                profile = userProfile,
                config = buildProfileConfig(
                    emergencyCandidates = buildEmergencyCandidates(
                        currentProfileId = session.userId,
                        selectedContactIds = contactIds,
                        directoryProfiles = candidates
                    )
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
        val contactIds = getEmergencyContactIdsOfflineFirst(session.userId)
        val storedEmergencyMessage = emergencyMessageStore.get(session.userId)
        val profile = remote.getProfile(session.userId)?.toProfile(session.displayName, contactIds, storedEmergencyMessage)
            ?: error("Perfil no encontrado")
        val candidates = buildEmergencyCandidates(
            currentProfileId = session.userId,
            selectedContactIds = contactIds,
            directoryProfiles = remote.getEmergencyCandidates()
        )
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
            emergencyContactsStore.save(session.userId, update.emergencyContactIds)
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

    override suspend fun saveEmergencySettings(
        contactIds: List<String>,
        message: String,
        messageIsDefault: Boolean
    ): Result<Unit> = runCatching {
        val normalizedContactIds = contactIds.distinct().take(5)
        val session = sessionManager.currentSession()
        val profileId = session?.userId ?: MockData.currentUser.id

        if (!AppConfig.USE_MOCK_BACKEND) {
            session ?: error("No hay sesion activa")
            remote.saveEmergencyContacts(session.userId, normalizedContactIds)
            emergencyContactsStore.save(session.userId, normalizedContactIds)
            emergencyMessageStore.save(
                profileId = session.userId,
                message = message,
                isDefault = messageIsDefault
            )
            return@runCatching Unit
        }

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

    override fun defaultEmergencyMessage(displayName: String): String =
        context.getString(
            R.string.sos_default_message,
            displayName.ifBlank { context.getString(R.string.user_fallback_name) }
        )

    override fun changesSavedMessage(): String =
        context.getString(R.string.profile_changes_saved)

    override fun emergencyContactsSavedMessage(): String =
        context.getString(R.string.profile_emergency_contacts_updated)

    private suspend fun getEmergencyContactIdsOfflineFirst(profileId: String): List<String> {
        val cachedContactIds = emergencyContactsStore.get(profileId)
        val networkContactIds = runCatching {
            withTimeoutOrNull(EmergencyContactsNetworkTimeoutMillis) {
                remote.getEmergencyContactIds(profileId, cacheMode = SupabaseCacheMode.NETWORK_ONLY)
            }
        }.getOrNull()
        if (networkContactIds != null) {
            emergencyContactsStore.save(profileId, networkContactIds)
            return networkContactIds
        }
        return cachedContactIds.ifEmpty {
            runCatching {
                remote.getEmergencyContactIds(profileId, cacheMode = SupabaseCacheMode.CACHE_FIRST)
            }.onSuccess { contactIds ->
                emergencyContactsStore.save(profileId, contactIds)
            }.getOrDefault(emptyList())
        }
    }

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
        val phoneParts = profilePhoneParts()
        return UserProfile(
            displayName = displayName,
            neighborhood = neighborhood?.takeIf { it.isNotBlank() } ?: barrio.orEmpty(),
            countryCode = phoneParts.first,
            phone = phoneParts.second,
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

    private fun CommunityProfile.profilePhoneParts(): Pair<String, String> {
        val explicitCountryCode = (country_code?.takeIf { it.isNotBlank() }
            ?: code?.takeIf { it.isNotBlank() })
            ?.filter(Char::isDigit)
        val e164Digits = phone_e164
            ?.takeIf { it.isNotBlank() }
            ?.filter(Char::isDigit)
        val localDigits = phone_local
            ?.takeIf { it.isNotBlank() }
            ?.filter(Char::isDigit)
            ?: telefono?.takeIf { it.isNotBlank() }?.filter(Char::isDigit)
        if (!explicitCountryCode.isNullOrBlank() && !localDigits.isNullOrBlank()) {
            return explicitCountryCode to localDigits
        }
        if (!e164Digits.isNullOrBlank() && !localDigits.isNullOrBlank() && e164Digits.endsWith(localDigits)) {
            val country = e164Digits.removeSuffix(localDigits)
            if (country.isNotBlank()) return country to localDigits
        }
        val fullPhoneDigits = phone?.takeIf { it.isNotBlank() }?.filter(Char::isDigit)
        if (!fullPhoneDigits.isNullOrBlank() && !localDigits.isNullOrBlank() && fullPhoneDigits.endsWith(localDigits)) {
            val country = fullPhoneDigits.removeSuffix(localDigits)
            if (country.isNotBlank()) return country to localDigits
        }
        return (
            explicitCountryCode
                ?: "240"
            ) to (
            localDigits
                ?: fullPhoneDigits
                ?: ""
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

    private suspend fun buildEmergencyCandidates(
        currentProfileId: String,
        selectedContactIds: List<String>,
        directoryProfiles: List<CommunityProfile>
    ): List<EmergencyContactCandidate> {
        val orderedProfiles = LinkedHashMap<String, CommunityProfile>()
        directoryProfiles
            .filterNot { it.id == currentProfileId }
            .forEach { profile -> orderedProfiles[profile.id] = profile }

        val missingSelectedIds = selectedContactIds
            .distinct()
            .filterNot { it == currentProfileId }
            .filterNot { selectedId -> orderedProfiles.containsKey(selectedId) }

        if (missingSelectedIds.isNotEmpty()) {
            runCatching { remote.getProfiles(missingSelectedIds) }
                .getOrDefault(emptyList())
                .filterNot { it.id == currentProfileId }
                .forEach { profile -> orderedProfiles[profile.id] = profile }
        }

        val candidatesById = LinkedHashMap<String, EmergencyContactCandidate>()
        orderedProfiles.values
            .map { it.toEmergencyCandidate() }
            .forEach { candidate -> candidatesById[candidate.id] = candidate }

        selectedContactIds
            .distinct()
            .filterNot { it == currentProfileId }
            .filterNot { selectedId -> candidatesById.containsKey(selectedId) }
            .forEach { missingId ->
                candidatesById[missingId] = EmergencyContactCandidate(
                    id = missingId,
                    displayName = context.getString(R.string.user_fallback_name),
                    email = "",
                    neighborhood = ""
                )
            }

        return candidatesById.values.toList()
    }

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

private const val EmergencyContactsNetworkTimeoutMillis = 3_500L
