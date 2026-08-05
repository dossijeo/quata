package com.quata.feature.chat.presentation.chat

import com.quata.core.common.AppDispatchers
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.feature.chat.domain.ChatConversationCandidatePage
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.domain.ChatSyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class ChatViewModelParticipantCandidatesTest {
    @Test
    fun participantCandidateObservationFailureIsSurfaced() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val model = ChatViewModel(
            conversationId = "conversation-1",
            repository = FailingParticipantCandidateRepository(),
            text = { text -> if (text == ChatText.LoadCandidates) "candidate-load-failed" else "other" },
            dispatchers = AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
        )

        testScheduler.advanceUntilIdle()

        assertEquals("candidate-load-failed", model.uiState.value.error)
        assertEquals(emptyList(), model.uiState.value.participantCandidates)
        model.close()
    }
}

private class FailingParticipantCandidateRepository : ChatRepository {
    override val activeConversationId = MutableStateFlow<String?>(null)
    override val isAppForeground = MutableStateFlow(true)
    override val pendingDeletedConversation = MutableStateFlow<Conversation?>(null)
    override val isRealtimeOnline = MutableStateFlow(true)
    override val typingProfileIds = MutableStateFlow(emptySet<String>())
    override val syncStatus = MutableStateFlow(ChatSyncStatus.Online)

    override fun setDeviceNetworkAvailable(isAvailable: Boolean) = Unit
    override fun currentUser(): User? = User("me", "me@example.invalid", "Me")
    override fun setActiveConversation(conversationId: String?) = Unit
    override fun setConversationVisible(conversationId: String, visible: Boolean) = Unit
    override fun setAppForeground(isForeground: Boolean) { isAppForeground.value = isForeground }
    override fun setTyping(conversationId: String, isTyping: Boolean) = Unit
    override fun cleanupEmptyConversation(conversationId: String) = Unit
    override fun clearChatNotifications() = Unit
    override suspend fun getConversations(): Result<List<Conversation>> = Result.success(emptyList())
    override fun observeConversations(): Flow<List<Conversation>> = emptyFlow()
    override fun observeMessages(conversationId: String): Flow<List<Message>> = emptyFlow()
    override suspend fun loadOlderMessages(conversationId: String, limit: Int): Result<Boolean> = Result.success(false)
    override fun observeParticipantCandidates(): Flow<List<User>> = flow { error("candidate-source-failed") }
    override suspend fun searchConversationCandidates(query: String, limit: Int, offset: Int): Result<ChatConversationCandidatePage> =
        Result.failure(UnsupportedOperationException("unused"))
    override suspend fun matchRegisteredContactPhones(phoneCandidates: Collection<String>): Result<Set<String>> = Result.success(emptySet())
    override suspend fun openPrivateConversation(peerProfileId: String): Result<String> = Result.failure(UnsupportedOperationException("unused"))
    override suspend fun sendMessage(conversationId: String, text: String, attachmentUri: String?, attachmentName: String?, attachmentMimeType: String?, clientMessageId: String?): Result<Unit> = Result.success(Unit)
    override suspend fun sendReply(conversationId: String, text: String, replyTo: Message, attachmentUri: String?, attachmentName: String?, attachmentMimeType: String?, clientMessageId: String?): Result<Unit> = Result.success(Unit)
    override suspend fun sendSosMessage(contactIds: List<String>, text: String, lat: Double?, lng: Double?, accuracy: Double?): Result<String> = Result.success("sos")
    override suspend fun cachedPrivateConversationId(userId: String): String? = null
    override suspend fun cachedCommunityConversationId(communityName: String): String? = null
    override suspend fun openCommunityConversation(communityId: String, title: String, participantIds: List<String>): Result<String> = Result.success("community")
    override suspend fun openGroupConversation(participantIds: List<String>, title: String?): Result<String> = Result.success("group")
    override suspend fun markConversationRead(conversationId: String): Result<Unit> = Result.success(Unit)
    override suspend fun setConversationMuted(conversationId: String, muted: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun setMemberInvitesEnabled(conversationId: String, enabled: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun addParticipants(conversationId: String, participantIds: List<String>): Result<Unit> = Result.success(Unit)
    override suspend fun promoteModerator(conversationId: String, userId: String): Result<Unit> = Result.success(Unit)
    override suspend fun demoteModerator(conversationId: String, userId: String): Result<Unit> = Result.success(Unit)
    override suspend fun removeParticipant(conversationId: String, userId: String): Result<Unit> = Result.success(Unit)
    override suspend fun blockParticipant(conversationId: String, userId: String): Result<Unit> = Result.success(Unit)
    override suspend fun reportMessage(messageId: String): Result<Unit> = Result.success(Unit)
    override suspend fun leaveConversation(conversationId: String): Result<Unit> = Result.success(Unit)
    override suspend fun hideConversation(conversationId: String): Result<Unit> = Result.success(Unit)
    override suspend fun deleteConversation(conversationId: String): Result<Unit> = Result.success(Unit)
    override suspend fun restorePendingDeletedConversation(): Result<Unit> = Result.success(Unit)
    override suspend fun finalizePendingDeletedConversation(): Result<Unit> = Result.success(Unit)
    override suspend fun editMessage(messageId: String, text: String): Result<Unit> = Result.success(Unit)
    override suspend fun deleteMessage(messageId: String): Result<Unit> = Result.success(Unit)
    override suspend fun toggleFavoriteMessage(messageId: String): Result<Unit> = Result.success(Unit)
    override suspend fun forwardMessage(message: Message, conversationIds: List<String>): Result<Unit> = Result.success(Unit)
    override suspend fun flushPendingMessages(): Boolean = true
    override suspend fun retryPendingMessage(clientMessageId: String): Result<Unit> = Result.success(Unit)
}
