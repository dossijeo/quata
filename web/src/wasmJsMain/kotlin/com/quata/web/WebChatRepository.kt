package com.quata.web

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.feature.chat.data.parseChatRpcPayloadEnvelope
import com.quata.feature.chat.data.toChatRpcConversations
import com.quata.feature.chat.data.toChatRpcMessages
import com.quata.feature.chat.domain.ChatConversationCandidatePage
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.domain.ChatSyncStatus
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Browser chat reader backed by the same RLS-protected inbox/thread RPCs as Android.
 *
 * There is deliberately no browser cache, upload path, mutation API, or Realtime transport here:
 * fresh snapshots are obtained by polling. Every write contract fails explicitly until its browser
 * transport and UX are implemented.
 */
class WebChatRepository(
    private val rpcClient: WebPostgrestRpcClient,
    private val authRepository: WebAuthRepository,
    private val pollIntervalMillis: Long = DefaultPollIntervalMillis,
) : ChatRepository {
    private val conversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val messagesByConversation = mutableMapOf<String, MutableStateFlow<List<Message>>>()
    private val _activeConversationId = MutableStateFlow<String?>(null)
    private val _isAppForeground = MutableStateFlow(true)
    private val _pendingDeletedConversation = MutableStateFlow<Conversation?>(null)
    private val _isRealtimeOnline = MutableStateFlow(false)
    private val _typingProfileIds = MutableStateFlow<Set<String>>(emptySet())
    private val _syncStatus = MutableStateFlow(ChatSyncStatus.Offline)
    private var networkAvailable = true
    private var currentUserSnapshot: User? = null

    override val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()
    override val isAppForeground: StateFlow<Boolean> = _isAppForeground.asStateFlow()
    override val pendingDeletedConversation: StateFlow<Conversation?> = _pendingDeletedConversation.asStateFlow()
    override val isRealtimeOnline: StateFlow<Boolean> = _isRealtimeOnline.asStateFlow()
    override val typingProfileIds: StateFlow<Set<String>> = _typingProfileIds.asStateFlow()
    override val syncStatus: StateFlow<ChatSyncStatus> = _syncStatus.asStateFlow()

    override fun setDeviceNetworkAvailable(isAvailable: Boolean) {
        networkAvailable = isAvailable
        _syncStatus.value = if (isAvailable) ChatSyncStatus.Refreshing else ChatSyncStatus.Offline
    }

    override fun currentUser(): User? = currentUserSnapshot

    override fun setActiveConversation(conversationId: String?) {
        _activeConversationId.value = conversationId
    }

    override fun setConversationVisible(conversationId: String, visible: Boolean) = Unit

    override fun setAppForeground(isForeground: Boolean) {
        _isAppForeground.value = isForeground
    }

    /** Typing presence needs a browser realtime protocol; this reader intentionally does not emit it. */
    override fun setTyping(conversationId: String, isTyping: Boolean) = Unit

    override fun cleanupEmptyConversation(conversationId: String) = Unit

    override fun clearChatNotifications() = Unit

    override suspend fun getConversations(): Result<List<Conversation>> = refreshInbox()

    override fun observeConversations(): Flow<List<Conversation>> = flow {
        while (currentCoroutineContext().isActive) {
            refreshInbox()
            emit(conversations.value)
            delay(pollIntervalMillis.coerceAtLeast(MinimumPollIntervalMillis))
        }
    }

    override fun observeMessages(conversationId: String): Flow<List<Message>> = flow {
        val state = messagesState(conversationId)
        while (currentCoroutineContext().isActive) {
            refreshThread(conversationId, ThreadPageSize)
            emit(state.value)
            delay(pollIntervalMillis.coerceAtLeast(MinimumPollIntervalMillis))
        }
    }

    override suspend fun loadOlderMessages(conversationId: String, limit: Int): Result<Boolean> = runCatching {
        val messages = refreshThread(conversationId, limit.coerceAtLeast(1)).getOrThrow()
        messages.size >= limit
    }

    override fun observeParticipantCandidates(): Flow<List<User>> = flow { emit(emptyList()) }

    override suspend fun searchConversationCandidates(
        query: String,
        limit: Int,
        offset: Int,
    ): Result<ChatConversationCandidatePage> = unsupportedMutation()

    override suspend fun matchRegisteredContactPhones(phoneCandidates: Collection<String>): Result<Set<String>> = unsupportedMutation()

    override suspend fun openPrivateConversation(peerProfileId: String): Result<String> = unsupportedMutation()

    override suspend fun sendMessage(
        conversationId: String,
        text: String,
        attachmentUri: String?,
        attachmentName: String?,
        attachmentMimeType: String?,
        clientMessageId: String?,
    ): Result<Unit> = unsupportedMutation()

    override suspend fun sendReply(
        conversationId: String,
        text: String,
        replyTo: Message,
        attachmentUri: String?,
        attachmentName: String?,
        attachmentMimeType: String?,
        clientMessageId: String?,
    ): Result<Unit> = unsupportedMutation()

    override suspend fun sendSosMessage(
        contactIds: List<String>, text: String, lat: Double?, lng: Double?, accuracy: Double?,
    ): Result<String> = unsupportedMutation()

    override suspend fun cachedPrivateConversationId(userId: String): String? = null

    override suspend fun cachedCommunityConversationId(communityName: String): String? = null

    override suspend fun openCommunityConversation(
        communityId: String, title: String, participantIds: List<String>,
    ): Result<String> = unsupportedMutation()

    override suspend fun openGroupConversation(participantIds: List<String>, title: String?): Result<String> = unsupportedMutation()

    override suspend fun markConversationRead(conversationId: String): Result<Unit> = unsupportedMutation()
    override suspend fun setConversationMuted(conversationId: String, muted: Boolean): Result<Unit> = unsupportedMutation()
    override suspend fun setMemberInvitesEnabled(conversationId: String, enabled: Boolean): Result<Unit> = unsupportedMutation()
    override suspend fun addParticipants(conversationId: String, participantIds: List<String>): Result<Unit> = unsupportedMutation()
    override suspend fun promoteModerator(conversationId: String, userId: String): Result<Unit> = unsupportedMutation()
    override suspend fun demoteModerator(conversationId: String, userId: String): Result<Unit> = unsupportedMutation()
    override suspend fun removeParticipant(conversationId: String, userId: String): Result<Unit> = unsupportedMutation()
    override suspend fun blockParticipant(conversationId: String, userId: String): Result<Unit> = unsupportedMutation()
    override suspend fun reportMessage(messageId: String): Result<Unit> = unsupportedMutation()
    override suspend fun leaveConversation(conversationId: String): Result<Unit> = unsupportedMutation()
    override suspend fun hideConversation(conversationId: String): Result<Unit> = unsupportedMutation()
    override suspend fun deleteConversation(conversationId: String): Result<Unit> = unsupportedMutation()
    override suspend fun restorePendingDeletedConversation(): Result<Unit> = unsupportedMutation()
    override suspend fun finalizePendingDeletedConversation(): Result<Unit> = unsupportedMutation()
    override suspend fun editMessage(messageId: String, text: String): Result<Unit> = unsupportedMutation()
    override suspend fun deleteMessage(messageId: String): Result<Unit> = unsupportedMutation()
    override suspend fun toggleFavoriteMessage(messageId: String): Result<Unit> = unsupportedMutation()
    override suspend fun forwardMessage(message: Message, conversationIds: List<String>): Result<Unit> = unsupportedMutation()
    override suspend fun flushPendingMessages(): Boolean = false
    override suspend fun retryPendingMessage(clientMessageId: String): Result<Unit> = unsupportedMutation()

    private suspend fun refreshInbox(): Result<List<Conversation>> = runCatching {
        val userId = currentUserId()
        _syncStatus.value = ChatSyncStatus.Refreshing
        val envelope = rpc("quata_chat_get_inbox", inboxRequest(userId))
        val mapped = envelope.toChatRpcConversations(userId)
            .sortedByDescending { it.updatedAtMillis ?: 0L }
        conversations.value = mapped
        mergeMessages(envelope.toChatRpcMessages(userId))
        _syncStatus.value = ChatSyncStatus.Online
        mapped
    }.onFailure { updateReadFailure() }

    private suspend fun refreshThread(conversationId: String, limit: Int): Result<List<Message>> = runCatching {
        val userId = currentUserId()
        val threadId = conversationId.removePrefix(ConversationPrefix).toLongOrNull()
            ?.takeIf { it > 0L }
            ?: throw IllegalArgumentException("web_chat_invalid_conversation_id")
        _syncStatus.value = ChatSyncStatus.Refreshing
        val knownIds = messagesState(conversationId).value.mapNotNull { it.id.toLongOrNull() }
        val envelope = rpc("quata_chat_get_thread", threadRequest(userId, threadId, limit, knownIds))
        val mapped = envelope.toChatRpcMessages(userId)
        mergeMessages(mapped)
        _syncStatus.value = ChatSyncStatus.Online
        messagesState(conversationId).value
    }.onFailure { updateReadFailure() }

    private suspend fun currentUserId(): String {
        if (!networkAvailable) throw IllegalStateException("web_chat_offline")
        val session = authRepository.sessionForAuthenticatedRequest() ?: throw IllegalStateException("web_chat_session_missing")
        currentUserSnapshot = User(id = session.userId, email = "", displayName = "Usuario")
        return session.userId
    }

    private suspend fun rpc(functionName: String, body: String) = when (val result = rpcClient.post(functionName, body)) {
        is WebPostgrestResult.Success -> Json.parseToJsonElement(result.body).let(::parseChatRpcPayloadEnvelope)
        is WebPostgrestResult.Failure -> throw WebPostgrestReadException(result)
    }

    private fun mergeMessages(incoming: List<Message>) {
        incoming.groupBy(Message::conversationId).forEach { (conversationId, messages) ->
            val existing = messagesState(conversationId).value.associateBy(Message::id)
            messagesState(conversationId).value = (existing + messages.associateBy(Message::id)).values
                .sortedBy { it.sentAtMillis ?: Long.MIN_VALUE }
        }
    }

    private fun messagesState(conversationId: String): MutableStateFlow<List<Message>> =
        messagesByConversation.getOrPut(conversationId) { MutableStateFlow(emptyList()) }

    private fun updateReadFailure() {
        _syncStatus.value = if (networkAvailable) ChatSyncStatus.Error else ChatSyncStatus.Offline
    }

    private fun inboxRequest(userId: String): String = buildJsonObject {
        put("p_actor_profile_id", userId)
        put("p_limit", InboxPageSize)
    }.toString()

    private fun threadRequest(userId: String, threadId: Long, limit: Int, knownIds: List<Long>): String = buildJsonObject {
        put("p_actor_profile_id", userId)
        put("p_thread_id", threadId)
        put("p_limit", limit)
        put("p_known_message_ids", JsonArray(knownIds.map(::JsonPrimitive)))
    }.toString()

    private fun <T> unsupportedMutation(): Result<T> =
        Result.failure(UnsupportedOperationException("web_chat_mutation_not_implemented"))

    private companion object {
        const val ConversationPrefix = "sb:"
        const val InboxPageSize = 100
        const val ThreadPageSize = 250
        const val DefaultPollIntervalMillis = 30_000L
        const val MinimumPollIntervalMillis = 5_000L
    }
}
