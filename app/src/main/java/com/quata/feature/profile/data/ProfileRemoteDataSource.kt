package com.quata.feature.profile.data

import com.quata.core.network.supabase.SupabaseApi
import com.quata.core.network.supabase.SupabaseProfileUpdateRequest

class ProfileRemoteDataSource(
    private val api: SupabaseApi
) {
    suspend fun getProfile(userId: String) = api.getProfiles("eq.$userId").firstOrNull()

    suspend fun getDirectoryProfiles() = api.getEmergencyCandidates()

    suspend fun getEmergencyCandidates() = api.getEmergencyCandidates()

    suspend fun saveProfile(userId: String, request: SupabaseProfileUpdateRequest) {
        api.updateProfile("eq.$userId", request)
    }
}
