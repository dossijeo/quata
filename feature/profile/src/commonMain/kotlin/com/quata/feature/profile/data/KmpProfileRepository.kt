package com.quata.feature.profile.data

import com.quata.core.model.CountryPrefix
import com.quata.feature.profile.domain.EmergencyContactCandidate
import com.quata.feature.profile.domain.ProfileEditConfig
import com.quata.feature.profile.domain.ProfileEditModel
import com.quata.feature.profile.domain.ProfileRepository
import com.quata.feature.profile.domain.ProfileUpdate
import com.quata.feature.profile.domain.SecretQuestionOption
import com.quata.feature.profile.domain.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/** Values that remain localized/platform-owned while Profile rules stay shared. */
interface ProfilePresentationCatalog {
    fun countryPrefixes(): List<CountryPrefix>
    fun secretQuestions(): List<SecretQuestionOption>
    fun fallbackUserName(): String
    fun defaultEmergencyMessage(displayName: String): String
    fun changesSavedMessage(): String
    fun emergencyContactsSavedMessage(): String
}

/**
 * Shared Profile repository. Transports, sessions, local stores, image handling and localized
 * text are injected so commonMain does not know about Android Context, Supabase or resources.
 */
class KmpProfileRepository(
    private val remote: ProfileRemoteGateway,
    private val sessions: ProfileSessionProvider,
    private val avatarUploader: ProfileAvatarUploader,
    private val emergencyMessages: ProfileEmergencyMessageStore,
    private val emergencyContacts: ProfileEmergencyContactsStore,
    private val catalog: ProfilePresentationCatalog
) : ProfileRepository {
    override fun observeProfileEditModel(): Flow<Result<ProfileEditModel>> {
        val session = sessions.currentSession()
            ?: return flow { emit(Result.failure(IllegalStateException("No hay sesion activa"))) }
        val storedMessage = emergencyMessages.get(session.profileId)
        val contactIds = observeEmergencyContactIds(session.profileId)
        return combine(contactIds, remote.observeProfile(session.profileId), remote.observeEmergencyCandidates()) {
                selectedIds, profile, directory ->
            val userProfile = profile?.toUserProfile(
                fallbackName = session.displayName,
                emergencyContactIds = selectedIds,
                storedMessage = storedMessage,
                defaultMessage = catalog::defaultEmergencyMessage
            )
                ?: error("Perfil no encontrado")
            ProfileEditModel(
                profile = userProfile,
                config = buildConfig(buildEmergencyCandidates(session.profileId, selectedIds, directory))
            )
        }.map { Result.success(it) }
            .catch { emit(Result.failure<ProfileEditModel>(it)) }
    }

    override suspend fun getProfileEditModel(): Result<ProfileEditModel> = runCatching {
        val session = sessions.currentSession() ?: error("No hay sesion activa")
        val selectedIds = getEmergencyContactIdsOfflineFirst(session.profileId)
        val storedMessage = emergencyMessages.get(session.profileId)
        val profile = remote.getProfile(session.profileId)
            ?.toUserProfile(
                fallbackName = session.displayName,
                emergencyContactIds = selectedIds,
                storedMessage = storedMessage,
                defaultMessage = catalog::defaultEmergencyMessage
            )
            ?: error("Perfil no encontrado")
        ProfileEditModel(
            profile = profile,
            config = buildConfig(
                buildEmergencyCandidates(session.profileId, selectedIds, remote.getEmergencyCandidates())
            )
        )
    }

    override suspend fun saveProfile(update: ProfileUpdate): Result<Unit> = runCatching {
        // Validate before any upload or remote mutation: a rejected password request must not
        // leave the remaining profile fields partially persisted.
        requireProfilePasswordUpdateSupported(update.newPassword)
        val session = sessions.currentSession() ?: error("No hay sesion activa")
        val normalizedIds = normalizeEmergencyContactIds(update.emergencyContactIds)
        val avatarUrl = avatarUploader.uploadIfNeeded(session.profileId, update.avatarUri)
        remote.saveProfile(session.profileId, update.copy(avatarUri = avatarUrl).toRemotePatch())
        if (update.secretAnswer.isNotBlank()) {
            remote.saveRecoverySecret(session.profileId, update.secretQuestion, update.secretAnswer)
        }
        remote.saveEmergencyContacts(session.profileId, normalizedIds)
        emergencyContacts.save(session.profileId, normalizedIds)
        emergencyMessages.save(session.profileId, update.emergencyMessage, update.emergencyMessageIsDefault)
        sessions.updateDisplayName(session, update.displayName)
    }

    override suspend fun saveEmergencySettings(
        contactIds: List<String>,
        message: String,
        messageIsDefault: Boolean
    ): Result<Unit> = runCatching {
        val session = sessions.currentSession() ?: error("No hay sesion activa")
        val normalizedIds = normalizeEmergencyContactIds(contactIds)
        remote.saveEmergencyContacts(session.profileId, normalizedIds)
        emergencyContacts.save(session.profileId, normalizedIds)
        emergencyMessages.save(session.profileId, message, messageIsDefault)
    }

    override fun defaultEmergencyMessage(displayName: String): String =
        catalog.defaultEmergencyMessage(displayName)

    override fun changesSavedMessage(): String = catalog.changesSavedMessage()

    override fun emergencyContactsSavedMessage(): String = catalog.emergencyContactsSavedMessage()

    private fun observeEmergencyContactIds(profileId: String): Flow<List<String>> = flow {
        emit(emergencyContacts.get(profileId))
        val networkIds = remote.getEmergencyContactIds(profileId, ProfileCachePolicy.NetworkOnly)
        emergencyContacts.save(profileId, networkIds)
        emit(networkIds)
    }.catch { emit(emergencyContacts.get(profileId)) }.distinctUntilChanged()

    private suspend fun getEmergencyContactIdsOfflineFirst(profileId: String): List<String> {
        val cachedIds = emergencyContacts.get(profileId)
        val networkIds = runCatching {
            withTimeoutOrNull(EmergencyContactsNetworkTimeoutMillis) {
                remote.getEmergencyContactIds(profileId, ProfileCachePolicy.NetworkOnly)
            }
        }.getOrNull()
        if (networkIds != null) {
            emergencyContacts.save(profileId, networkIds)
            return networkIds
        }
        return cachedIds.ifEmpty {
            runCatching { remote.getEmergencyContactIds(profileId) }
                .onSuccess { emergencyContacts.save(profileId, it) }
                .getOrDefault(emptyList())
        }
    }

    private suspend fun buildEmergencyCandidates(
        currentProfileId: String,
        selectedIds: List<String>,
        directory: List<ProfileRemoteRecord>
    ): List<EmergencyContactCandidate> {
        val ordered = linkedMapOf<String, ProfileRemoteRecord>()
        directory.filterNot { it.id == currentProfileId }.forEach { ordered[it.id] = it }
        val missingIds = normalizeEmergencyContactIds(selectedIds)
            .filterNot { it == currentProfileId || ordered.containsKey(it) }
        if (missingIds.isNotEmpty()) {
            runCatching { remote.getProfiles(missingIds) }.getOrDefault(emptyList())
                .filterNot { it.id == currentProfileId }
                .forEach { ordered[it.id] = it }
        }
        val candidates = linkedMapOf<String, EmergencyContactCandidate>()
        ordered.values.map { it.toEmergencyCandidate() }.forEach { candidates[it.id] = it }
        missingIds.filterNot { candidates.containsKey(it) }.forEach { missingId ->
            candidates[missingId] = EmergencyContactCandidate(
                id = missingId,
                displayName = catalog.fallbackUserName(),
                email = "",
                neighborhood = ""
            )
        }
        return candidates.values.toList()
    }

    private fun buildConfig(candidates: List<EmergencyContactCandidate>) = ProfileEditConfig(
        countryPrefixes = catalog.countryPrefixes(),
        secretQuestions = catalog.secretQuestions(),
        emergencyCandidates = candidates
    )
}

internal fun normalizeEmergencyContactIds(contactIds: List<String>): List<String> =
    contactIds.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(MaxEmergencyContacts)

internal fun requireProfilePasswordUpdateSupported(newPassword: String) {
    require(newPassword.isBlank()) { "profile_password_update_unavailable" }
}

internal fun ProfileUpdate.toRemotePatch(): Map<String, String?> = buildMap {
    put("display_name", displayName)
    put("nombre", displayName)
    put("neighborhood", neighborhood)
    put("barrio", neighborhood)
    put("country_code", countryCode)
    put("code", countryCode)
    put("phone_local", phone)
    put("phone", "+${countryCode.filter(Char::isDigit)}${phone.filter(Char::isDigit)}")
    put("telefono", phone)
    put("avatar_url", avatarUri.cleanProfileValue())
}

internal fun ProfileRemoteRecord.toUserProfile(
    fallbackName: String,
    emergencyContactIds: List<String>,
    storedMessage: StoredProfileEmergencyMessage?,
    defaultMessage: (String) -> String
): UserProfile {
    val displayName = displayName.cleanProfileValue() ?: legacyName.cleanProfileValue() ?: fallbackName
    val phoneParts = profilePhoneParts()
    return UserProfile(
        displayName = displayName,
        neighborhood = neighborhood.cleanProfileValue() ?: legacyNeighborhood.orEmpty(),
        countryCode = phoneParts.first,
        phone = phoneParts.second,
        avatarUri = avatarUrl.cleanProfileValue() ?: legacyAvatar.cleanProfileValue(),
        selectedSecretQuestion = secretQuestion.orEmpty(),
        emergencyContactIds = emergencyContactIds,
        emergencyMessage = storedMessage?.takeUnless { it.isDefault }?.message ?: defaultMessage(displayName),
        emergencyMessageIsDefault = storedMessage?.isDefault ?: true
    )
}

private fun ProfileRemoteRecord.toEmergencyCandidate(): EmergencyContactCandidate = EmergencyContactCandidate(
    id = id,
    displayName = displayName.cleanProfileValue() ?: legacyName.cleanProfileValue() ?: phoneLocal.orEmpty(),
    email = "${countryCode.orEmpty()}${phoneLocal.orEmpty()}@phone.quata.app",
    neighborhood = neighborhood.cleanProfileValue() ?: legacyNeighborhood.orEmpty(),
    phone = phoneLocal.cleanProfileValue() ?: phone.orEmpty()
)

private fun ProfileRemoteRecord.profilePhoneParts(): Pair<String, String> {
    val explicit = (countryCode.cleanProfileValue() ?: legacyCountryCode.cleanProfileValue())?.filter(Char::isDigit)
    val local = phoneLocal.cleanProfileValue()?.filter(Char::isDigit)
        ?: legacyPhone.cleanProfileValue()?.filter(Char::isDigit)
    val e164 = phoneE164.cleanProfileValue()?.filter(Char::isDigit)
    val full = phone.cleanProfileValue()?.filter(Char::isDigit)
    if (!explicit.isNullOrBlank() && !local.isNullOrBlank()) return explicit to local
    if (!e164.isNullOrBlank() && !local.isNullOrBlank() && e164.endsWith(local)) {
        e164.removeSuffix(local).takeIf { it.isNotBlank() }?.let { return it to local }
    }
    if (!full.isNullOrBlank() && !local.isNullOrBlank() && full.endsWith(local)) {
        full.removeSuffix(local).takeIf { it.isNotBlank() }?.let { return it to local }
    }
    return (explicit ?: "240") to (local ?: full ?: "")
}

private fun String?.cleanProfileValue(): String? = this?.trim()?.takeIf { it.isNotBlank() }

private const val MaxEmergencyContacts = 5
private const val EmergencyContactsNetworkTimeoutMillis = 3_500L
