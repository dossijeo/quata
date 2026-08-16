package com.quata.feature.chat.data

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.core.navigation.AppDestinations
import com.quata.core.platform.PlatformFile
import com.quata.feature.chat.domain.ChatConversationCandidate
import com.quata.feature.chat.domain.ChatConversationCandidatePage
import com.quata.feature.chat.domain.ChatForwardResult
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.domain.ChatSyncStatus
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
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

/** Platform boundary for the authenticated RPC calls used by the shared PostgREST chat protocol. */
interface ChatPostgrestTransport {
    suspend fun post(functionName: String, body: String): ChatPostgrestResponse
}

sealed interface ChatPostgrestResponse {
    data class Success(val body: String) : ChatPostgrestResponse
    data class Failure(val cause: Throwable) : ChatPostgrestResponse
}

/** Session lookup remains platform-owned; the common repository only needs the profile id. */
fun interface ChatAuthenticatedUserProvider {
    suspend fun currentUserId(): String?
}

fun interface ChatAttachmentUploader {
    suspend fun upload(profileId: String, file: PlatformFile): UploadedChatAttachment

    suspend fun deleteUploadedAttachment(uploaded: UploadedChatAttachment): Boolean = false
}

data class UploadedChatAttachment(
    val storagePath: String,
    val publicUrl: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val name: String,
    val extension: String,
)

private data class RetryableOutgoingMessage(
    val conversationId: String,
    val text: String,
    val attachmentUri: String?,
    val attachmentName: String?,
    val attachmentMimeType: String?,
    val replyToMessageId: Long?,
    val clientMessageId: String,
    val registeredAttachmentIds: List<Long>,
)

/**
 * Portable chat implementation for the existing PostgREST RPC contract.
 *
 * The backend's current permissions are deliberately preserved while Web and iOS are migrated:
 * this class sends the same authenticated RPCs Android uses.  Hosts may provide a realtime
 * transport later, but no mutation is hidden behind an unsupported placeholder.
 */
open class PostgrestChatRepository(
    private val transport: ChatPostgrestTransport,
    private val authenticatedUser: ChatAuthenticatedUserProvider,
    private val attachmentUploader: ChatAttachmentUploader,
    private val pollIntervalMillis: Long = DefaultPollIntervalMillis,
    private val realtimeGateway: ChatRealtimeGateway? = null,
) : ChatRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val conversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val messagesByConversation = mutableMapOf<String, MutableStateFlow<List<Message>>>()
    private val _activeConversationId = MutableStateFlow<String?>(null)
    private val _isAppForeground = MutableStateFlow(true)
    private val _pendingDeletedConversation = MutableStateFlow<Conversation?>(null)
    private val realtimeOnlineState = MutableStateFlow(false)
    private val _typingProfileIds = MutableStateFlow<Set<String>>(emptySet())
    private val _syncStatus = MutableStateFlow(ChatSyncStatus.Offline)
    private var networkAvailable = true
    private var currentUserSnapshot: User? = null
    private val retryableOutgoing = mutableMapOf<String, RetryableOutgoingMessage>()

    override val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()
    override val isAppForeground: StateFlow<Boolean> = _isAppForeground.asStateFlow()
    override val pendingDeletedConversation: StateFlow<Conversation?> = _pendingDeletedConversation.asStateFlow()
    override val isRealtimeOnline: StateFlow<Boolean> = realtimeGateway?.isOnline ?: realtimeOnlineState.asStateFlow()
    override val typingProfileIds: StateFlow<Set<String>> = realtimeGateway?.typingProfileIds ?: _typingProfileIds.asStateFlow()
    override val syncStatus: StateFlow<ChatSyncStatus> = _syncStatus.asStateFlow()

    init {
        realtimeGateway?.let { gateway ->
            // Read the stream eagerly: construction is the subscription boundary and tests can
            // detect accidental removal even before the collector is scheduled.
            val changeStream = gateway.changes
            scope.launch {
                changeStream.collect { change -> refreshForRealtimeChange(change) }
            }
        }
    }
    override fun setDeviceNetworkAvailable(isAvailable: Boolean) {
        networkAvailable = isAvailable
        realtimeGateway?.setNetworkAvailable(isAvailable)
        _syncStatus.value = if (isAvailable) ChatSyncStatus.Refreshing else ChatSyncStatus.Offline
    }
    override fun currentUser(): User? = currentUserSnapshot
    override fun setActiveConversation(conversationId: String?) { _activeConversationId.value = conversationId; realtimeGateway?.setVisibleConversation(conversationId) }
    override fun setConversationVisible(conversationId: String, visible: Boolean) {
        if (visible) {
            _activeConversationId.value = conversationId
            realtimeGateway?.setVisibleConversation(conversationId)
        } else if (_activeConversationId.value == conversationId) {
            _activeConversationId.value = null
            realtimeGateway?.setVisibleConversation(null)
        }
    }
    override fun setAppForeground(isForeground: Boolean) { _isAppForeground.value = isForeground; realtimeGateway?.setForeground(isForeground) }
    override fun setTyping(conversationId: String, isTyping: Boolean) { realtimeGateway?.setTyping(conversationId, isTyping) }
    override fun cleanupEmptyConversation(conversationId: String) {
        if (conversationId == AppDestinations.FavoriteMessagesConversationId) return
        val conversation = conversations.value.firstOrNull { it.id == conversationId }
        if (!shouldCleanupEmptyPrivateConversation(conversation, messagesByConversation[conversationId]?.value.orEmpty())) return
        scope.launch {
            runCatching {
                val userId = currentUserId()
                val threadId = conversationId.requirePostgrestThreadId()
                val payload = transport.post(
                    "quata_chat_cleanup_empty_private_thread",
                    threadActionRequest(userId, threadId),
                ).successOrThrow()
                val deleted = Json.parseToJsonElement(payload).jsonObject["deleted"]?.jsonPrimitive?.booleanOrNull == true
                if (deleted) {
                    messagesByConversation.remove(conversationId)
                    conversations.value = conversations.value.filterNot { it.id == conversationId }
                }
            }.onFailure { updateReadFailure() }
        }
    }
    override fun clearChatNotifications() = Unit
    override suspend fun getConversations(): Result<List<Conversation>> = refreshInbox()
    override fun observeConversations(): Flow<List<Conversation>> = flow {
        while (currentCoroutineContext().isActive) {
            awaitForeground()
            refreshInbox()
            emit(conversations.value)
            delay(pollIntervalMillis.coerceAtLeast(MinimumPollIntervalMillis))
        }
    }
    override fun observeMessages(conversationId: String): Flow<List<Message>> = flow {
        val state = messagesState(conversationId)
        while (currentCoroutineContext().isActive) {
            awaitForeground()
            if (conversationId == AppDestinations.FavoriteMessagesConversationId) refreshFavorites().getOrThrow()
            else { awaitActiveConversation(conversationId); refreshThread(conversationId, ThreadPageSize) }
            emit(state.value)
            delay(pollIntervalMillis.coerceAtLeast(MinimumPollIntervalMillis))
        }
    }
    override suspend fun loadOlderMessages(conversationId: String, limit: Int): Result<Boolean> = runCatching {
        if (conversationId == AppDestinations.FavoriteMessagesConversationId) return@runCatching false
        refreshThread(conversationId, limit.coerceAtLeast(1)).getOrThrow().size >= limit
    }
    override fun observeParticipantCandidates(): Flow<List<User>> = flow {
        val page = searchConversationCandidates(query = "", limit = CandidatePageSize, offset = 0).getOrThrow()
        emit(page.candidates.map {
            User(it.profileId, "", it.displayName, it.neighborhood, it.avatarUrl)
        })
    }
    override suspend fun searchConversationCandidates(query: String, limit: Int, offset: Int): Result<ChatConversationCandidatePage> = runCatching {
        val userId = currentUserId()
        val body = buildJsonObject {
            put("p_actor_profile_id", userId); put("p_query", query.trim())
            put("p_limit", limit.coerceIn(1, 50)); put("p_offset", offset.coerceAtLeast(0))
        }.toString()
        val response = transport.post("quata_chat_search_conversation_candidates", body).successOrThrow()
        response.toChatConversationCandidatePage(offset)
    }.onFailure { updateReadFailure() }
    override suspend fun matchRegisteredContactPhones(phoneCandidates: Collection<String>): Result<Set<String>> = runCatching {
        val candidates = phoneCandidates.map(String::trim).filter { it.isNotEmpty() }.distinct()
        if (candidates.isEmpty()) return@runCatching emptySet()
        val userId = currentUserId()
        val body = buildJsonObject {
            put("p_actor_profile_id", userId)
            put("p_phone_candidates", JsonArray(candidates.map(::JsonPrimitive)))
        }.toString()
        val root = Json.parseToJsonElement(transport.post("quata_chat_match_registered_contacts", body).successOrThrow()).jsonObject
        root["matched_phones"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }.toSet()
    }.onFailure { updateReadFailure() }
    override suspend fun openPrivateConversation(peerProfileId: String): Result<String> = openThread("quata_chat_get_or_create_private_thread") { userId ->
        buildJsonObject { put("p_actor_profile_id", userId); put("p_peer_profile_id", peerProfileId) }.toString()
    }
    override suspend fun sendMessage(conversationId: String, text: String, attachmentUri: String?, attachmentName: String?, attachmentMimeType: String?, clientMessageId: String?): Result<Unit> =
        sendTextMessage(conversationId, text, attachmentUri, attachmentName, attachmentMimeType, null, clientMessageId)
    override suspend fun sendReply(conversationId: String, text: String, replyTo: Message, attachmentUri: String?, attachmentName: String?, attachmentMimeType: String?, clientMessageId: String?): Result<Unit> {
        val replyId = replyTo.id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("web_chat_invalid_reply_message_id"))
        return sendTextMessage(conversationId, text, attachmentUri, attachmentName, attachmentMimeType, replyId, clientMessageId)
    }
    override suspend fun sendSosMessage(contactIds: List<String>, text: String, lat: Double?, lng: Double?, accuracy: Double?): Result<String> = runCatching {
        val userId = currentUserId()
        val body = buildJsonObject {
            put("p_actor_profile_id", userId)
            put("p_contact_profile_ids", JsonArray(contactIds.distinct().map(::JsonPrimitive)))
            put("p_message", text)
            put("p_lat", lat?.let(::JsonPrimitive) ?: JsonNull)
            put("p_lng", lng?.let(::JsonPrimitive) ?: JsonNull)
            put("p_accuracy", accuracy?.let(::JsonPrimitive) ?: JsonNull)
        }.toString()
        val rawPayload = transport.post("quata_chat_send_sos", body).successOrThrow()
        val rawRoot = Json.parseToJsonElement(rawPayload).jsonObject
        val envelope = parseChatRpcPayloadEnvelope(rawRoot)
        mergeConversations(envelope.toChatRpcConversations(userId)); mergeMessages(envelope.toChatRpcMessages(userId))
        val threadId = rawRoot["thread_id"]?.jsonPrimitive?.longOrNull
            ?: rawRoot["thread"]?.jsonObject?.get("thread_id")?.jsonPrimitive?.longOrNull
            ?: envelope.toChatRpcConversations(userId).firstOrNull()?.id?.threadIdForRefresh()
            ?: throw IllegalStateException("chat_sos_thread_missing")
        _syncStatus.value = ChatSyncStatus.Online
        "sb:$threadId"
    }.onFailure { updateReadFailure() }
    override suspend fun cachedPrivateConversationId(userId: String): String? {
        val current = currentUserId()
        return conversations.value.firstOrNull { conversation ->
            !conversation.isGroup && !conversation.isEmergency &&
                conversation.participantIds.containsAll(listOf(current, userId)) && conversation.participantIds.size == 2
        }?.id ?: openPrivateConversation(userId).getOrNull()
    }
    override suspend fun cachedCommunityConversationId(communityName: String): String? =
        conversations.value.firstOrNull { conversation ->
            conversation.communityName.equals(communityName, ignoreCase = true) ||
                conversation.title.equals(communityName, ignoreCase = true)
        }?.id
    override suspend fun openCommunityConversation(communityId: String, title: String, participantIds: List<String>): Result<String> = openThread("quata_chat_open_community_thread") { userId ->
        buildJsonObject {
            put("p_actor_profile_id", userId); put("p_community_id", communityId); put("p_title", title)
        }.toString()
    }
    override suspend fun openGroupConversation(participantIds: List<String>, title: String?): Result<String> = openThread("quata_chat_start_thread") { userId ->
        buildJsonObject {
            put("p_actor_profile_id", userId); put("p_recipient_profile_ids", JsonArray(participantIds.distinct().map(::JsonPrimitive)))
            put("p_subject", title?.let(::JsonPrimitive) ?: JsonNull); put("p_type", "group"); put("p_message", "")
        }.toString()
    }
    override suspend fun markConversationRead(conversationId: String): Result<Unit> = runCatching {
        val userId = currentUserId(); val threadId = conversationId.requirePostgrestThreadId(); _syncStatus.value = ChatSyncStatus.Refreshing
        rpc("quata_chat_mark_thread_read", threadActionRequest(userId, threadId)); updateConversation(conversationId) { it.copy(unreadCount = 0) }; _syncStatus.value = ChatSyncStatus.Online
    }.onFailure { updateReadFailure() }
    override suspend fun setConversationMuted(conversationId: String, muted: Boolean): Result<Unit> = runCatching {
        val userId = currentUserId(); val threadId = conversationId.requirePostgrestThreadId(); _syncStatus.value = ChatSyncStatus.Refreshing
        rpc("quata_chat_set_muted", mutedRequest(userId, threadId, muted)); updateConversation(conversationId) { it.copy(isMuted = muted) }; _syncStatus.value = ChatSyncStatus.Online
    }.onFailure { updateReadFailure() }
    override suspend fun setMemberInvitesEnabled(conversationId: String, enabled: Boolean): Result<Unit> = threadMutation(
        functionName = "quata_chat_set_member_invites_enabled", conversationId = conversationId,
        body = { userId, threadId -> buildJsonObject { put("p_actor_profile_id", userId); put("p_thread_id", threadId); put("p_enabled", enabled) }.toString() },
        after = { updateConversation(conversationId) { it.copy(canMembersInvite = enabled) } },
    )
    override suspend fun addParticipants(conversationId: String, participantIds: List<String>): Result<Unit> = threadMutation(
        functionName = "quata_chat_add_participants", conversationId = conversationId,
        body = { userId, threadId -> buildJsonObject { put("p_actor_profile_id", userId); put("p_thread_id", threadId); put("p_participant_profile_ids", JsonArray(participantIds.distinct().map(::JsonPrimitive))) }.toString() },
        after = { refreshThread(conversationId, ThreadPageSize).getOrThrow(); refreshInbox().getOrThrow() },
    )
    override suspend fun promoteModerator(conversationId: String, userId: String): Result<Unit> = participantMutation("quata_chat_promote_moderator", conversationId, userId)
    override suspend fun demoteModerator(conversationId: String, userId: String): Result<Unit> = participantMutation("quata_chat_demote_moderator", conversationId, userId)
    override suspend fun removeParticipant(conversationId: String, userId: String): Result<Unit> = participantMutation("quata_chat_remove_participant", conversationId, userId)
    override suspend fun blockParticipant(conversationId: String, userId: String): Result<Unit> = participantMutation("quata_chat_block_participant", conversationId, userId) {
        updateConversation(conversationId) { it.copy(blockedUserIds = (it.blockedUserIds + userId).distinct()) }
    }
    override suspend fun reportMessage(messageId: String): Result<Unit> = runCatching {
        val userId = currentUserId()
        rpc("quata_ugc_report", buildJsonObject {
            put("p_actor_profile_id", userId); put("p_target_type", "chat_message"); put("p_target_id", messageId); put("p_reason", "user_report"); put("p_details", JsonNull)
        }.toString()); _syncStatus.value = ChatSyncStatus.Online
    }.onFailure { updateReadFailure() }
    override suspend fun leaveConversation(conversationId: String): Result<Unit> = removeThreadFromInbox("quata_chat_leave_thread", conversationId, retainUndo = false)
    /**
     * Current backend compatibility: `quata_chat_delete_thread` is a reversible per-member inbox
     * removal. Hide intentionally maps to it and retains undo; it is not presented as hard delete.
     */
    override suspend fun hideConversation(conversationId: String): Result<Unit> =
        removeThreadFromInbox("quata_chat_delete_thread", conversationId, retainUndo = true)

    /**
     * The deployment has no separate hard-delete RPC. A user-confirmed delete therefore requests
     * the same reversible legacy removal, while remaining a distinct domain/UI intent.
     */
    override suspend fun deleteConversation(conversationId: String): Result<Unit> =
        removeThreadFromInbox("quata_chat_delete_thread", conversationId, retainUndo = true)
    override suspend fun restorePendingDeletedConversation(): Result<Unit> = runCatching {
        val conversation = _pendingDeletedConversation.value ?: return@runCatching
        val userId = currentUserId(); rpc("quata_chat_restore_thread", threadActionRequest(userId, conversation.id.requirePostgrestThreadId()))
        _pendingDeletedConversation.value = null; refreshInbox().getOrThrow()
    }.onFailure { updateReadFailure() }
    override suspend fun finalizePendingDeletedConversation(): Result<Unit> = runCatching { _pendingDeletedConversation.value = null }
    override suspend fun editMessage(messageId: String, text: String): Result<Unit> = messageMutation("quata_chat_edit_message", messageId) { userId, threadId, numericMessageId ->
        buildJsonObject { put("p_actor_profile_id", userId); put("p_thread_id", threadId); put("p_message_id", numericMessageId); put("p_message", text.trim()) }.toString()
    }
    override suspend fun deleteMessage(messageId: String): Result<Unit> = messageMutation("quata_chat_delete_messages", messageId) { userId, threadId, numericMessageId ->
        buildJsonObject { put("p_actor_profile_id", userId); put("p_thread_id", threadId); put("p_message_ids", JsonArray(listOf(JsonPrimitive(numericMessageId)))) }.toString()
    }
    override suspend fun toggleFavoriteMessage(messageId: String): Result<Unit> = runCatching {
        val message = allMessages().firstOrNull { it.id == messageId } ?: throw IllegalArgumentException("chat_message_not_loaded")
        val userId = currentUserId(); val threadId = message.conversationId.requirePostgrestThreadId(); val numericMessageId = message.id.toLongOrNull() ?: throw IllegalArgumentException("chat_message_id_invalid")
        rpc("quata_chat_set_favorite", buildJsonObject { put("p_actor_profile_id", userId); put("p_thread_id", threadId); put("p_message_id", numericMessageId); put("p_favorite", !message.isFavorite) }.toString())
        refreshThread(message.conversationId, ThreadPageSize).getOrThrow()
        refreshFavorites().getOrThrow()
        _syncStatus.value = ChatSyncStatus.Online
    }.onFailure { updateReadFailure() }
    override suspend fun forwardMessage(message: Message, conversationIds: List<String>): Result<ChatForwardResult> = runCatching {
        val userId = currentUserId(); val numericMessageId = message.id.toLongOrNull() ?: throw IllegalArgumentException("chat_message_id_invalid")
        val threadIds = conversationIds.map(String::requirePostgrestThreadId).distinct()
        if (threadIds.isEmpty()) return@runCatching ChatForwardResult(requestedCount = 0, sentCount = 0)
        val payload = transport.post("quata_chat_forward_message", buildJsonObject { put("p_actor_profile_id", userId); put("p_message_id", numericMessageId); put("p_thread_ids", JsonArray(threadIds.map(::JsonPrimitive))) }.toString()).successOrThrow()
        val result = parseChatForwardResult(payload, requestedCount = threadIds.size)
        refreshInbox().getOrThrow(); _syncStatus.value = ChatSyncStatus.Online
        result
    }.onFailure { updateReadFailure() }
    override suspend fun flushPendingMessages(): Boolean {
        if (!networkAvailable) return false
        return retryableOutgoing.keys.toList().all { retryPendingMessage(it).isSuccess }
    }
    override suspend fun retryPendingMessage(clientMessageId: String): Result<Unit> {
        val pending = retryableOutgoing[clientMessageId]
            ?: return Result.failure(IllegalArgumentException("chat_retry_message_missing"))
        return sendTextMessage(
            conversationId = pending.conversationId,
            text = pending.text,
            attachmentUri = pending.attachmentUri,
            attachmentName = pending.attachmentName,
            attachmentMimeType = pending.attachmentMimeType,
            replyToMessageId = pending.replyToMessageId,
            clientMessageId = pending.clientMessageId,
            registeredAttachmentIds = pending.registeredAttachmentIds,
        )
    }

    private suspend fun refreshInbox(): Result<List<Conversation>> = runCatching {
        val userId = currentUserId(); _syncStatus.value = ChatSyncStatus.Refreshing
        val envelope = rpc("quata_chat_get_inbox", inboxRequest(userId)); updateCurrentUserFrom(envelope, userId); val mapped = envelope.toChatRpcConversations(userId).sortedByDescending { it.updatedAtMillis ?: 0L }
        conversations.value = mapped; mergeMessages(envelope.toChatRpcMessages(userId)); _syncStatus.value = ChatSyncStatus.Online; mapped
    }.onFailure { updateReadFailure() }

    /** Realtime is authoritative for wakeups; polling remains only a bounded fallback. */
    private suspend fun refreshForRealtimeChange(change: ChatRealtimeChange) {
        if (!_isAppForeground.value || !networkAvailable) return
        val userId = runCatching { currentUserId() }.getOrNull() ?: return
        val conversationId = change.threadId?.let { "sb:$it" }
        when (change.table) {
            "chat_message_favorites" -> refreshFavorites()
            "chat_messages", "chat_attachments", "chat_message_reads", "chat_message_states" -> {
                conversationId?.let { refreshThread(it, ThreadPageSize) }
                _activeConversationId.value
                    ?.takeIf { it != AppDestinations.FavoriteMessagesConversationId && it != conversationId }
                    ?.let { refreshThread(it, ThreadPageSize) }
                refreshInbox()
            }
            "chat_threads", "chat_participants" -> {
                conversationId?.let { refreshThread(it, ThreadPageSize) }
                refreshInbox()
            }
            else -> refreshInbox()
        }
        _syncStatus.value = if (isRealtimeOnline.value) ChatSyncStatus.Online else ChatSyncStatus.Refreshing
    }
    private suspend fun openThread(functionName: String, body: (String) -> String): Result<String> = runCatching {
        val userId = currentUserId(); _syncStatus.value = ChatSyncStatus.Refreshing
        val envelope = rpc(functionName, body(userId)); val mapped = envelope.toChatRpcConversations(userId)
        mergeConversations(mapped); mergeMessages(envelope.toChatRpcMessages(userId)); _syncStatus.value = ChatSyncStatus.Online
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
        registeredAttachmentIds: List<Long> = emptyList(),
    ): Result<Unit> {
        var reusableAttachmentIds = registeredAttachmentIds
        return runCatching {
        require(text.isNotBlank() || !attachmentUri.isNullOrBlank()) { "web_chat_message_empty" }
        val userId = currentUserId(); val threadId = conversationId.requirePostgrestThreadId(); _syncStatus.value = ChatSyncStatus.Refreshing
        val fileIds = if (reusableAttachmentIds.isNotEmpty()) {
            reusableAttachmentIds
        } else {
            attachmentUri?.takeIf { it.isNotBlank() }?.let { reference ->
                listOf(uploadAndRegisterAttachment(userId, threadId, PlatformFile(reference, attachmentName, attachmentMimeType)))
                    .also { reusableAttachmentIds = it }
            }.orEmpty()
        }
        val envelope = rpc("quata_chat_send_message", sendMessageRequest(userId, threadId, text.trim(), fileIds, replyToMessageId, clientMessageId))
        mergeConversations(envelope.toChatRpcConversations(userId)); mergeMessages(envelope.toChatRpcMessages(userId)); clientMessageId?.let(retryableOutgoing::remove); _syncStatus.value = ChatSyncStatus.Online
    }.onFailure {
        clientMessageId?.takeIf(String::isNotBlank)?.let { id ->
            retryableOutgoing[id] = RetryableOutgoingMessage(conversationId, text, attachmentUri, attachmentName, attachmentMimeType, replyToMessageId, id, reusableAttachmentIds)
        }
        updateReadFailure()
    }
    }
    private suspend fun threadMutation(
        functionName: String,
        conversationId: String,
        body: (String, Long) -> String,
        after: suspend () -> Unit = {},
    ): Result<Unit> = runCatching {
        val userId = currentUserId(); val threadId = conversationId.requirePostgrestThreadId(); _syncStatus.value = ChatSyncStatus.Refreshing
        rpc(functionName, body(userId, threadId)); after(); _syncStatus.value = ChatSyncStatus.Online
    }.onFailure { updateReadFailure() }
    private suspend fun participantMutation(
        functionName: String,
        conversationId: String,
        participantId: String,
        after: suspend () -> Unit = { refreshThread(conversationId, ThreadPageSize).getOrThrow() },
    ): Result<Unit> = threadMutation(functionName, conversationId, { userId, threadId ->
        buildJsonObject { put("p_actor_profile_id", userId); put("p_thread_id", threadId); put("p_profile_id", participantId) }.toString()
    }, after)
    private suspend fun removeThreadFromInbox(functionName: String, conversationId: String, retainUndo: Boolean): Result<Unit> = runCatching {
        val userId = currentUserId(); val threadId = conversationId.requirePostgrestThreadId(); val conversation = conversations.value.firstOrNull { it.id == conversationId }
        rpc(functionName, threadActionRequest(userId, threadId))
        if (retainUndo) _pendingDeletedConversation.value = conversation
        conversations.value = conversations.value.filterNot { it.id == conversationId }
        messagesByConversation.remove(conversationId); if (_activeConversationId.value == conversationId) _activeConversationId.value = null
        _syncStatus.value = ChatSyncStatus.Online
    }.onFailure { updateReadFailure() }
    private suspend fun messageMutation(
        functionName: String,
        messageId: String,
        body: (String, Long, Long) -> String,
    ): Result<Unit> = runCatching {
        val message = allMessages().firstOrNull { it.id == messageId } ?: throw IllegalArgumentException("chat_message_not_loaded")
        val userId = currentUserId(); val threadId = message.conversationId.requirePostgrestThreadId(); val numericMessageId = message.id.toLongOrNull() ?: throw IllegalArgumentException("chat_message_id_invalid")
        rpc(functionName, body(userId, threadId, numericMessageId)); refreshThread(message.conversationId, ThreadPageSize).getOrThrow(); _syncStatus.value = ChatSyncStatus.Online
    }.onFailure { updateReadFailure() }
    private fun allMessages(): List<Message> = messagesByConversation.values.flatMap { it.value }
    private suspend fun uploadAndRegisterAttachment(profileId: String, threadId: Long, file: PlatformFile): Long {
        val uploaded = attachmentUploader.upload(profileId, file)
        val body = buildJsonObject {
            put("p_actor_profile_id", profileId); put("p_thread_id", threadId); put("p_file_url", uploaded.publicUrl); put("p_storage_bucket", ChatAttachmentsBucket)
            put("p_storage_path", uploaded.storagePath); put("p_mime_type", uploaded.mimeType); put("p_name", uploaded.name); uploaded.sizeBytes?.let { put("p_size_bytes", it) } ?: put("p_size_bytes", JsonNull)
            put("p_ext", uploaded.extension); put("p_thumb", JsonNull)
        }.toString()
        return try {
            Json.parseToJsonElement(transport.post("quata_chat_register_attachment", body).successOrThrow()).jsonObject["id"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0L }
                ?: throw IllegalStateException("web_chat_attachment_registration_missing_id")
        } catch (error: Throwable) {
            runCatching { attachmentUploader.deleteUploadedAttachment(uploaded) }
            throw error
        }
    }
    private suspend fun refreshThread(conversationId: String, limit: Int): Result<List<Message>> = runCatching {
        val userId = currentUserId(); val threadId = conversationId.threadIdForRefresh(); _syncStatus.value = ChatSyncStatus.Refreshing
        val knownIds = messagesState(conversationId).value.mapNotNull { it.id.toLongOrNull() }
        val envelope = rpc("quata_chat_get_thread", threadRequest(userId, threadId, limit, knownIds))
        updateCurrentUserFrom(envelope, userId)
        mergeConversations(envelope.toChatRpcConversations(userId))
        mergeMessages(envelope.toChatRpcMessages(userId)); _syncStatus.value = ChatSyncStatus.Online; messagesState(conversationId).value
    }.onFailure { updateReadFailure() }
    private suspend fun refreshFavorites(): Result<List<Message>> = runCatching {
        val userId = currentUserId(); _syncStatus.value = ChatSyncStatus.Refreshing
        val envelope = rpc("quata_chat_get_favorites", buildJsonObject { put("p_actor_profile_id", userId); put("p_limit", FavoritesPageSize) }.toString())
        val favorites = envelope.toChatRpcMessages(userId).filter { it.isFavorite && !it.isDeleted }
            .sortedByDescending { it.sentAtMillis ?: Long.MIN_VALUE }
        messagesState(AppDestinations.FavoriteMessagesConversationId).value = favorites
        _syncStatus.value = ChatSyncStatus.Online; favorites
    }.onFailure { updateReadFailure() }
    private suspend fun currentUserId(): String {
        if (!networkAvailable) throw IllegalStateException("web_chat_offline")
        val id = authenticatedUser.currentUserId() ?: throw IllegalStateException("web_chat_session_missing")
        // Identity is the authenticated profile id.  Profile display information comes from the
        // server payload; never invent a user/persona when the session only grants an id.
        if (currentUserSnapshot?.id != id) currentUserSnapshot = User(id = id, email = "", displayName = "")
        return id
    }
    private fun updateCurrentUserFrom(envelope: ChatRpcPayloadEnvelope, userId: String) {
        envelope.profileRecords().firstOrNull { it.id == userId }?.let { profile ->
            currentUserSnapshot = User(userId, "", profile.resolvedDisplayName(), profile.neighborhood.orEmpty(), profile.avatarUrl)
        }
    }
    private suspend fun rpc(functionName: String, body: String): ChatRpcPayloadEnvelope = Json.parseToJsonElement(transport.post(functionName, body).successOrThrow()).let(::parseChatRpcPayloadEnvelope)
    private fun mergeMessages(incoming: List<Message>) { incoming.groupBy(Message::conversationId).forEach { (id, messages) -> val old = messagesState(id).value.associateBy(Message::id); messagesState(id).value = (old + messages.associateBy(Message::id)).values.sortedBy { it.sentAtMillis ?: Long.MIN_VALUE } } }
    private fun mergeConversations(incoming: List<Conversation>) { if (incoming.isNotEmpty()) conversations.value = (conversations.value.associateBy(Conversation::id) + incoming.associateBy(Conversation::id)).values.sortedByDescending { it.updatedAtMillis ?: 0L } }
    private fun updateConversation(id: String, transform: (Conversation) -> Conversation) { conversations.value = conversations.value.map { if (it.id == id) transform(it) else it } }
    private fun messagesState(id: String) = messagesByConversation.getOrPut(id) { MutableStateFlow(emptyList()) }
    private suspend fun awaitForeground() { if (!_isAppForeground.value) _isAppForeground.filter { it }.first() }
    private suspend fun awaitActiveConversation(id: String) { if (_activeConversationId.value != id) _activeConversationId.filter { it == id }.first() }
    private fun updateReadFailure() { _syncStatus.value = if (networkAvailable) ChatSyncStatus.Error else ChatSyncStatus.Offline }
    private fun inboxRequest(userId: String) = buildJsonObject { put("p_actor_profile_id", userId); put("p_limit", InboxPageSize) }.toString()
    private fun threadRequest(userId: String, threadId: Long, limit: Int, knownIds: List<Long>) = buildJsonObject { put("p_actor_profile_id", userId); put("p_thread_id", threadId); put("p_limit", limit); put("p_known_message_ids", JsonArray(knownIds.map(::JsonPrimitive))) }.toString()
    private fun sendMessageRequest(userId: String, threadId: Long, message: String, fileIds: List<Long>, replyTo: Long?, clientId: String?) = buildJsonObject { put("p_actor_profile_id", userId); put("p_thread_id", threadId); put("p_message", message); put("p_file_ids", JsonArray(fileIds.map(::JsonPrimitive))); put("p_reply_to_message_id", replyTo?.let(::JsonPrimitive) ?: JsonNull); put("p_client_message_id", clientId?.let(::JsonPrimitive) ?: JsonNull) }.toString()
    private fun threadActionRequest(userId: String, threadId: Long) = buildJsonObject { put("p_actor_profile_id", userId); put("p_thread_id", threadId) }.toString()
    private fun mutedRequest(userId: String, threadId: Long, muted: Boolean) = buildJsonObject { put("p_actor_profile_id", userId); put("p_thread_id", threadId); put("p_muted", muted) }.toString()
    private companion object { const val ConversationPrefix = "sb:"; const val InboxPageSize = 100; const val ThreadPageSize = 250; const val FavoritesPageSize = 250; const val CandidatePageSize = 100; const val DefaultPollIntervalMillis = 30_000L; const val MinimumPollIntervalMillis = 5_000L; const val ChatAttachmentsBucket = "chat-attachments" }
}

internal fun shouldCleanupEmptyPrivateConversation(conversation: Conversation?, messages: List<Message>): Boolean =
    conversation?.isGroup != true && conversation?.isEmergency != true && messages.isEmpty()

private fun ChatPostgrestResponse.successOrThrow(): String = when (this) {
    is ChatPostgrestResponse.Success -> body
    is ChatPostgrestResponse.Failure -> throw cause
}
internal fun parseChatForwardResult(payload: String, requestedCount: Int): ChatForwardResult {
    val root = Json.parseToJsonElement(payload).jsonObject
    val sentCount = root["sent"]?.jsonObject?.size ?: 0
    val errorCount = root["errors"]?.jsonArray?.size ?: 0
    return ChatForwardResult(requestedCount = requestedCount, sentCount = sentCount, errorCount = errorCount)
}
private fun String.requirePostgrestThreadId(): Long = removePrefix("sb:").toLongOrNull()?.takeIf { startsWith("sb:") && it > 0L } ?: throw IllegalArgumentException("web_chat_invalid_conversation_id")
private fun String.threadIdForRefresh(): Long = removePrefix("sb:").toLongOrNull()
    ?.takeIf { it > 0L }
    ?: throw IllegalArgumentException("web_chat_invalid_conversation_id")

fun String.toChatConversationCandidatePage(requestOffset: Int): ChatConversationCandidatePage {
    val root = Json.parseToJsonElement(this).jsonObject
    val candidates = root["items"]?.jsonArray.orEmpty().mapNotNull { item ->
        val candidate = item.jsonObject; val id = candidate["profile_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        ChatConversationCandidate(id, candidate["display_name"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "Usuario" }, candidate["neighborhood"]?.jsonPrimitive?.contentOrNull.orEmpty(), candidate["phone"]?.jsonPrimitive?.contentOrNull.orEmpty(), candidate["avatar_url"]?.jsonPrimitive?.contentOrNull, candidate["section_key"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "other" }, candidate["neighborhood_group"]?.jsonPrimitive?.contentOrNull.orEmpty(), candidate["existing_thread_id"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0L }?.let { "sb:$it" })
    }
    return ChatConversationCandidatePage(candidates, root["has_more"]?.jsonPrimitive?.booleanOrNull ?: false, root["next_offset"]?.jsonPrimitive?.intOrNull ?: requestOffset + candidates.size, root["actor_neighborhood"]?.jsonPrimitive?.contentOrNull.orEmpty())
}
