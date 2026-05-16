package com.quata.feature.profile.data

import com.quata.data.supabase.SupabaseCommunityApi

class ProfileRemoteDataSource(
    private val api: SupabaseCommunityApi
) {
    suspend fun getProfile(userId: String) = api.getProfiles(ids = listOf(userId)).firstOrNull()

    suspend fun getDirectoryProfiles() = api.getProfiles()

    suspend fun getEmergencyCandidates() = api.getProfiles()

    suspend fun getEmergencyContactIds(profileId: String): List<String> =
        api.getEmergencyContacts(profileId).mapNotNull { it.contact_profile_id }

    suspend fun saveProfile(userId: String, patch: Map<String, String?>) {
        api.updateProfile(userId, patch)
    }

    suspend fun saveEmergencyContacts(profileId: String, contactIds: List<String>) {
        val existing = api.getEmergencyContacts(profileId)
        existing.forEach { contact ->
            contact.id.let { api.removeEmergencyContact(it) }
        }
        contactIds.distinct().forEachIndexed { index, contactId ->
            api.addEmergencyContact(profileId, contactId, index + 1)
        }
    }
}
