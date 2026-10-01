package com.quata.feature.chat.data

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.core.navigation.AppDestinations
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.FileCacheService
import com.quata.core.platform.PlatformResult
import com.quata.feature.chat.domain.ChatConversationCandidate
import com.quata.feature.chat.domain.ChatConversationCandidatePage
import com.quata.feature.chat.domain.ChatConversationCursor
import com.quata.feature.chat.domain.ChatConversationPage
import com.quata.feature.chat.domain.ChatForwardResult
import com.quata.feature.chat.domain.ChatFavoriteCursor
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.domain.SosRateLimitException
import com.quata.feature.chat.domain.ChatSyncStatus
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Platform boundary for the authenticated RPC calls used by the shared PostgREST chat protocol. */
fun interface ChatPostgrestTransport {
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

/**
 * Portable chat implementation for the existing PostgREST RPC contract.
 *
 * The backend's current permissions are deliberately preserved while Web and iOS are migrated:
 * this class sends the same authenticated RPCs Android uses.  Hosts may provide a realtime
 * transport later, but no mutation is hidden behind an unsupported placeholder.
 */
@OptIn(ExperimentalTime::class)
open class PostgrestChatRepository(
    private val transport: ChatPostgrestTransport,
    private val authenticatedUser: ChatAuthenticatedUserProvider,
    private val attachmentUploader: ChatAttachmentUploader,
    private val pollIntervalMillis: Long = DefaultPollIntervalMillis,
    private val realtimeGateway: ChatRealtimeGateway? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val outgoingStore: ChatOutgoingStore = MemoryChatOutgoingStore(),
    private val outboxFiles: FileCacheService = MemoryChatFileCacheService(),
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val outgoingExecutionLock: ChatOutgoingExecutionLock = LocalChatOutgoingExecutionLock,
) : ChatRepository {
    private val conversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val messagesByConversation = mutableMapOf<String, MutableStateFlow<List<Message>>>()
    private val _activeConversationId = MutableStateFlow<String?>(null)
    private val _isAppForeground = MutableStateFlow(true)
    private val _pendingDeletedConversation = MutableStateFlow<Conversation?>(null)
    private val realtimeOnlineState = MutableStateFlow(false)
    private val _typingProfileIds = MutableStateFlow<Set<String>>(emptySet())
    private val _syncStatus = MutableStateFlow(ChatSyncStatus.Offline)
    private val observedNetworkAvailable = MutableStateFlow(true)
    val isDeviceNetworkAvailable: StateFlow<Boolean> =
        realtimeGateway?.isNetworkAvailable ?: observedNetworkAvailable.asStateFlow()
    private val networkAvailable: Boolean
        get() = isDeviceNetworkAvailable.value
    private var currentUserSnapshot: User? = null
    private val retryableOutgoing = mutableMapOf<String, StoredChatOutgoing>()
    private val outboxMutex = Mutex()
    private val favoritesMutex = Mutex()
    private var loadedOutboxActorId: String? = null
    private var loadedInboxPageCount: Int = 0
    private var favoritesOwnerActorId: String? = null
    private var favoritesGeneration: Long = 0L
    private var loadedFavoritesPageCount: Int = 0
    private var favoritesNextCursor: ChatFavoriteCursor? = null
    private var favoritesHasMore: Boolean = false
    private var networkRecoveryJob: Job? = null
    private val deliveryAcknowledgements = ChatDeliveryAcknowledgements(
        currentActor = { if (networkAvailable) authenticatedUser.currentUserId() else null },
        send = { actor, ids, source ->
            transport.post("quata_chat_mark_messages_state", buildJsonObject {
                put("p_actor_profile_id", actor)
                put("p_message_ids", JsonArray(ids.map(::JsonPrimitive)))
                put("p_status", "DELIVERED")
                put("p_source", source)
            }.toString()).successOrThrow()
        },
    )

    override val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()
    override val isAppForeground: StateFlow<Boolean> = _isAppForeground.asStateFlow()
    override val pendingDeletedConversation: StateFlow<Conversation?> = _pendingDeletedConversation.asStateFlow()
    override val isRealtimeOnline: StateFlow<Boolean> = realtimeGateway?.isOnline ?: realtimeOnlineState.asStateFlow()
    override val typingProfileIds: StateFlow<Set<String>> = realtimeGateway?.typingProfileIds ?: _typingProfileIds.asStateFlow()
    override val syncStatus: StateFlow<ChatSyncStatus> = _syncStatus.asStateFlow()

    init {
        realtimeGateway?.let { gateway ->
            // Reuse the transport's OS observer. Read its current value synchronously for
            // requests, and propagate later changes to the shared visible sync state.
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                var previousAvailable: Boolean? = null
                gateway.isNetworkAvailable.collect { available ->
                    val recovered = previousAvailable == false && available
                    previousAvailable = available
                    _syncStatus.value = if (available) ChatSyncStatus.Refreshing else ChatSyncStatus.Offline
                    if (!available) {
                        networkRecoveryJob?.cancel()
                        networkRecoveryJob = null
                    } else if (recovered && _isAppForeground.value) {
                        launchNetworkRecovery()
                    }
                }
            }
            // Read the stream eagerly: construction is the subscription boundary and tests can
            // detect accidental removal even before the collector is scheduled.
            val changeStream = gateway.changes
            scope.launch {
                changeStream.collect { change -> refreshForRealtimeChange(change) }
            }
        }
    }
    override fun setDeviceNetworkAvailable(isAvailable: Boolean) {
        val recoveredWithoutGateway = realtimeGateway == null && !observedNetworkAvailable.value && isAvailable
        observedNetworkAvailable.value = isAvailable
        realtimeGateway?.setNetworkAvailable(isAvailable)
        _syncStatus.value = if (isAvailable) ChatSyncStatus.Refreshing else ChatSyncStatus.Offline
        if (!isAvailable) {
            networkRecoveryJob?.cancel()
            networkRecoveryJob = null
        } else if (recoveredWithoutGateway && _isAppForeground.value) {
            launchNetworkRecovery()
        }
    }
    private fun launchNetworkRecovery() {
        networkRecoveryJob?.cancel()
        networkRecoveryJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            flushPendingMessages()
            refreshInbox()
            _activeConversationId.value
                ?.takeIf { it != AppDestinations.FavoriteMessagesConversationId }
                ?.let { refreshThread(it, ThreadPageSize) }
        }
    }
    override fun currentUser(): User? = currentUserSnapshot
    override suspend fun currentActorId(): String? = runCatching {
        authenticatedUser.currentUserId()?.also { actorId ->
            if (currentUserSnapshot?.id != actorId) {
                currentUserSnapshot = User(id = actorId, email = "", displayName = "")
            }
        }
    }.getOrNull()
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
    override fun setAppForeground(isForeground: Boolean) {
        val resumed = isForeground && !_isAppForeground.value
        _isAppForeground.value = isForeground
        realtimeGateway?.setForeground(isForeground)
        if (resumed && networkAvailable) scope.launch {
            flushPendingMessages()
            refreshInbox()
        }
    }
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
    override suspend fun loadConversationPage(cursor: ChatConversationCursor?, limit: Int): Result<ChatConversationPage> = runCatching {
        val page = fetchInboxPage(cursor, limit)
        if (cursor == null) {
            conversations.value = page.conversations
            loadedInboxPageCount = 1
        } else {
            mergeConversations(page.conversations)
            loadedInboxPageCount += 1
        }
        page
    }.onFailure { updateReadFailure() }
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
        authenticatedActorId().let { actorId -> restoreOutbox(actorId) }
        while (currentCoroutineContext().isActive) {
            awaitForeground()
            if (conversationId == AppDestinations.FavoriteMessagesConversationId) {
                val actorId = authenticatedActorId()
                favoritesMutex.withLock { activateFavoritesActorLocked(actorId) }
                if (networkAvailable) refreshFavorites().getOrThrow()
            } else {
                awaitActiveConversation(conversationId)
                if (networkAvailable) refreshThread(conversationId, ThreadPageSize)
            }
            emit(state.value)
            delay(pollIntervalMillis.coerceAtLeast(MinimumPollIntervalMillis))
        }
    }
    override suspend fun loadOlderMessages(conversationId: String, limit: Int): Result<Boolean> = runCatching {
        if (conversationId == AppDestinations.FavoriteMessagesConversationId) {
            return@runCatching favoritesMutex.withLock {
                val actorId = authenticatedActorId()
                val generation = activateFavoritesActorLocked(actorId)
                if (!favoritesHasMore) return@withLock false
                val cursor = favoritesNextCursor ?: error("chat_favorites_cursor_missing")
                val page = fetchFavoritesPage(actorId, cursor, limit)
                assertFavoritesActorCurrent(actorId, generation)
                val state = messagesState(AppDestinations.FavoriteMessagesConversationId)
                state.value = (state.value + page.messages)
                    .distinctBy(Message::id)
                    .sortedByDescending { it.sentAtMillis ?: Long.MIN_VALUE }
                favoritesHasMore = page.hasMore
                favoritesNextCursor = page.nextCursor
                loadedFavoritesPageCount += 1
                favoritesHasMore
            }
        }
        val normalizedLimit = limit.coerceAtLeast(1)
        val knownMessageIds = messagesState(conversationId).value
            .mapNotNull { it.id.toLongOrNull() }
            .toSet()
        val refreshedMessages = refreshThread(conversationId, normalizedLimit).getOrThrow()
        refreshedMessages.asSequence()
            .mapNotNull { it.id.toLongOrNull() }
            .filterNot(knownMessageIds::contains)
            .distinct()
            .count() >= normalizedLimit
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
    override suspend fun sendMessage(conversationId: String, text: String, attachmentUri: String?, attachmentName: String?, attachmentMimeType: String?, clientMessageId: String?, expectedActorId: String?): Result<Unit> =
        sendTextMessage(conversationId, text, attachmentUri, attachmentName, attachmentMimeType, null, clientMessageId, expectedActorId)
    override suspend fun sendReply(conversationId: String, text: String, replyTo: Message, attachmentUri: String?, attachmentName: String?, attachmentMimeType: String?, clientMessageId: String?): Result<Unit> {
        val replyId = replyTo.id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("web_chat_invalid_reply_message_id"))
        return sendTextMessage(conversationId, text, attachmentUri, attachmentName, attachmentMimeType, replyId, clientMessageId)
    }
    override suspend fun sendSosMessage(contactIds: List<String>, text: String, lat: Double?, lng: Double?, accuracy: Double?, expectedActorId: String?): Result<String> = runCatching {
        val userId = currentUserId(expectedActorId)
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
        if (rawRoot["rate_limited"]?.jsonPrimitive?.booleanOrNull == true) {
            throw SosRateLimitException(
                rawRoot["remaining_millis"]?.jsonPrimitive?.longOrNull?.coerceAtLeast(1L) ?: 1L,
            )
        }
        val envelope = parseChatRpcPayloadEnvelope(rawRoot)
        mergeConversations(envelope.toChatRpcConversations(userId)); mergeMessages(envelope.toChatRpcMessages(userId))
        val threadId = rawRoot["thread_id"]?.jsonPrimitive?.longOrNull
            ?: rawRoot["thread"]?.jsonObject?.get("thread_id")?.jsonPrimitive?.longOrNull
            ?: envelope.toChatRpcConversations(userId).firstOrNull()?.id?.threadIdForRefresh()
            ?: throw IllegalStateException("chat_sos_thread_missing")
        markRequestCompleted()
        "sb:$threadId"
    }.onFailure { updateReadFailure() }
    override suspend fun cachedPrivateConversationId(userId: String): String? {
        val current = currentUserId()
        return conversations.value.firstOrNull { conversation ->
            !conversation.isGroup && !conversation.isEmergency &&
                conversation.participantIds.containsAll(listOf(current, userId)) && conversation.participantIds.size == 2
        }?.id
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
    override suspend fun openGroupConversationForRequest(participantIds: List<String>, title: String?, requestKey: String): Result<String> =
        openThread("quata_chat_start_thread") { userId ->
            buildJsonObject {
                put("p_actor_profile_id", userId); put("p_recipient_profile_ids", JsonArray(participantIds.distinct().map(::JsonPrimitive)))
                put("p_subject", title?.let(::JsonPrimitive) ?: JsonNull); put("p_type", "group"); put("p_message", "")
                put("p_unique_key", requestKey)
            }.toString()
        }
    override suspend fun markConversationRead(conversationId: String): Result<Unit> = runCatching {
        val userId = currentUserId(); val threadId = conversationId.requirePostgrestThreadId(); _syncStatus.value = ChatSyncStatus.Refreshing
        rpc("quata_chat_mark_thread_read", threadActionRequest(userId, threadId)); updateConversation(conversationId) { it.copy(unreadCount = 0) }; markRequestCompleted()
    }.onFailure { updateReadFailure() }
    override suspend fun setConversationMuted(conversationId: String, muted: Boolean): Result<Unit> = runCatching {
        val userId = currentUserId(); val threadId = conversationId.requirePostgrestThreadId(); _syncStatus.value = ChatSyncStatus.Refreshing
        rpc("quata_chat_set_muted", mutedRequest(userId, threadId, muted)); updateConversation(conversationId) { it.copy(isMuted = muted) }; markRequestCompleted()
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
        }.toString()); markRequestCompleted()
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
        markRequestCompleted()
    }.onFailure { updateReadFailure() }
    override suspend fun forwardMessage(message: Message, conversationIds: List<String>): Result<ChatForwardResult> = runCatching {
        val userId = currentUserId(); val numericMessageId = message.id.toLongOrNull() ?: throw IllegalArgumentException("chat_message_id_invalid")
        val threadIds = conversationIds.map(String::requirePostgrestThreadId).distinct()
        if (threadIds.isEmpty()) return@runCatching ChatForwardResult(requestedCount = 0, sentCount = 0)
        val payload = transport.post("quata_chat_forward_message", buildJsonObject { put("p_actor_profile_id", userId); put("p_message_id", numericMessageId); put("p_thread_ids", JsonArray(threadIds.map(::JsonPrimitive))) }.toString()).successOrThrow()
        val result = parseChatForwardResult(payload, requestedCount = threadIds.size)
        refreshInbox().getOrThrow(); markRequestCompleted()
        result
    }.onFailure { updateReadFailure() }
    override suspend fun flushPendingMessages(): Boolean {
        if (!networkAvailable) return false
        val actorId = authenticatedUser.currentUserId() ?: return true
        restoreOutbox(actorId, forceReload = true)
        val pending = outboxMutex.withLock {
            retryableOutgoing.values.sortedBy(StoredChatOutgoing::createdAtMillis)
        }
        var allSent = true
        pending.forEach { outgoing ->
            if ((!outgoing.deliveredAwaitingCleanup && !outgoing.orphanCleanupBlocked && outgoing.attempts >= MaxOutboxAttempts) ||
                attemptOutgoing(outgoing).isFailure
            ) {
                allSent = false
            }
        }
        return allSent
    }
    override suspend fun retryPendingMessage(clientMessageId: String): Result<Unit> {
        val actorId = authenticatedActorId()
        restoreOutbox(actorId)
        val pending = outboxMutex.withLock { retryableOutgoing[clientMessageId] }
            ?: return Result.failure(IllegalArgumentException("chat_retry_message_missing"))
        if (pending.orphanCleanupBlocked) return attemptOutgoing(pending)
        if (pending.deliveredAwaitingCleanup) return attemptOutgoing(pending)
        projectPendingMessage(pending.copy(attempts = 0, lastError = null))
        if (!networkAvailable) return Result.success(Unit)
        return attemptOutgoing(pending, resetAttempts = true)
    }

    override suspend fun isMessagePending(clientMessageId: String): Boolean {
        val actorId = authenticatedUser.currentUserId() ?: return false
        restoreOutbox(actorId)
        return outboxMutex.withLock { retryableOutgoing[clientMessageId]?.deliveredAwaitingCleanup == false }
    }

    private suspend fun refreshInbox(): Result<List<Conversation>> = runCatching {
        val retained = conversations.value
        val page = fetchInboxPage(cursor = null, limit = InboxPageSize)
        if (loadedInboxPageCount > 1) {
            val firstPageIds = page.conversations.mapTo(mutableSetOf(), Conversation::id)
            conversations.value = (page.conversations + retained.filterNot { it.id in firstPageIds })
                .distinctBy(Conversation::id)
                .sortedByDescending { it.updatedAtMillis ?: Long.MIN_VALUE }
        } else {
            conversations.value = page.conversations
            loadedInboxPageCount = 1
        }
        conversations.value
    }.onFailure { error ->
        if (error is CancellationException) throw error
        updateReadFailure()
    }

    private suspend fun fetchInboxPage(cursor: ChatConversationCursor?, limit: Int): ChatConversationPage {
        val userId = currentUserId()
        _syncStatus.value = ChatSyncStatus.Refreshing
        val envelope = rpc("quata_chat_get_inbox_page", inboxPageRequest(userId, cursor, limit))
        updateCurrentUserFrom(envelope, userId)
        val mapped = envelope.toChatRpcConversations(userId)
        val incoming = envelope.toChatRpcMessages(userId)
        mergeMessages(incoming)
        markRequestCompleted()
        acknowledgeDelivery(userId, incoming, "inbox_refresh")
        return ChatConversationPage(
            conversations = mapped,
            hasMore = envelope.inboxHasMore,
            nextCursor = envelope.inboxNextCursor,
        )
    }

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
        _syncStatus.value = when {
            !networkAvailable -> ChatSyncStatus.Offline
            isRealtimeOnline.value -> ChatSyncStatus.Online
            else -> ChatSyncStatus.Refreshing
        }
    }
    private suspend fun openThread(functionName: String, body: (String) -> String): Result<String> = runCatching {
        val userId = currentUserId(); _syncStatus.value = ChatSyncStatus.Refreshing
        val envelope = rpc(functionName, body(userId)); val mapped = envelope.toChatRpcConversations(userId)
        mergeConversations(mapped); mergeMessages(envelope.toChatRpcMessages(userId)); markRequestCompleted()
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
        expectedActorId: String? = null,
    ): Result<Unit> = runCatching {
        require(text.isNotBlank() || !attachmentUri.isNullOrBlank()) { "web_chat_message_empty" }
        conversationId.requirePostgrestThreadId()
        val actorId = authenticatedActorId(expectedActorId)
        restoreOutbox(actorId)
        val stableClientMessageId = clientMessageId?.takeIf(String::isNotBlank) ?: newOutboxClientMessageId()
        val existing = outboxMutex.withLock { retryableOutgoing[stableClientMessageId] }
        val outgoing = existing ?: StoredChatOutgoing(
            actorId = actorId,
            conversationId = conversationId,
            text = text,
            attachmentCacheKey = attachmentUri?.takeIf(String::isNotBlank)?.let { reference ->
                val cacheKey = attachmentCacheKey(stableClientMessageId)
                when (val stored = outboxFiles.store(
                    cacheKey,
                    PlatformFile(reference, attachmentName, attachmentMimeType),
                )) {
                    is PlatformResult.Success -> cacheKey
                    is PlatformResult.Failure -> error(stored.reason ?: "chat_outbox_attachment_store_failed")
                    PlatformResult.Cancelled -> error("chat_outbox_attachment_store_cancelled")
                    PlatformResult.Unsupported -> error("chat_outbox_attachment_store_unsupported")
                }
            },
            attachmentName = attachmentName,
            attachmentMimeType = attachmentMimeType,
            replyToMessageId = replyToMessageId,
            clientMessageId = stableClientMessageId,
            createdAtMillis = nowMillis(),
        )
        require(outgoing.actorId == actorId && outgoing.conversationId == conversationId) {
            "chat_outbox_client_message_conflict"
        }
        try {
            if (existing == null && !insertOutgoing(outgoing)) error("chat_outbox_client_message_conflict")
        } catch (error: Throwable) {
            if (existing == null) outgoing.attachmentCacheKey?.let { outboxFiles.remove(it) }
            throw error
        }
        projectPendingMessage(outgoing)
        if (!networkAvailable) {
            _syncStatus.value = ChatSyncStatus.Offline
            return@runCatching
        }
        val attempt = attemptOutgoing(outgoing)
        val failure = attempt.exceptionOrNull()
        if (failure is AttachmentOrphanCleanupFailed) throw failure
    }

    private suspend fun attemptOutgoing(outgoing: StoredChatOutgoing, resetAttempts: Boolean = false): Result<Unit> =
        outgoingExecutionLock.withLock(outgoing.actorId, outgoing.clientMessageId) {
            attemptOutgoingLocked(outgoing, resetAttempts)
        }

    private suspend fun attemptOutgoingLocked(outgoing: StoredChatOutgoing, resetAttempts: Boolean): Result<Unit> {
        val leaseToken = newOutboxLeaseToken()
        val claimed = outgoingStore.claim(
            outgoing.actorId,
            outgoing.clientMessageId,
            leaseToken,
            nowMillis(),
            nowMillis() + OutboxLeaseMillis,
        ) ?: run {
            val durable = outgoingStore.load(outgoing.actorId)
                .firstOrNull { it.clientMessageId == outgoing.clientMessageId }
            outboxMutex.withLock {
                if (loadedOutboxActorId == outgoing.actorId) {
                    if (durable == null) retryableOutgoing.remove(outgoing.clientMessageId)
                    else retryableOutgoing[outgoing.clientMessageId] = durable
                }
            }
            return if (durable == null) Result.success(Unit)
            else Result.failure(OutboxMessageAlreadyClaimed())
        }
        var active = claimed
        outboxMutex.withLock {
            if (loadedOutboxActorId == claimed.actorId) retryableOutgoing[claimed.clientMessageId] = claimed
        }
        if (resetAttempts) {
            active = active.copy(attempts = 0, lastError = null)
            persistClaimed(active, leaseToken)
        }
        if (active.deliveredAwaitingCleanup) return cleanupDeliveredOutgoing(active, leaseToken)
        return runCatching {
        active = renewClaim(active, leaseToken)
        if (active.orphanCleanupBlocked) {
            val orphan = active.orphanedStoragePath ?: error("chat_outbox_orphan_path_missing")
            val cleaned = attachmentUploader.deleteUploadedAttachment(
                UploadedChatAttachment(
                    storagePath = orphan,
                    publicUrl = "",
                    mimeType = active.attachmentMimeType.orEmpty(),
                    sizeBytes = null,
                    name = active.attachmentName.orEmpty(),
                    extension = active.attachmentName?.substringAfterLast('.', "").orEmpty(),
                ),
            )
            check(cleaned) { "chat_outbox_orphan_cleanup_still_failed" }
            active = renewClaim(active, leaseToken).copy(
                orphanCleanupBlocked = false,
                orphanedStoragePath = null,
                attempts = 0,
                lastError = null,
            )
            persistClaimed(active, leaseToken)
        }
        check(networkAvailable) { "web_chat_offline" }
        val actorId = authenticatedActorId(active.actorId)
        val threadId = active.conversationId.requirePostgrestThreadId()
        _syncStatus.value = ChatSyncStatus.Refreshing
        val attachmentIds = if (active.registeredAttachmentIds.isNotEmpty()) {
            active.registeredAttachmentIds
        } else {
            active.attachmentCacheKey?.let { cacheKey ->
                val file = when (val cached = outboxFiles.get(cacheKey)) {
                    is PlatformResult.Success -> cached.value.copy(
                        displayName = active.attachmentName,
                        mimeType = active.attachmentMimeType,
                    )
                    is PlatformResult.Failure -> error(cached.reason ?: "chat_outbox_attachment_missing")
                    PlatformResult.Cancelled -> error("chat_outbox_attachment_read_cancelled")
                    PlatformResult.Unsupported -> error("chat_outbox_attachment_read_unsupported")
                }
                listOf(uploadAndRegisterAttachment(actorId, threadId, file)).also { registered ->
                    active = renewClaim(active, leaseToken).copy(registeredAttachmentIds = registered)
                    persistClaimed(active, leaseToken)
                }
            }.orEmpty()
        }
        active = renewClaim(active, leaseToken)
        val envelope = rpc(
            "quata_chat_send_message",
            sendMessageRequest(
                actorId,
                threadId,
                active.text.trim(),
                attachmentIds,
                active.replyToMessageId,
                active.clientMessageId,
            ),
        )
        active = renewClaim(active, leaseToken)
        val delivered = active.copy(
                deliveredAwaitingCleanup = true,
                lastError = null,
            )
        persistClaimed(delivered, leaseToken)
        active = delivered
        mergeConversations(envelope.toChatRpcConversations(actorId))
        mergeMessages(envelope.toChatRpcMessages(actorId))
        cleanupDeliveredOutgoing(delivered, leaseToken).getOrThrow()
        markRequestCompleted()
    }.onFailure { error ->
        if (error is CancellationException) throw error
        if (error is OutboxLeaseLost) return@onFailure
        if (error is AttachmentOrphanCleanupFailed) {
            val unsafeToRetry = active.copy(
                orphanCleanupBlocked = true,
                orphanedStoragePath = error.uploaded.storagePath,
                attempts = MaxOutboxAttempts,
                leaseToken = null,
                leaseUntilMillis = null,
                lastError = error.message,
            )
            persistClaimed(unsafeToRetry, leaseToken)
            projectPendingMessage(unsafeToRetry)
        } else {
            val updated = active.copy(
                attempts = if (active.deliveredAwaitingCleanup || active.orphanCleanupBlocked) active.attempts else active.attempts + 1,
                lastError = error.message,
                leaseToken = null,
                leaseUntilMillis = null,
            )
            persistClaimed(updated, leaseToken)
            if (!updated.deliveredAwaitingCleanup) projectPendingMessage(updated)
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
        rpc(functionName, body(userId, threadId)); after(); markRequestCompleted()
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
        markRequestCompleted()
    }.onFailure { updateReadFailure() }
    private suspend fun messageMutation(
        functionName: String,
        messageId: String,
        body: (String, Long, Long) -> String,
    ): Result<Unit> = runCatching {
        val message = allMessages().firstOrNull { it.id == messageId } ?: throw IllegalArgumentException("chat_message_not_loaded")
        val userId = currentUserId(); val threadId = message.conversationId.requirePostgrestThreadId(); val numericMessageId = message.id.toLongOrNull() ?: throw IllegalArgumentException("chat_message_id_invalid")
        rpc(functionName, body(userId, threadId, numericMessageId)); refreshThread(message.conversationId, ThreadPageSize).getOrThrow(); markRequestCompleted()
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
            val cleaned = runCatching { attachmentUploader.deleteUploadedAttachment(uploaded) }
                .getOrElse { cleanupError ->
                    throw AttachmentOrphanCleanupFailed(uploaded, cleanupError)
                }
            if (!cleaned) throw AttachmentOrphanCleanupFailed(uploaded)
            throw error
        }
    }
    private suspend fun refreshThread(conversationId: String, limit: Int): Result<List<Message>> = runCatching {
        val userId = currentUserId(); val threadId = conversationId.threadIdForRefresh(); _syncStatus.value = ChatSyncStatus.Refreshing
        val knownIds = messagesState(conversationId).value.mapNotNull { it.id.toLongOrNull() }
        val envelope = rpc("quata_chat_get_thread", threadRequest(userId, threadId, limit, knownIds))
        updateCurrentUserFrom(envelope, userId)
        mergeConversations(envelope.toChatRpcConversations(userId))
        val incoming = envelope.toChatRpcMessages(userId)
        mergeMessages(incoming); markRequestCompleted()
        acknowledgeDelivery(userId, incoming, "thread_refresh")
        messagesState(conversationId).value
    }.onFailure { updateReadFailure() }
    private suspend fun refreshFavorites(): Result<List<Message>> = runCatching {
        favoritesMutex.withLock {
            val actorId = currentUserId()
            val generation = activateFavoritesActorLocked(actorId)
            val pagesToRetain = loadedFavoritesPageCount.coerceAtLeast(1)
            var cursor: ChatFavoriteCursor? = null
            var hasMore = true
            val refreshed = mutableListOf<Message>()
            var loadedPages = 0
            while (loadedPages < pagesToRetain && hasMore) {
                val page = fetchFavoritesPage(actorId, cursor, FavoritesPageSize)
                refreshed += page.messages
                cursor = page.nextCursor
                hasMore = page.hasMore
                loadedPages += 1
            }
            assertFavoritesActorCurrent(actorId, generation)
            val favorites = refreshed.distinctBy(Message::id)
                .sortedByDescending { it.sentAtMillis ?: Long.MIN_VALUE }
            messagesState(AppDestinations.FavoriteMessagesConversationId).value = favorites
            favoritesHasMore = hasMore
            favoritesNextCursor = cursor
            loadedFavoritesPageCount = loadedPages.coerceAtLeast(1)
            favorites
        }
    }.onFailure { updateReadFailure() }

    private suspend fun fetchFavoritesPage(
        actorId: String,
        cursor: ChatFavoriteCursor?,
        limit: Int,
    ): ChatFavoritePage {
        currentUserId(expectedActorId = actorId)
        _syncStatus.value = ChatSyncStatus.Refreshing
        val envelope = rpc("quata_chat_get_favorites_page", favoritesPageRequest(actorId, cursor, limit))
        authenticatedActorId(expectedActorId = actorId)
        updateCurrentUserFrom(envelope, actorId)
        val messages = envelope.toChatRpcMessages(actorId)
            .filter { it.isFavorite && !it.isDeleted }
        if (envelope.favoritesHasMore && envelope.favoritesNextCursor == null) {
            error("chat_favorites_cursor_missing")
        }
        markRequestCompleted()
        return ChatFavoritePage(messages, envelope.favoritesHasMore, envelope.favoritesNextCursor)
    }

    private fun activateFavoritesActorLocked(actorId: String): Long {
        if (favoritesOwnerActorId != actorId) {
            favoritesOwnerActorId = actorId
            favoritesGeneration += 1L
            loadedFavoritesPageCount = 0
            favoritesNextCursor = null
            favoritesHasMore = false
            messagesState(AppDestinations.FavoriteMessagesConversationId).value = emptyList()
        }
        return favoritesGeneration
    }

    private suspend fun assertFavoritesActorCurrent(actorId: String, generation: Long) {
        check(favoritesOwnerActorId == actorId && favoritesGeneration == generation) {
            "chat_favorites_actor_generation_changed"
        }
        authenticatedActorId(expectedActorId = actorId)
    }
    private fun acknowledgeDelivery(actor: String, incoming: List<Message>, source: String) {
        scope.launch { deliveryAcknowledgements.received(actor, incoming, source) }
    }
    private suspend fun restoreOutbox(actorId: String, forceReload: Boolean = false) {
        val restored = outboxMutex.withLock {
            if (loadedOutboxActorId == actorId && !forceReload) return
            val loaded = outgoingStore.load(actorId)
            retryableOutgoing.clear()
            loaded.associateByTo(retryableOutgoing, StoredChatOutgoing::clientMessageId)
            loadedOutboxActorId = actorId
            loaded
        }
        messagesByConversation.values.forEach { state ->
            state.value = state.value.filterNot(Message::isLocalEcho)
        }
        restored.forEach(::projectPendingMessage)
    }
    private suspend fun insertOutgoing(outgoing: StoredChatOutgoing): Boolean {
        if (!outgoingStore.insert(outgoing)) return false
        outboxMutex.withLock {
            check(loadedOutboxActorId == outgoing.actorId) { "chat_outbox_actor_not_loaded" }
            retryableOutgoing[outgoing.clientMessageId] = outgoing
        }
        return true
    }
    private suspend fun persistClaimed(outgoing: StoredChatOutgoing, leaseToken: String) {
        if (!outgoingStore.updateClaimed(outgoing, leaseToken)) throw OutboxLeaseLost()
        outboxMutex.withLock {
            if (loadedOutboxActorId == outgoing.actorId) retryableOutgoing[outgoing.clientMessageId] = outgoing
        }
    }
    private suspend fun renewClaim(outgoing: StoredChatOutgoing, leaseToken: String): StoredChatOutgoing {
        val leaseUntil = nowMillis() + OutboxLeaseMillis
        if (!outgoingStore.renewClaim(outgoing.actorId, outgoing.clientMessageId, leaseToken, leaseUntil)) {
            throw OutboxLeaseLost()
        }
        return outgoing.copy(leaseUntilMillis = leaseUntil)
    }
    private suspend fun removeOutgoingMetadata(outgoing: StoredChatOutgoing, leaseToken: String) {
        if (!outgoingStore.removeClaimed(outgoing.actorId, outgoing.clientMessageId, leaseToken)) throw OutboxLeaseLost()
        outboxMutex.withLock {
            if (loadedOutboxActorId == outgoing.actorId) {
                retryableOutgoing.remove(outgoing.clientMessageId)
            }
        }
        val state = messagesState(outgoing.conversationId)
        state.value = state.value.filterNot { message ->
            message.isLocalEcho && message.clientMessageId == outgoing.clientMessageId
        }
    }
    private suspend fun cleanupDeliveredOutgoing(outgoing: StoredChatOutgoing, leaseToken: String): Result<Unit> = runCatching {
        outgoing.attachmentCacheKey?.let { cacheKey ->
            when (val removed = outboxFiles.remove(cacheKey)) {
                is PlatformResult.Success -> Unit
                is PlatformResult.Failure -> error(removed.reason ?: "chat_outbox_attachment_cleanup_failed")
                PlatformResult.Cancelled -> error("chat_outbox_attachment_cleanup_cancelled")
                PlatformResult.Unsupported -> error("chat_outbox_attachment_cleanup_unsupported")
            }
        }
        removeOutgoingMetadata(outgoing, leaseToken)
    }
    private fun projectPendingMessage(outgoing: StoredChatOutgoing) {
        if (outgoing.deliveredAwaitingCleanup) return
        val failed = outgoing.attempts >= MaxOutboxAttempts
        val message = Message(
            id = "local:${outgoing.clientMessageId}",
            conversationId = outgoing.conversationId,
            senderId = outgoing.actorId,
            senderName = currentUserSnapshot?.displayName?.takeIf(String::isNotBlank) ?: "Usuario",
            text = outgoing.text,
            sentAt = outgoing.createdAtMillis.toString(),
            sentAtMillis = outgoing.createdAtMillis,
            isMine = true,
            isRead = false,
            replyToMessageId = outgoing.replyToMessageId?.toString(),
            attachmentName = outgoing.attachmentName,
            attachmentMimeType = outgoing.attachmentMimeType,
            clientMessageId = outgoing.clientMessageId,
            isPending = !failed,
            isLocalEcho = true,
            deliveryState = if (failed) com.quata.core.model.MessageDeliveryState.Failed
            else com.quata.core.model.MessageDeliveryState.Pending,
        )
        val state = messagesState(outgoing.conversationId)
        state.value = (state.value.filterNot { it.clientMessageId == outgoing.clientMessageId } + message)
            .sortedBy { it.sentAtMillis ?: Long.MAX_VALUE }
    }
    private suspend fun authenticatedActorId(expectedActorId: String? = null): String {
        val actorId = authenticatedUser.currentUserId() ?: throw IllegalStateException("web_chat_session_missing")
        check(expectedActorId == null || actorId == expectedActorId) { "web_chat_session_actor_mismatch" }
        if (currentUserSnapshot?.id != actorId) currentUserSnapshot = User(id = actorId, email = "", displayName = "")
        return actorId
    }
    private suspend fun currentUserId(expectedActorId: String? = null): String {
        if (!networkAvailable) throw IllegalStateException("web_chat_offline")
        return authenticatedActorId(expectedActorId)
    }
    private fun updateCurrentUserFrom(envelope: ChatRpcPayloadEnvelope, userId: String) {
        envelope.profileRecords().firstOrNull { it.id == userId }?.let { profile ->
            currentUserSnapshot = User(userId, "", profile.resolvedDisplayName(), profile.neighborhood.orEmpty(), profile.avatarUrl)
        }
    }
    private suspend fun rpc(functionName: String, body: String): ChatRpcPayloadEnvelope {
        val response = transport.post(functionName, body)
        currentCoroutineContext().ensureActive()
        return Json.parseToJsonElement(response.successOrThrow()).let(::parseChatRpcPayloadEnvelope)
    }
    private fun mergeMessages(incoming: List<Message>) { incoming.groupBy(Message::conversationId).forEach { (id, messages) -> val old = messagesState(id).value.associateBy(Message::id); messagesState(id).value = (old + messages.associateBy(Message::id)).values.sortedBy { it.sentAtMillis ?: Long.MIN_VALUE } } }
    private fun mergeConversations(incoming: List<Conversation>) { if (incoming.isNotEmpty()) conversations.value = (conversations.value.associateBy(Conversation::id) + incoming.associateBy(Conversation::id)).values.sortedByDescending { it.updatedAtMillis ?: 0L } }
    private fun updateConversation(id: String, transform: (Conversation) -> Conversation) { conversations.value = conversations.value.map { if (it.id == id) transform(it) else it } }
    private fun messagesState(id: String) = messagesByConversation.getOrPut(id) { MutableStateFlow(emptyList()) }
    private suspend fun awaitForeground() { if (!_isAppForeground.value) _isAppForeground.filter { it }.first() }
    private suspend fun awaitActiveConversation(id: String) { if (_activeConversationId.value != id) _activeConversationId.filter { it == id }.first() }
    private fun markRequestCompleted() { _syncStatus.value = if (networkAvailable) ChatSyncStatus.Online else ChatSyncStatus.Offline }
    private fun updateReadFailure() { _syncStatus.value = if (networkAvailable) ChatSyncStatus.Error else ChatSyncStatus.Offline }
    private fun inboxPageRequest(userId: String, cursor: ChatConversationCursor?, limit: Int) = buildJsonObject {
        put("p_actor_profile_id", userId)
        put("p_limit", limit.coerceIn(1, InboxPageSize))
        put("p_before_last_message_at", cursor?.lastMessageAt?.let(::JsonPrimitive) ?: JsonNull)
        put("p_before_updated_at", cursor?.updatedAt?.let(::JsonPrimitive) ?: JsonNull)
        put("p_before_thread_id", cursor?.threadId?.let(::JsonPrimitive) ?: JsonNull)
    }.toString()
    private fun favoritesPageRequest(userId: String, cursor: ChatFavoriteCursor?, limit: Int) = buildJsonObject {
        put("p_actor_profile_id", userId)
        put("p_limit", limit.coerceIn(1, FavoritesPageSize))
        put("p_before_created_at", cursor?.createdAt?.let(::JsonPrimitive) ?: JsonNull)
        put("p_before_message_id", cursor?.messageId?.let(::JsonPrimitive) ?: JsonNull)
    }.toString()
    private fun threadRequest(userId: String, threadId: Long, limit: Int, knownIds: List<Long>) = buildJsonObject { put("p_actor_profile_id", userId); put("p_thread_id", threadId); put("p_limit", limit); put("p_known_message_ids", JsonArray(knownIds.map(::JsonPrimitive))) }.toString()
    private fun sendMessageRequest(userId: String, threadId: Long, message: String, fileIds: List<Long>, replyTo: Long?, clientId: String?) = buildJsonObject { put("p_actor_profile_id", userId); put("p_thread_id", threadId); put("p_message", message); put("p_file_ids", JsonArray(fileIds.map(::JsonPrimitive))); put("p_reply_to_message_id", replyTo?.let(::JsonPrimitive) ?: JsonNull); put("p_client_message_id", clientId?.let(::JsonPrimitive) ?: JsonNull) }.toString()
    private fun threadActionRequest(userId: String, threadId: Long) = buildJsonObject { put("p_actor_profile_id", userId); put("p_thread_id", threadId) }.toString()
    private fun mutedRequest(userId: String, threadId: Long, muted: Boolean) = buildJsonObject { put("p_actor_profile_id", userId); put("p_thread_id", threadId); put("p_muted", muted) }.toString()
    private fun newOutboxClientMessageId(): String =
        "outbox-${nowMillis()}-${Random.nextLong().toString(16)}"
    private fun newOutboxLeaseToken(): String =
        "lease-${nowMillis()}-${Random.nextLong().toString(16)}"
    private fun attachmentCacheKey(clientMessageId: String): String =
        "chat-outbox-" + clientMessageId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(96)
    private companion object {
        const val ConversationPrefix = "sb:"
        const val InboxPageSize = 100
        const val ThreadPageSize = 250
        const val FavoritesPageSize = 250
        const val CandidatePageSize = 100
        const val DefaultPollIntervalMillis = 30_000L
        const val MinimumPollIntervalMillis = 5_000L
        const val ChatAttachmentsBucket = "chat-attachments"
        const val MaxOutboxAttempts = 5
        const val OutboxLeaseMillis = 5 * 60_000L
    }
}

private data class ChatFavoritePage(
    val messages: List<Message>,
    val hasMore: Boolean,
    val nextCursor: ChatFavoriteCursor?,
)

private class OutboxMessageAlreadyClaimed : IllegalStateException("chat_outbox_message_already_claimed")
private class OutboxLeaseLost : IllegalStateException("chat_outbox_lease_lost")

internal fun shouldCleanupEmptyPrivateConversation(conversation: Conversation?, messages: List<Message>): Boolean =
    conversation?.isGroup != true && conversation?.isEmergency != true && messages.isEmpty()

private fun ChatPostgrestResponse.successOrThrow(): String = when (this) {
    is ChatPostgrestResponse.Success -> body
    is ChatPostgrestResponse.Failure -> throw cause
}
private class AttachmentOrphanCleanupFailed(val uploaded: UploadedChatAttachment, cause: Throwable? = null) :
    IllegalStateException("web_chat_attachment_orphan_cleanup_failed", cause)

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
