package com.quata.feature.profile.data

import com.quata.data.supabase.SupabaseCommunityApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProfileRemoteDataSource(
    private val api: SupabaseCommunityApi
) {
    suspend fun getProfile(userId: String) = api.getProfiles(ids = listOf(userId)).firstOrNull()

    suspend fun getProfiles(ids: Collection<String>) =
        if (ids.isEmpty()) emptyList() else api.getProfiles(ids = ids)

    fun observeProfile(userId: String) = api.observeProfiles(ids = listOf(userId)).map { it.firstOrNull() }

    suspend fun getDirectoryProfiles() = api.getProfiles()

    fun observeDirectoryProfiles() = api.observeProfiles()

    suspend fun getEmergencyCandidates() = api.getProfiles(limit = EmergencyCandidateLimit)

    fun observeEmergencyCandidates() = api.observeProfiles(limit = EmergencyCandidateLimit)

    suspend fun getEmergencyContactIds(profileId: String): List<String> =
        api.getEmergencyContacts(profileId)
            .mapNotNull { it.contact_profile_id }
            .distinct()
            .take(5)

    fun observeEmergencyContactIds(profileId: String): Flow<List<String>> =
        api.observeEmergencyContacts(profileId).map { contacts ->
            contacts
                .mapNotNull { it.contact_profile_id }
                .distinct()
                .take(5)
        }

    suspend fun saveProfile(userId: String, patch: Map<String, String?>) {
        api.updateProfile(userId, patch)
    }

    suspend fun uploadAvatar(profileId: String, bytes: ByteArray, extension: String, mimeType: String) =
        api.uploadAvatar(profileId, bytes, extension, mimeType)

    suspend fun saveEmergencyContacts(profileId: String, contactIds: List<String>) {
        api.replaceEmergencyContacts(profileId, contactIds)
    }

    private companion object {
        const val EmergencyCandidateLimit = 5_000
    }
}
