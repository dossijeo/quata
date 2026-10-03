package com.quata.feature.chat.presentation.chat

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.feature.chat.domain.ChatConversationCandidatePage
import com.quata.feature.chat.domain.ChatForwardResult
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.domain.ChatSyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

const val DocumentRetryEvidenceConversationId = "local:document-retry"
const val DocumentRetryEvidenceMessageId = "local-document-retry-message"
const val DocumentRetryEvidenceDocumentName = "quata-document-retry.docx"
const val DocumentRetryEvidenceDocumentMime =
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

/**
 * One-message repository for platform document-retry acceptance when the remote Auth edge is
 * unavailable. Hosts must gate construction behind their explicit local evidence opt-in. The
 * production Chat surface and document adapters remain unchanged; only remote fixture seeding is
 * replaced by this immutable input.
 */
class DocumentRetryEvidenceChatRepository(
    attachmentReference: String,
    attachmentName: String = DocumentRetryEvidenceDocumentName,
    attachmentMimeType: String = DocumentRetryEvidenceDocumentMime,
) : ChatRepository {
    private val user = User("local-document-retry-user", "document-retry@invalid", "Prueba local")
    private val conversations = MutableStateFlow(
        listOf(
            Conversation(
                id = DocumentRetryEvidenceConversationId,
                title = "Reintento de documento",
                lastMessagePreview = "Documento local para reintento",
                unreadCount = 0,
            ),
        ),
    )
    private val messages = MutableStateFlow(
        listOf(
            Message(
                id = DocumentRetryEvidenceMessageId,
                conversationId = DocumentRetryEvidenceConversationId,
                senderId = user.id,
                senderName = user.displayName,
                text = "Documento local para reintento",
                sentAt = "local",
                sentAtMillis = 1L,
                isMine = true,
                attachmentUri = attachmentReference,
                attachmentName = attachmentName,
                attachmentMimeType = attachmentMimeType,
            ),
        ),
    )
    private val active = MutableStateFlow<String?>(null)
    private val foreground = MutableStateFlow(true)
    private val pendingDeleted = MutableStateFlow<Conversation?>(null)
    private val realtime = MutableStateFlow(false)
    private val typing = MutableStateFlow<Set<String>>(emptySet())
    private val sync = MutableStateFlow(ChatSyncStatus.Online)

    override val activeConversationId: StateFlow<String?> = active.asStateFlow()
    override val isAppForeground: StateFlow<Boolean> = foreground.asStateFlow()
    override val pendingDeletedConversation: StateFlow<Conversation?> = pendingDeleted.asStateFlow()
    override val isRealtimeOnline: StateFlow<Boolean> = realtime.asStateFlow()
    override val typingProfileIds: StateFlow<Set<String>> = typing.asStateFlow()
    override val syncStatus: StateFlow<ChatSyncStatus> = sync.asStateFlow()
    override fun setDeviceNetworkAvailable(isAvailable: Boolean) = Unit
    override fun currentUser(): User = user
    override fun setActiveConversation(conversationId: String?) { active.value = conversationId }
    override fun setConversationVisible(conversationId: String, visible: Boolean) = Unit
    override fun setAppForeground(isForeground: Boolean) { foreground.value = isForeground }
    override fun setTyping(conversationId: String, isTyping: Boolean) = Unit
    override fun cleanupEmptyConversation(conversationId: String) = Unit
    override fun clearChatNotifications() = Unit
    override suspend fun getConversations(): Result<List<Conversation>> = Result.success(conversations.value)
    override fun observeConversations(): Flow<List<Conversation>> = conversations
    override fun observeMessages(conversationId: String): Flow<List<Message>> =
        if (conversationId == DocumentRetryEvidenceConversationId) messages else flowOf(emptyList())
    override suspend fun loadOlderMessages(conversationId: String, limit: Int): Result<Boolean> = Result.success(false)
    override fun observeParticipantCandidates(): Flow<List<User>> = flowOf(emptyList())
    override suspend fun searchConversationCandidates(query: String, limit: Int, offset: Int) =
        Result.success(ChatConversationCandidatePage(emptyList(), false, 0, ""))
    override suspend fun matchRegisteredContactPhones(phoneCandidates: Collection<String>) = Result.success(emptySet<String>())
    override suspend fun openPrivateConversation(peerProfileId: String) = Result.success(DocumentRetryEvidenceConversationId)
    override suspend fun sendMessage(
        conversationId: String,
        text: String,
        attachmentUri: String?,
        attachmentName: String?,
        attachmentMimeType: String?,
        clientMessageId: String?,
        expectedActorId: String?,
    ) = Result.failure<Unit>(IllegalStateException("document_retry_fixture_is_read_only"))
    override suspend fun sendReply(
        conversationId: String,
        text: String,
        replyTo: Message,
        attachmentUri: String?,
        attachmentName: String?,
        attachmentMimeType: String?,
        clientMessageId: String?,
    ) = Result.failure<Unit>(IllegalStateException("document_retry_fixture_is_read_only"))
    override suspend fun sendSosMessage(
        contactIds: List<String>,
        text: String,
        lat: Double?,
        lng: Double?,
        accuracy: Double?,
        expectedActorId: String?,
    ) = Result.failure<String>(IllegalStateException("document_retry_fixture_is_read_only"))
    override suspend fun cachedPrivateConversationId(userId: String): String? = null
    override suspend fun cachedCommunityConversationId(communityName: String): String? = null
    override suspend fun openCommunityConversation(communityId: String, title: String, participantIds: List<String>) =
        Result.failure<String>(IllegalStateException("document_retry_fixture_is_read_only"))
    override suspend fun openGroupConversation(participantIds: List<String>, title: String?) =
        Result.failure<String>(IllegalStateException("document_retry_fixture_is_read_only"))
    override suspend fun markConversationRead(conversationId: String) = Result.success(Unit)
    override suspend fun setConversationMuted(conversationId: String, muted: Boolean) = Result.success(Unit)
    override suspend fun setMemberInvitesEnabled(conversationId: String, enabled: Boolean) = Result.success(Unit)
    override suspend fun addParticipants(conversationId: String, participantIds: List<String>) = Result.success(Unit)
    override suspend fun promoteModerator(conversationId: String, userId: String) = Result.success(Unit)
    override suspend fun demoteModerator(conversationId: String, userId: String) = Result.success(Unit)
    override suspend fun removeParticipant(conversationId: String, userId: String) = Result.success(Unit)
    override suspend fun blockParticipant(conversationId: String, userId: String) = Result.success(Unit)
    override suspend fun reportMessage(messageId: String) = Result.success(Unit)
    override suspend fun leaveConversation(conversationId: String) = Result.success(Unit)
    override suspend fun hideConversation(conversationId: String) = Result.success(Unit)
    override suspend fun deleteConversation(conversationId: String) = Result.success(Unit)
    override suspend fun restorePendingDeletedConversation() = Result.success(Unit)
    override suspend fun finalizePendingDeletedConversation() = Result.success(Unit)
    override suspend fun editMessage(messageId: String, text: String) = Result.success(Unit)
    override suspend fun deleteMessage(messageId: String) = Result.success(Unit)
    override suspend fun toggleFavoriteMessage(messageId: String) = Result.success(Unit)
    override suspend fun forwardMessage(message: Message, conversationIds: List<String>) = Result.success(
        ChatForwardResult(conversationIds.distinct().size, conversationIds.distinct().size),
    )
    override suspend fun flushPendingMessages() = true
    override suspend fun retryPendingMessage(clientMessageId: String) = Result.success(Unit)
}
