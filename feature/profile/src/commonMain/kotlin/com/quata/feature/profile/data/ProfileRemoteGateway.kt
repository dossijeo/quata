package com.quata.feature.profile.data

import kotlinx.coroutines.flow.Flow

/**
 * Platform-neutral boundary for the Profile backend.
 *
 * The feature deliberately does not expose a Supabase type here: Android, iOS and Web can
 * translate their transport model at this edge without leaking a backend SDK into commonMain.
 */
interface ProfileRemoteGateway {
    suspend fun getProfile(profileId: String): ProfileRemoteRecord?
    suspend fun getProfiles(profileIds: Collection<String>): List<ProfileRemoteRecord>
    fun observeProfile(profileId: String): Flow<ProfileRemoteRecord?>
    suspend fun getEmergencyCandidates(): List<ProfileRemoteRecord>
    fun observeEmergencyCandidates(): Flow<List<ProfileRemoteRecord>>
    suspend fun getEmergencyContactIds(
        profileId: String,
        cachePolicy: ProfileCachePolicy = ProfileCachePolicy.CacheFirst
    ): List<String>

    suspend fun saveProfile(profileId: String, patch: Map<String, String?>)
    suspend fun saveRecoverySecret(profileId: String, secretQuestion: String, secretAnswer: String) {
        error("profile_recovery_secret_update_unavailable")
    }
    suspend fun saveEmergencyContacts(profileId: String, contactIds: List<String>)
}

enum class ProfileCachePolicy { CacheFirst, NetworkOnly }

/** Raw, stable projection of the profile columns consumed by this feature. */
data class ProfileRemoteRecord(
    val id: String,
    val displayName: String? = null,
    val legacyName: String? = null,
    val neighborhood: String? = null,
    val legacyNeighborhood: String? = null,
    val countryCode: String? = null,
    val legacyCountryCode: String? = null,
    val phoneLocal: String? = null,
    val phoneE164: String? = null,
    val phone: String? = null,
    val legacyPhone: String? = null,
    val avatarUrl: String? = null,
    val legacyAvatar: String? = null,
    val secretQuestion: String? = null
)

data class ProfileSession(
    val profileId: String,
    val displayName: String
)

interface ProfileSessionProvider {
    fun currentSession(): ProfileSession?
    fun updateDisplayName(session: ProfileSession, displayName: String)
}

/** Owns platform URI decoding, image optimization and storage upload. */
interface ProfileAvatarUploader {
    suspend fun uploadIfNeeded(profileId: String, avatarUri: String?): String?
    suspend fun rollbackUploaded(profileId: String, uploadedAvatarUrl: String)
}

data class StoredProfileEmergencyMessage(
    val message: String,
    val isDefault: Boolean
)

interface ProfileEmergencyMessageStore {
    suspend fun get(profileId: String): StoredProfileEmergencyMessage?
    suspend fun save(profileId: String, message: String, isDefault: Boolean)
}

interface ProfileEmergencyContactsStore {
    suspend fun get(profileId: String): List<String>
    suspend fun save(profileId: String, contactIds: List<String>)
}
