package com.quata.web

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.core.platform.PlatformFile
import com.quata.feature.chat.data.parseChatRpcPayloadEnvelope
import com.quata.feature.chat.data.toChatRpcConversations
import com.quata.feature.chat.data.toChatRpcMessages
import com.quata.feature.chat.domain.ChatConversationCandidatePage
import com.quata.feature.chat.domain.ChatConversationCandidate
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Browser chat repository backed by the same RLS-protected inbox/thread RPCs as Android.
 *
 * Snapshots are obtained by polling. Text/reply and browser-selected attachment writes use the
 * matching RPCs; cache, Realtime and the remaining mutations stay explicitly unavailable.
 */
class WebChatRepository(
    private val rpcClient: WebPostgrestRpcClient,
    private val authRepository: WebAuthRepository,
    private val attachmentUploader: WebChatAttachmentUploader,
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
    ): Result<ChatConversationCandidatePage> = runCatching {
        val userId = currentUserId()
        val body = buildJsonObject {
            put("p_actor_profile_id", userId)
            put("p_query", query.trim())
            put("p_limit", limit.coerceIn(1, 50))
            put("p_offset", offset.coerceAtLeast(0))
        }.toString()
        val result = rpcClient.post("quata_chat_search_conversation_candidates", body)
        val response = result as? WebPostgrestResult.Success
            ?: throw WebPostgrestReadException(result as WebPostgrestResult.Failure)
        response.body.toWebConversationCandidatePage(offset)
    }.onFailure { updateReadFailure() }

    override suspend fun matchRegisteredContactPhones(phoneCandidates: Collection<String>): Result<Set<String>> = unsupportedMutation()

    override suspend fun openPrivateConversation(peerProfileId: String): Result<String> = openThread(
        functionName = "quata_chat_get_or_create_private_thread",
        body = { userId -> buildJsonObject { put("p_actor_profile_id", userId); put("p_peer_profile_id", peerProfileId) }.toString() },
    )

    override suspend fun sendMessage(
        conversationId: String,
        text: String,
        attachmentUri: String?,
        attachmentName: String?,
        attachmentMimeType: String?,
        clientMessageId: String?,
    ): Result<Unit> = sendTextMessage(
        conversationId = conversationId,
        text = text,
        attachmentUri = attachmentUri,
        attachmentName = attachmentName,
        attachmentMimeType = attachmentMimeType,
        replyToMessageId = null,
        clientMessageId = clientMessageId,
    )

    override suspend fun sendReply(
        conversationId: String,
        text: String,
        replyTo: Message,
        attachmentUri: String?,
        attachmentName: String?,
        attachmentMimeType: String?,
        clientMessageId: String?,
    ): Result<Unit> {
        val replyToMessageId = replyTo.id.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("web_chat_invalid_reply_message_id"))
        return sendTextMessage(
            conversationId = conversationId,
            text = text,
            attachmentUri = attachmentUri,
            attachmentName = attachmentName,
            attachmentMimeType = attachmentMimeType,
            replyToMessageId = replyToMessageId,
            clientMessageId = clientMessageId,
        )
    }

    override suspend fun sendSosMessage(
        contactIds: List<String>, text: String, lat: Double?, lng: Double?, accuracy: Double?,
    ): Result<String> = unsupportedMutation()

    override suspend fun cachedPrivateConversationId(userId: String): String? = null

    override suspend fun cachedCommunityConversationId(communityName: String): String? = null

    override suspend fun openCommunityConversation(
        communityId: String, title: String, participantIds: List<String>,
    ): Result<String> = unsupportedMutation()

    override suspend fun openGroupConversation(participantIds: List<String>, title: String?): Result<String> = openThread(
        functionName = "quata_chat_start_thread",
        body = { userId -> buildJsonObject {
            put("p_actor_profile_id", userId)
            put("p_recipient_profile_ids", JsonArray(participantIds.distinct().map(::JsonPrimitive)))
            put("p_subject", title?.let(::JsonPrimitive) ?: JsonNull)
            put("p_type", "group")
            put("p_message", "")
        }.toString() },
    )

    override suspend fun markConversationRead(conversationId: String): Result<Unit> = runCatching {
        val userId = currentUserId()
        val threadId = conversationId.requireWebThreadId()
        _syncStatus.value = ChatSyncStatus.Refreshing
        rpc("quata_chat_mark_thread_read", threadActionRequest(userId, threadId))
        updateConversation(conversationId) { it.copy(unreadCount = 0) }
        _syncStatus.value = ChatSyncStatus.Online
    }.onFailure { updateReadFailure() }

    override suspend fun setConversationMuted(conversationId: String, muted: Boolean): Result<Unit> = runCatching {
        val userId = currentUserId()
        val threadId = conversationId.requireWebThreadId()
        _syncStatus.value = ChatSyncStatus.Refreshing
        rpc("quata_chat_set_muted", mutedRequest(userId, threadId, muted))
        updateConversation(conversationId) { it.copy(isMuted = muted) }
        _syncStatus.value = ChatSyncStatus.Online
    }.onFailure { updateReadFailure() }
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

    private suspend fun openThread(
        functionName: String,
        body: (String) -> String,
    ): Result<String> = runCatching {
        val userId = currentUserId()
        _syncStatus.value = ChatSyncStatus.Refreshing
        val envelope = rpc(functionName, body(userId))
        val mapped = envelope.toChatRpcConversations(userId)
        mergeConversations(mapped)
        mergeMessages(envelope.toChatRpcMessages(userId))
        _syncStatus.value = ChatSyncStatus.Online
        mapped.firstOrNull()?.id ?: throw IllegalStateException("web_chat_thread_response_missing")
    }.onFailure { updateReadFailure() }

    private suspend fun sendTextMessage(
        conversationId: String,
        text: String,
        attachmentUri: String?,
        attachmentName: String?,
        attachmentMimeType: String?,
        replyToMessageId: Long?,
        clientMessageId: String?,
    ): Result<Unit> {
        return runCatching {
            require(text.isNotBlank() || !attachmentUri.isNullOrBlank()) { "web_chat_message_empty" }
            val userId = currentUserId()
            val threadId = conversationId.requireWebThreadId()
            _syncStatus.value = ChatSyncStatus.Refreshing
            val fileIds = attachmentUri
                ?.takeIf { it.isNotBlank() }
                ?.let { reference ->
                    listOf(
                        uploadAndRegisterAttachment(
                            profileId = userId,
                            threadId = threadId,
                            file = PlatformFile(
                                reference = reference,
                                displayName = attachmentName,
                                mimeType = attachmentMimeType,
                            ),
                        ),
                    )
                }
                .orEmpty()
            val envelope = rpc(
                "quata_chat_send_message",
                sendMessageRequest(userId, threadId, text.trim(), fileIds, replyToMessageId, clientMessageId),
            )
            mergeConversations(envelope.toChatRpcConversations(userId))
            mergeMessages(envelope.toChatRpcMessages(userId))
            _syncStatus.value = ChatSyncStatus.Online
        }.onFailure { updateReadFailure() }
    }

    private suspend fun uploadAndRegisterAttachment(
        profileId: String,
        threadId: Long,
        file: PlatformFile,
    ): Long {
        val uploaded = attachmentUploader.upload(profileId, file)
        val body = buildJsonObject {
            put("p_actor_profile_id", profileId)
            put("p_thread_id", threadId)
            put("p_file_url", uploaded.publicUrl)
            put("p_storage_bucket", ChatAttachmentsBucket)
            put("p_storage_path", uploaded.storagePath)
            put("p_mime_type", uploaded.mimeType)
            put("p_name", uploaded.name)
            uploaded.sizeBytes?.let { put("p_size_bytes", it) } ?: put("p_size_bytes", JsonNull)
            put("p_ext", uploaded.extension)
            put("p_thumb", JsonNull)
        }.toString()
        val result = rpcClient.post("quata_chat_register_attachment", body)
        val response = result as? WebPostgrestResult.Success
            ?: throw WebPostgrestReadException(result as WebPostgrestResult.Failure)
        return Json.parseToJsonElement(response.body)
            .jsonObject["id"]
            ?.jsonPrimitive
            ?.longOrNull
            ?.takeIf { it > 0L }
            ?: throw IllegalStateException("web_chat_attachment_registration_missing_id")
    }

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

    private fun mergeConversations(incoming: List<Conversation>) {
        if (incoming.isEmpty()) return
        conversations.value = (conversations.value.associateBy(Conversation::id) + incoming.associateBy(Conversation::id)).values
            .sortedByDescending { it.updatedAtMillis ?: 0L }
    }

    private fun updateConversation(conversationId: String, transform: (Conversation) -> Conversation) {
        conversations.value = conversations.value.map { conversation ->
            if (conversation.id == conversationId) transform(conversation) else conversation
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

    private fun sendMessageRequest(
        userId: String,
        threadId: Long,
        message: String,
        fileIds: List<Long>,
        replyToMessageId: Long?,
        clientMessageId: String?,
    ): String = buildJsonObject {
        put("p_actor_profile_id", userId)
        put("p_thread_id", threadId)
        put("p_message", message)
        put("p_file_ids", JsonArray(fileIds.map(::JsonPrimitive)))
        put("p_reply_to_message_id", replyToMessageId?.let(::JsonPrimitive) ?: JsonNull)
        put("p_client_message_id", clientMessageId?.let(::JsonPrimitive) ?: JsonNull)
    }.toString()

    private fun threadActionRequest(userId: String, threadId: Long): String = buildJsonObject {
        put("p_actor_profile_id", userId)
        put("p_thread_id", threadId)
    }.toString()

    private fun mutedRequest(userId: String, threadId: Long, muted: Boolean): String = buildJsonObject {
        put("p_actor_profile_id", userId)
        put("p_thread_id", threadId)
        put("p_muted", muted)
    }.toString()

    private fun <T> unsupportedMutation(): Result<T> =
        Result.failure(UnsupportedOperationException("web_chat_mutation_not_implemented"))

    private companion object {
        const val ConversationPrefix = "sb:"
        const val InboxPageSize = 100
        const val ThreadPageSize = 250
        const val DefaultPollIntervalMillis = 30_000L
        const val MinimumPollIntervalMillis = 5_000L
        const val ChatAttachmentsBucket = "chat-attachments"
    }
}

private fun String.requireWebThreadId(): Long = removePrefix("sb:").toLongOrNull()
    ?.takeIf { startsWith("sb:") && it > 0L }
    ?: throw IllegalArgumentException("web_chat_invalid_conversation_id")

private fun String.toWebConversationCandidatePage(requestOffset: Int): ChatConversationCandidatePage {
    val root = Json.parseToJsonElement(this).jsonObject
    val candidates = root["items"]?.jsonArray.orEmpty().mapNotNull { item ->
        val candidate = item.jsonObject
        val profileId = candidate["profile_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        ChatConversationCandidate(
            profileId = profileId,
            displayName = candidate["display_name"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "Usuario" },
            neighborhood = candidate["neighborhood"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            phone = candidate["phone"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            avatarUrl = candidate["avatar_url"]?.jsonPrimitive?.contentOrNull,
            sectionKey = candidate["section_key"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "other" },
            neighborhoodGroup = candidate["neighborhood_group"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            existingConversationId = candidate["existing_thread_id"]?.jsonPrimitive?.longOrNull
                ?.takeIf { it > 0L }
                ?.let { "sb:$it" },
        )
    }
    return ChatConversationCandidatePage(
        candidates = candidates,
        hasMore = root["has_more"]?.jsonPrimitive?.booleanOrNull ?: false,
        nextOffset = root["next_offset"]?.jsonPrimitive?.intOrNull ?: requestOffset + candidates.size,
        actorNeighborhood = root["actor_neighborhood"]?.jsonPrimitive?.contentOrNull.orEmpty(),
    )
}
