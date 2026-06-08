package com.quata.feature.profile.data

import com.quata.data.supabase.SupabaseCommunityApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProfileRemoteDataSource(
    private val api: SupabaseCommunityApi
) {
    suspend fun getProfile(userId: String) = api.getProfiles(ids = listOf(userId)).firstOrNull()

    fun observeProfile(userId: String) = api.observeProfiles(ids = listOf(userId)).map { it.firstOrNull() }

    suspend fun getDirectoryProfiles() = api.getProfiles()

    fun observeDirectoryProfiles() = api.observeProfiles()

    suspend fun getEmergencyCandidates() = api.getProfiles()

    fun observeEmergencyCandidates() = api.observeProfiles()

    suspend fun getEmergencyContactIds(profileId: String): List<String> =
        api.getEmergencyContacts(profileId).mapNotNull { it.contact_profile_id }

    fun observeEmergencyContactIds(profileId: String): Flow<List<String>> =
        api.observeEmergencyContacts(profileId).map { contacts -> contacts.mapNotNull { it.contact_profile_id } }

    suspend fun saveProfile(userId: String, patch: Map<String, String?>) {
        api.updateProfile(userId, patch)
    }

    suspend fun uploadAvatar(profileId: String, bytes: ByteArray, extension: String, mimeType: String) =
        api.uploadAvatar(profileId, bytes, extension, mimeType)

    suspend fun saveEmergencyContacts(profileId: String, contactIds: List<String>) {
        api.replaceEmergencyContacts(profileId, contactIds)
    }
}
