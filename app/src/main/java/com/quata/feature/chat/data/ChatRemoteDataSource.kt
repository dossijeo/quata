package com.quata.feature.chat.data

import com.quata.data.supabase.SupabaseCommunityApi

class ChatRemoteDataSource(
    val supabaseApi: SupabaseCommunityApi
) {
    suspend fun ensureFreshSession(force: Boolean = false) = supabaseApi.ensureFreshSession(force)
    suspend fun getProfiles(ids: Collection<String>? = null, limit: Int = 500) = supabaseApi.getProfiles(ids, limit)
    fun observeProfiles(ids: Collection<String>? = null, limit: Int = 500) = supabaseApi.observeProfiles(ids, limit)
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
    suspend fun uploadChatAttachment(profileId: String, bytes: ByteArray, extension: String, mimeType: String, fileName: String?) =
        supabaseApi.uploadChatAttachment(profileId, bytes, extension, mimeType, fileName)
    suspend fun getPrivateChats(profileId: String) = supabaseApi.getPrivateChats(profileId)
    fun observePrivateChats(profileId: String) = supabaseApi.observePrivateChats(profileId)
    suspend fun createOrGetPrivateChat(profileId: String, peerProfileId: String) =
        supabaseApi.createOrGetPrivateChat(profileId, peerProfileId)
    suspend fun sendSos(profileId: String, text: String, lat: Double? = null, lng: Double? = null, accuracy: Double? = null) =
        supabaseApi.sendSos(profileId, text, lat, lng, accuracy)
    suspend fun getChatInbox(profileId: String, limit: Int = 100) = supabaseApi.getChatInbox(profileId, limit)
    suspend fun getChatThread(profileId: String, threadId: Long, limit: Int = 250) = supabaseApi.getChatThread(profileId, threadId, limit)
    suspend fun getOrCreatePrivateThread(profileId: String, peerProfileId: String) = supabaseApi.getOrCreatePrivateThread(profileId, peerProfileId)
    suspend fun searchChatConversationCandidates(profileId: String, query: String = "", limit: Int = 30, offset: Int = 0) =
        supabaseApi.searchChatConversationCandidates(profileId, query, limit, offset)
    suspend fun startChatThread(profileId: String, participantIds: List<String>, subject: String?, type: String, message: String = "", uniqueKey: String? = null, communityId: String? = null) =
        supabaseApi.startChatThread(profileId, participantIds, subject, type, message, uniqueKey, communityId)
    suspend fun openCommunityChatThread(profileId: String, communityId: String, title: String) = supabaseApi.openCommunityChatThread(profileId, communityId, title)
    suspend fun registerChatAttachment(profileId: String, threadId: Long, fileUrl: String, storagePath: String?, mimeType: String, name: String?, sizeBytes: Long? = null, extension: String? = null) =
        supabaseApi.registerChatAttachment(profileId, threadId, fileUrl, storagePath, mimeType, name, sizeBytes, extension)
    suspend fun sendChatMessage(
        profileId: String,
        threadId: Long,
        message: String,
        fileIds: List<Long> = emptyList(),
        replyToMessageId: Long? = null,
        clientMessageId: String? = null
    ) = supabaseApi.sendChatMessage(profileId, threadId, message, fileIds, replyToMessageId, clientMessageId)
    suspend fun setChatFavorite(profileId: String, threadId: Long, messageId: Long, favorite: Boolean) = supabaseApi.setChatFavorite(profileId, threadId, messageId, favorite)
    suspend fun getChatFavorites(profileId: String, limit: Int = 250) = supabaseApi.getChatFavorites(profileId, limit)
    suspend fun editChatMessage(profileId: String, threadId: Long, messageId: Long, message: String) = supabaseApi.editChatMessage(profileId, threadId, messageId, message)
    suspend fun deleteChatMessages(profileId: String, threadId: Long, messageIds: List<Long>) = supabaseApi.deleteChatMessages(profileId, threadId, messageIds)
    suspend fun forwardChatMessage(profileId: String, messageId: Long, threadIds: List<Long>) = supabaseApi.forwardChatMessage(profileId, messageId, threadIds)
    suspend fun markChatThreadRead(profileId: String, threadId: Long) = supabaseApi.markChatThreadRead(profileId, threadId)
    suspend fun setChatMuted(profileId: String, threadId: Long, muted: Boolean) = supabaseApi.setChatMuted(profileId, threadId, muted)
    suspend fun setChatMemberInvitesEnabled(profileId: String, threadId: Long, enabled: Boolean) = supabaseApi.setChatMemberInvitesEnabled(profileId, threadId, enabled)
    suspend fun addChatParticipants(profileId: String, threadId: Long, participantIds: List<String>) = supabaseApi.addChatParticipants(profileId, threadId, participantIds)
    suspend fun promoteChatModerator(profileId: String, threadId: Long, participantId: String) = supabaseApi.promoteChatModerator(profileId, threadId, participantId)
    suspend fun demoteChatModerator(profileId: String, threadId: Long, participantId: String) = supabaseApi.demoteChatModerator(profileId, threadId, participantId)
    suspend fun removeChatParticipant(profileId: String, threadId: Long, participantId: String) = supabaseApi.removeChatParticipant(profileId, threadId, participantId)
    suspend fun blockChatParticipant(profileId: String, threadId: Long, participantId: String) = supabaseApi.blockChatParticipant(profileId, threadId, participantId)
    suspend fun leaveChatThread(profileId: String, threadId: Long) = supabaseApi.leaveChatThread(profileId, threadId)
    suspend fun deleteChatThread(profileId: String, threadId: Long) = supabaseApi.deleteChatThread(profileId, threadId)
    suspend fun cleanupEmptyPrivateThread(profileId: String, threadId: Long) = supabaseApi.cleanupEmptyPrivateThread(profileId, threadId)
    suspend fun restoreChatThread(profileId: String, threadId: Long) = supabaseApi.restoreChatThread(profileId, threadId)
    suspend fun sendChatSos(profileId: String, contactIds: List<String>, message: String, lat: Double? = null, lng: Double? = null, accuracy: Double? = null) =
        supabaseApi.sendChatSos(profileId, contactIds, message, lat, lng, accuracy)
}
