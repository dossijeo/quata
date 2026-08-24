package com.quata.feature.profile.data

import android.content.Context
import com.quata.R
import com.quata.core.media.ImageUploadOptions
import com.quata.core.media.MediaUploadOptimizer
import com.quata.core.session.SessionManager
import com.quata.data.supabase.CommunityProfile
import com.quata.data.supabase.SupabaseCacheMode
import com.quata.feature.profile.domain.SecretQuestionOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Android-only implementations of Profile's portable boundaries. */
class AndroidProfileRemoteGateway(
    private val source: ProfileRemoteDataSource,
    private val authApi: com.quata.data.supabase.SupabaseCommunityApi
) : ProfileRemoteGateway {
    override suspend fun getProfile(profileId: String): ProfileRemoteRecord? =
        source.getProfile(profileId)?.toRemoteRecord()

    override suspend fun getProfiles(profileIds: Collection<String>): List<ProfileRemoteRecord> =
        source.getProfiles(profileIds).map(CommunityProfile::toRemoteRecord)

    override fun observeProfile(profileId: String): Flow<ProfileRemoteRecord?> =
        source.observeProfile(profileId).map { it?.toRemoteRecord() }

    override suspend fun getEmergencyCandidates(): List<ProfileRemoteRecord> =
        source.getEmergencyCandidates().map(CommunityProfile::toRemoteRecord)

    override fun observeEmergencyCandidates(): Flow<List<ProfileRemoteRecord>> =
        source.observeEmergencyCandidates().map { profiles -> profiles.map(CommunityProfile::toRemoteRecord) }

    override suspend fun getEmergencyContactIds(
        profileId: String,
        cachePolicy: ProfileCachePolicy
    ): List<String> = source.getEmergencyContactIds(
        profileId = profileId,
        cacheMode = when (cachePolicy) {
            ProfileCachePolicy.CacheFirst -> SupabaseCacheMode.CACHE_FIRST
            ProfileCachePolicy.NetworkOnly -> SupabaseCacheMode.NETWORK_ONLY
        }
    )

    override suspend fun saveProfile(profileId: String, patch: Map<String, String?>) =
        source.saveProfile(profileId, patch)

    override suspend fun saveRecoverySecret(profileId: String, secretQuestion: String, secretAnswer: String) =
        authApi.updateRecoverySecretWithAuthBridge(profileId, secretQuestion, secretAnswer)


    override suspend fun saveEmergencyContacts(profileId: String, contactIds: List<String>) =
        source.saveEmergencyContacts(profileId, contactIds)
}

class AndroidProfileSessionProvider(
    private val sessionManager: SessionManager
) : ProfileSessionProvider {
    override fun currentSession(): ProfileSession? = sessionManager.currentSession()?.let {
        ProfileSession(profileId = it.userId, displayName = it.displayName)
    }

    override fun updateDisplayName(session: ProfileSession, displayName: String) {
        sessionManager.currentSession()
            ?.takeIf { it.userId == session.profileId }
            ?.let { sessionManager.setSession(it.copy(displayName = displayName)) }
    }
}

class AndroidProfileAvatarUploader(
    private val remote: ProfileRemoteDataSource,
    private val optimizer: MediaUploadOptimizer
) : ProfileAvatarUploader {
    override suspend fun uploadIfNeeded(profileId: String, avatarUri: String?): String? {
        if (!optimizer.isLocalUploadUri(avatarUri)) return avatarUri
        val media = optimizer.prepareImageUpload(
            uriString = avatarUri ?: return null,
            fallbackMimeType = "image/jpeg",
            fallbackFileNameBase = "avatar",
            options = ImageUploadOptions.Avatar
        )
        return remote.uploadAvatar(profileId, media.bytes, media.extension, media.mimeType).publicUrl
            ?: error("Supabase no devolvio URL de avatar")
    }

    override suspend fun rollbackUploaded(profileId: String, uploadedAvatarUrl: String) {
        val storagePath = uploadedAvatarUrl
            .substringAfter("/storage/v1/object/public/community-posts/", missingDelimiterValue = "")
            .takeIf { it.startsWith("avatars/$profileId/") && ".." !in it }
            ?: error("android_profile_avatar_rollback_path_invalid")
        remote.deleteAvatarObject(storagePath)
    }
}

class AndroidProfileEmergencyMessageStore(context: Context) : ProfileEmergencyMessageStore {
    private val delegate = EmergencyMessageStore(context)

    override suspend fun get(profileId: String): StoredProfileEmergencyMessage? = delegate.get(profileId)?.let {
        StoredProfileEmergencyMessage(message = it.message, isDefault = it.isDefault)
    }

    override suspend fun save(profileId: String, message: String, isDefault: Boolean) {
        delegate.save(profileId, message, isDefault)
    }
}

class AndroidProfileEmergencyContactsStore(context: Context) : ProfileEmergencyContactsStore {
    private val delegate = EmergencyContactsStore(context)
    override suspend fun get(profileId: String): List<String> = delegate.get(profileId)
    override suspend fun save(profileId: String, contactIds: List<String>) = delegate.save(profileId, contactIds)
}

class AndroidProfilePresentationCatalog(private val context: Context) : ProfilePresentationCatalog {
    override fun countryPrefixes() = context.countryPrefixOptions()
    override fun secretQuestions(): List<SecretQuestionOption> = context.profileSecretQuestionOptions()
    override fun fallbackUserName(): String = context.getString(R.string.user_fallback_name)
    override fun defaultEmergencyMessage(displayName: String): String = context.getString(
        R.string.sos_default_message,
        displayName.ifBlank { fallbackUserName() }
    )

    override fun changesSavedMessage(): String = context.getString(R.string.profile_changes_saved)
    override fun emergencyContactsSavedMessage(): String =
        context.getString(R.string.profile_emergency_contacts_updated)
}

private fun CommunityProfile.toRemoteRecord() = ProfileRemoteRecord(
    id = id,
    displayName = display_name,
    legacyName = nombre,
    neighborhood = neighborhood,
    legacyNeighborhood = barrio,
    countryCode = country_code,
    legacyCountryCode = code,
    phoneLocal = phone_local,
    phoneE164 = phone_e164,
    phone = phone,
    legacyPhone = telefono,
    avatarUrl = avatar_url,
    legacyAvatar = avatar,
    secretQuestion = secret_question
)
