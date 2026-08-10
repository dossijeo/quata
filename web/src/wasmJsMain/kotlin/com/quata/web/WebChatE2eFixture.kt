@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.feature.chat.domain.ChatConversationCandidate
import com.quata.feature.chat.domain.ChatConversationCandidatePage
import com.quata.feature.chat.domain.ChatForwardResult
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.domain.ChatSyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Localhost-only, query-opt-in fixture used solely by WEB-CHAT-A11Y-E2E-001. It keeps the
 * production Compose host and its real [ChatViewModel] callbacks while replacing only the
 * remote repository; it never performs fetch, authentication or a database mutation.
 */
internal fun webChatE2eFixtureOrNull(): ChatRepository? = if (isWebChatE2eFixtureEnabled()) WebChatE2eFixture() else null

@JsFun("""() => {
  const location = globalThis.location;
  const local = location?.hostname === '127.0.0.1' || location?.hostname === 'localhost';
  return local && new URLSearchParams(location?.search || '').get('quata-chat-e2e') === '1';
}""")
private external fun isWebChatE2eFixtureEnabled(): Boolean

private class WebChatE2eFixture : ChatRepository {
    private companion object {
        const val SyntheticMessage = "mensaje AX local"
    }

    private val conversationId = "local:ax"
    private val conversations = MutableStateFlow(listOf(Conversation(
        id = conversationId,
        title = "Chat de prueba",
        lastMessagePreview = "Mensaje pendiente de lectura",
        unreadCount = 2,
    )))
    private val messages = MutableStateFlow<List<Message>>(emptyList())
    private val active = MutableStateFlow<String?>(null)
    private val foreground = MutableStateFlow(true)
    private val pendingDeleted = MutableStateFlow<Conversation?>(null)
    private val realtime = MutableStateFlow(false)
    private val typing = MutableStateFlow<Set<String>>(emptySet())
    private val sync = MutableStateFlow(ChatSyncStatus.Online)
    private var sends = 0
    private val user = User("fixture-user", "fixture@invalid", "Usuario de prueba")

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
    override fun setTyping(conversationId: String, isTyping: Boolean) { typing.value = if (isTyping) setOf(user.id) else emptySet() }
    override fun cleanupEmptyConversation(conversationId: String) = Unit
    override fun clearChatNotifications() = Unit
    override suspend fun getConversations(): Result<List<Conversation>> = Result.success(conversations.value)
    override fun observeConversations(): Flow<List<Conversation>> = conversations
    override fun observeMessages(conversationId: String): Flow<List<Message>> = messages
    override suspend fun loadOlderMessages(conversationId: String, limit: Int): Result<Boolean> = Result.success(false)
    override fun observeParticipantCandidates(): Flow<List<User>> = flowOf(emptyList())
    override suspend fun searchConversationCandidates(query: String, limit: Int, offset: Int) = Result.success(ChatConversationCandidatePage(emptyList(), false, 0, ""))
    override suspend fun matchRegisteredContactPhones(phoneCandidates: Collection<String>) = Result.success(emptySet<String>())
    override suspend fun openPrivateConversation(peerProfileId: String) = Result.success(conversationId)
    override suspend fun sendMessage(conversationId: String, text: String, attachmentUri: String?, attachmentName: String?, attachmentMimeType: String?, clientMessageId: String?): Result<Unit> {
        if (text != SyntheticMessage) return Result.failure(IllegalArgumentException("unexpected_fixture_message"))
        sends += 1
        messages.value = messages.value + Message("fixture-$sends", conversationId, user.id, user.displayName, text, sends.toString(), sends.toLong(), isMine = true, clientMessageId = clientMessageId)
        publishChatE2eSend(sends)
        return Result.success(Unit)
    }
    override suspend fun sendReply(conversationId: String, text: String, replyTo: Message, attachmentUri: String?, attachmentName: String?, attachmentMimeType: String?, clientMessageId: String?) = sendMessage(conversationId, text, attachmentUri, attachmentName, attachmentMimeType, clientMessageId)
    override suspend fun sendSosMessage(contactIds: List<String>, text: String, lat: Double?, lng: Double?, accuracy: Double?) = Result.success(conversationId)
    override suspend fun cachedPrivateConversationId(userId: String): String? = null
    override suspend fun cachedCommunityConversationId(communityName: String): String? = null
    override suspend fun openCommunityConversation(communityId: String, title: String, participantIds: List<String>) = Result.success(conversationId)
    override suspend fun openGroupConversation(participantIds: List<String>, title: String?) = Result.success(conversationId)
    override suspend fun markConversationRead(conversationId: String): Result<Unit> {
        conversations.value = conversations.value.map { conversation ->
            if (conversation.id == conversationId) conversation.copy(unreadCount = 0) else conversation
        }
        return Result.success(Unit)
    }
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
    override suspend fun forwardMessage(message: Message, conversationIds: List<String>) =
        Result.success(ChatForwardResult(requestedCount = conversationIds.distinct().size, sentCount = conversationIds.distinct().size))
    override suspend fun flushPendingMessages() = true
    override suspend fun retryPendingMessage(clientMessageId: String) = Result.success(Unit)
}

@JsFun("""(count) => { globalThis.__quataChatE2eProduct = Object.freeze({ version: 1, sends: count, text: 'mensaje AX local' }); }""")
private external fun publishChatE2eSend(count: Int)
