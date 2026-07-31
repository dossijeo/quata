package com.quata.feature.chat.data

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.core.navigation.AppDestinations
import com.quata.core.platform.PlatformFile
import com.quata.feature.chat.domain.ChatConversationCandidate
import com.quata.feature.chat.domain.ChatConversationCandidatePage
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.domain.ChatSyncStatus
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
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
 * Portable chat implementation for the existing RLS-protected PostgREST RPC contract.
 *
 * It deliberately polls while the host is foreground because realtime subscription mechanics are
 * platform specific. Android, Web and iOS can therefore share mapping, mutation and reconciliation
 * behavior while providing only transport, authentication and binary-upload adapters.
 */
open class PostgrestChatRepository(
    private val transport: ChatPostgrestTransport,
    private val authenticatedUser: ChatAuthenticatedUserProvider,
    private val attachmentUploader: ChatAttachmentUploader,
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
    override fun setActiveConversation(conversationId: String?) { _activeConversationId.value = conversationId }
    override fun setConversationVisible(conversationId: String, visible: Boolean) = Unit
    override fun setAppForeground(isForeground: Boolean) { _isAppForeground.value = isForeground }
    override fun setTyping(conversationId: String, isTyping: Boolean) = Unit
    override fun cleanupEmptyConversation(conversationId: String) = Unit
    override fun clearChatNotifications() = Unit
    override suspend fun getConversations(): Result<List<Conversation>> = refreshInbox()
    override fun observeConversations(): Flow<List<Conversation>> = flow {
        while (currentCoroutineContext().isActive) {
            awaitForeground(); refreshInbox(); emit(conversations.value)
            delay(pollIntervalMillis.coerceAtLeast(MinimumPollIntervalMillis))
        }
    }
    override fun observeMessages(conversationId: String): Flow<List<Message>> = flow {
        val state = messagesState(conversationId)
        while (currentCoroutineContext().isActive) {
            awaitForeground()
            if (conversationId == AppDestinations.FavoriteMessagesConversationId) refreshFavorites()
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
    override suspend fun matchRegisteredContactPhones(phoneCandidates: Collection<String>): Result<Set<String>> = unsupportedMutation()
    override suspend fun openPrivateConversation(peerProfileId: String): Result<String> = openThread("quata_chat_get_or_create_private_thread") { userId ->
        buildJsonObject { put("p_actor_profile_id", userId); put("p_peer_profile_id", peerProfileId) }.toString()
    }
    override suspend fun sendMessage(conversationId: String, text: String, attachmentUri: String?, attachmentName: String?, attachmentMimeType: String?, clientMessageId: String?): Result<Unit> =
        sendTextMessage(conversationId, text, attachmentUri, attachmentName, attachmentMimeType, null, clientMessageId)
    override suspend fun sendReply(conversationId: String, text: String, replyTo: Message, attachmentUri: String?, attachmentName: String?, attachmentMimeType: String?, clientMessageId: String?): Result<Unit> {
        val replyId = replyTo.id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("web_chat_invalid_reply_message_id"))
        return sendTextMessage(conversationId, text, attachmentUri, attachmentName, attachmentMimeType, replyId, clientMessageId)
    }
    override suspend fun sendSosMessage(contactIds: List<String>, text: String, lat: Double?, lng: Double?, accuracy: Double?): Result<String> = unsupportedMutation()
    override suspend fun cachedPrivateConversationId(userId: String): String? = null
    override suspend fun cachedCommunityConversationId(communityName: String): String? = null
    override suspend fun openCommunityConversation(communityId: String, title: String, participantIds: List<String>): Result<String> = unsupportedMutation()
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
        val userId = currentUserId(); _syncStatus.value = ChatSyncStatus.Refreshing
        val envelope = rpc("quata_chat_get_inbox", inboxRequest(userId)); val mapped = envelope.toChatRpcConversations(userId).sortedByDescending { it.updatedAtMillis ?: 0L }
        conversations.value = mapped; mergeMessages(envelope.toChatRpcMessages(userId)); _syncStatus.value = ChatSyncStatus.Online; mapped
    }.onFailure { updateReadFailure() }
    private suspend fun openThread(functionName: String, body: (String) -> String): Result<String> = runCatching {
        val userId = currentUserId(); _syncStatus.value = ChatSyncStatus.Refreshing
        val envelope = rpc(functionName, body(userId)); val mapped = envelope.toChatRpcConversations(userId)
        mergeConversations(mapped); mergeMessages(envelope.toChatRpcMessages(userId)); _syncStatus.value = ChatSyncStatus.Online
        mapped.firstOrNull()?.id ?: throw IllegalStateException("web_chat_thread_response_missing")
    }.onFailure { updateReadFailure() }
    private suspend fun sendTextMessage(conversationId: String, text: String, attachmentUri: String?, attachmentName: String?, attachmentMimeType: String?, replyToMessageId: Long?, clientMessageId: String?): Result<Unit> = runCatching {
        require(text.isNotBlank() || !attachmentUri.isNullOrBlank()) { "web_chat_message_empty" }
        val userId = currentUserId(); val threadId = conversationId.requirePostgrestThreadId(); _syncStatus.value = ChatSyncStatus.Refreshing
        val fileIds = attachmentUri?.takeIf { it.isNotBlank() }?.let { reference -> listOf(uploadAndRegisterAttachment(userId, threadId, PlatformFile(reference, attachmentName, attachmentMimeType))) }.orEmpty()
        val envelope = rpc("quata_chat_send_message", sendMessageRequest(userId, threadId, text.trim(), fileIds, replyToMessageId, clientMessageId))
        mergeConversations(envelope.toChatRpcConversations(userId)); mergeMessages(envelope.toChatRpcMessages(userId)); _syncStatus.value = ChatSyncStatus.Online
    }.onFailure { updateReadFailure() }
    private suspend fun uploadAndRegisterAttachment(profileId: String, threadId: Long, file: PlatformFile): Long {
        val uploaded = attachmentUploader.upload(profileId, file)
        val body = buildJsonObject {
            put("p_actor_profile_id", profileId); put("p_thread_id", threadId); put("p_file_url", uploaded.publicUrl); put("p_storage_bucket", ChatAttachmentsBucket)
            put("p_storage_path", uploaded.storagePath); put("p_mime_type", uploaded.mimeType); put("p_name", uploaded.name); uploaded.sizeBytes?.let { put("p_size_bytes", it) } ?: put("p_size_bytes", JsonNull)
            put("p_ext", uploaded.extension); put("p_thumb", JsonNull)
        }.toString()
        return Json.parseToJsonElement(transport.post("quata_chat_register_attachment", body).successOrThrow()).jsonObject["id"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0L }
            ?: throw IllegalStateException("web_chat_attachment_registration_missing_id")
    }
    private suspend fun refreshThread(conversationId: String, limit: Int): Result<List<Message>> = runCatching {
        val userId = currentUserId(); val threadId = conversationId.threadIdForRefresh(); _syncStatus.value = ChatSyncStatus.Refreshing
        val knownIds = messagesState(conversationId).value.mapNotNull { it.id.toLongOrNull() }
        val envelope = rpc("quata_chat_get_thread", threadRequest(userId, threadId, limit, knownIds)); mergeMessages(envelope.toChatRpcMessages(userId)); _syncStatus.value = ChatSyncStatus.Online; messagesState(conversationId).value
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
        currentUserSnapshot = User(id = id, email = "", displayName = "Usuario"); return id
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

private fun ChatPostgrestResponse.successOrThrow(): String = when (this) {
    is ChatPostgrestResponse.Success -> body
    is ChatPostgrestResponse.Failure -> throw cause
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
