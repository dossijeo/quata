package com.quata.feature.chat.data

import com.quata.data.supabase.SupabaseCommunityApi

class ChatRemoteDataSource(
    private val supabaseApi: SupabaseCommunityApi
) {
    suspend fun getProfiles(ids: Collection<String>? = null) = supabaseApi.getProfiles(ids)
    fun observeProfiles(ids: Collection<String>? = null) = supabaseApi.observeProfiles(ids)
    suspend fun getActiveWalls() = supabaseApi.getActiveWallsStats()
    fun observeActiveWalls() = supabaseApi.observeActiveWallsStats()
    suspend fun getWallFollows(wallId: String) = supabaseApi.getWallFollows(wallId = wallId)
    fun observeWallFollows(wallId: String) = supabaseApi.observeWallFollows(wallId = wallId)
    suspend fun ensureWallFollow(wallId: String, profileId: String) = supabaseApi.ensureWallFollow(wallId, profileId)
    suspend fun getCommunityMessages(wallId: String, limit: Int = 250) = supabaseApi.getCommunityMessages(wallId, limit = limit)
    fun observeCommunityMessages(wallId: String, limit: Int = 250) = supabaseApi.observeCommunityMessages(wallId, limit = limit)
    suspend fun sendCommunityMessage(wallId: String, profileId: String, body: String) =
        supabaseApi.sendCommunityMessage(wallId, profileId, body)
    suspend fun sendCommunityImageMessage(wallId: String, profileId: String, imageUrl: String, fileName: String, mimeType: String, text: String) =
        supabaseApi.sendCommunityImageMessage(wallId, profileId, imageUrl, fileName, mimeType, text)
    suspend fun uploadCommunityChatImage(profileId: String, bytes: ByteArray, extension: String, mimeType: String) =
        supabaseApi.uploadCommunityChatImage(profileId, bytes, extension, mimeType)
    suspend fun getPrivateChats(profileId: String) = supabaseApi.getPrivateChats(profileId)
    fun observePrivateChats(profileId: String) = supabaseApi.observePrivateChats(profileId)
    suspend fun createOrGetPrivateChat(profileId: String, peerProfileId: String) =
        supabaseApi.createOrGetPrivateChat(profileId, peerProfileId)
    suspend fun sendSos(profileId: String, text: String, lat: Double? = null, lng: Double? = null, accuracy: Double? = null) =
        supabaseApi.sendSos(profileId, text, lat, lng, accuracy)
}
