package com.quata.feature.chat.domain

/** Shared chat data contract. */

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class ChatConversationCandidate(
    val profileId: String,
    val displayName: String,
    val neighborhood: String,
    val phone: String,
    val avatarUrl: String?,
    val sectionKey: String,
    val neighborhoodGroup: String,
    val existingConversationId: String?
)

data class ChatConversationCandidatePage(
    val candidates: List<ChatConversationCandidate>,
    val hasMore: Boolean,
    val nextOffset: Int,
    val actorNeighborhood: String
)

data class ChatInviteContact(
    val id: String,
    val displayName: String,
    val phone: String,
    val phoneKeys: Set<String>,
    val internationalPhone: String? = null
)

enum class ChatSyncStatus { CacheAvailable, Refreshing, Online, Offline, Error }

interface ChatRepository {
    val activeConversationId: StateFlow<String?>
    val isAppForeground: StateFlow<Boolean>
    val pendingDeletedConversation: StateFlow<Conversation?>
    val isRealtimeOnline: StateFlow<Boolean>
    val typingProfileIds: StateFlow<Set<String>>
    val syncStatus: StateFlow<ChatSyncStatus>
    fun setDeviceNetworkAvailable(isAvailable: Boolean)
    fun currentUser(): User?
    fun setActiveConversation(conversationId: String?)
    fun setConversationVisible(conversationId: String, visible: Boolean)
    fun setAppForeground(isForeground: Boolean)
    fun setTyping(conversationId: String, isTyping: Boolean)
    fun cleanupEmptyConversation(conversationId: String)
    fun clearChatNotifications()
    suspend fun getConversations(): Result<List<Conversation>>
    fun observeConversations(): Flow<List<Conversation>>
    fun observeMessages(conversationId: String): Flow<List<Message>>
    suspend fun loadOlderMessages(conversationId: String, limit: Int = 100): Result<Boolean>
    fun observeParticipantCandidates(): Flow<List<User>>
    suspend fun searchConversationCandidates(query: String, limit: Int = 30, offset: Int = 0): Result<ChatConversationCandidatePage>
    suspend fun matchRegisteredContactPhones(phoneCandidates: Collection<String>): Result<Set<String>>
    suspend fun openPrivateConversation(peerProfileId: String): Result<String>
    suspend fun sendMessage(
        conversationId: String,
        text: String,
        attachmentUri: String? = null,
        attachmentName: String? = null,
        attachmentMimeType: String? = null,
        clientMessageId: String? = null
    ): Result<Unit>
    suspend fun sendReply(
        conversationId: String,
        text: String,
        replyTo: Message,
        attachmentUri: String? = null,
        attachmentName: String? = null,
        attachmentMimeType: String? = null,
        clientMessageId: String? = null
    ): Result<Unit>
    suspend fun sendSosMessage(
        contactIds: List<String>,
        text: String,
        lat: Double? = null,
        lng: Double? = null,
        accuracy: Double? = null
    ): Result<String>
    suspend fun cachedPrivateConversationId(userId: String): String?
    suspend fun cachedCommunityConversationId(communityName: String): String?
    suspend fun openCommunityConversation(communityId: String, title: String, participantIds: List<String>): Result<String>
    suspend fun openGroupConversation(participantIds: List<String>, title: String? = null): Result<String>
    suspend fun markConversationRead(conversationId: String): Result<Unit>
    suspend fun setConversationMuted(conversationId: String, muted: Boolean): Result<Unit>
    suspend fun setMemberInvitesEnabled(conversationId: String, enabled: Boolean): Result<Unit>
    suspend fun addParticipants(conversationId: String, participantIds: List<String>): Result<Unit>
    suspend fun promoteModerator(conversationId: String, userId: String): Result<Unit>
    suspend fun demoteModerator(conversationId: String, userId: String): Result<Unit>
    suspend fun removeParticipant(conversationId: String, userId: String): Result<Unit>
    suspend fun blockParticipant(conversationId: String, userId: String): Result<Unit>
    suspend fun reportMessage(messageId: String): Result<Unit>
    suspend fun leaveConversation(conversationId: String): Result<Unit>
    suspend fun hideConversation(conversationId: String): Result<Unit>
    suspend fun deleteConversation(conversationId: String): Result<Unit>
    suspend fun restorePendingDeletedConversation(): Result<Unit>
    suspend fun finalizePendingDeletedConversation(): Result<Unit>
    suspend fun editMessage(messageId: String, text: String): Result<Unit>
    suspend fun deleteMessage(messageId: String): Result<Unit>
    suspend fun toggleFavoriteMessage(messageId: String): Result<Unit>
    suspend fun forwardMessage(message: Message, conversationIds: List<String>): Result<Unit>
    suspend fun flushPendingMessages(): Boolean
    suspend fun retryPendingMessage(clientMessageId: String): Result<Unit>
}
