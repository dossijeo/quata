package com.quata.feature.chat.presentation.chat

import com.quata.core.common.AppDispatchers
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.feature.chat.domain.ChatConversationCandidate
import com.quata.feature.chat.domain.ChatConversationCandidatePage
import com.quata.feature.chat.domain.ChatForwardResult
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

    @Test
    fun successfulGroupParticipantAddUpdatesVisibleConversationBeforeRemoteRefresh() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = GroupParticipantRepository()
        val model = ChatViewModel(
            conversationId = "conversation-1",
            repository = repository,
            dispatchers = AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
        )

        testScheduler.advanceUntilIdle()

        model.onEvent(ChatUiEvent.OpenAddParticipants)
        testScheduler.advanceUntilIdle()
        model.onEvent(ChatUiEvent.ParticipantSelectionToggled("person-3"))
        model.onEvent(ChatUiEvent.AddSelectedParticipants)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("person-1", "person-2", "person-3"), model.uiState.value.conversation?.participantIds)
        assertEquals(listOf("Gabrielo", "Gabrielu", "Nsue"), model.uiState.value.conversation?.participantNames)
        assertEquals(listOf("avatar-1", "avatar-2", "avatar-3"), model.uiState.value.conversation?.participantAvatarUrls)
        assertEquals(listOf("person-3"), repository.addedParticipantIds)
        model.close()
    }
}

private class GroupParticipantRepository : ChatRepository {
    override val activeConversationId = MutableStateFlow<String?>(null)
    override val isAppForeground = MutableStateFlow(true)
    override val pendingDeletedConversation = MutableStateFlow<Conversation?>(null)
    override val isRealtimeOnline = MutableStateFlow(true)
    override val typingProfileIds = MutableStateFlow(emptySet<String>())
    override val syncStatus = MutableStateFlow(ChatSyncStatus.Online)
    private val conversations = MutableStateFlow(
        listOf(
            Conversation(
                id = "conversation-1",
                title = "Group",
                lastMessagePreview = "",
                participantIds = listOf("person-1", "person-2"),
                participantNames = listOf("Gabrielo", "Gabrielu"),
                participantAvatarUrls = listOf("avatar-1", "avatar-2"),
                isGroup = true,
                moderatorIds = listOf("person-1"),
            )
        )
    )
    var addedParticipantIds: List<String> = emptyList()

    override fun setDeviceNetworkAvailable(isAvailable: Boolean) = Unit
    override fun currentUser(): User? = User("person-1", "gabrielo@example.invalid", "Gabrielo")
    override fun setActiveConversation(conversationId: String?) = Unit
    override fun setConversationVisible(conversationId: String, visible: Boolean) = Unit
    override fun setAppForeground(isForeground: Boolean) { isAppForeground.value = isForeground }
    override fun setTyping(conversationId: String, isTyping: Boolean) = Unit
    override fun cleanupEmptyConversation(conversationId: String) = Unit
    override fun clearChatNotifications() = Unit
    override suspend fun getConversations(): Result<List<Conversation>> = Result.success(conversations.value)
    override fun observeConversations(): Flow<List<Conversation>> = conversations
    override fun observeMessages(conversationId: String): Flow<List<Message>> = emptyFlow()
    override suspend fun loadOlderMessages(conversationId: String, limit: Int): Result<Boolean> = Result.success(false)
    override fun observeParticipantCandidates(): Flow<List<User>> = emptyFlow()
    override suspend fun searchConversationCandidates(query: String, limit: Int, offset: Int): Result<ChatConversationCandidatePage> =
        Result.success(
            ChatConversationCandidatePage(
                candidates = listOf(
                    ChatConversationCandidate(
                        profileId = "person-3",
                        displayName = "Nsue",
                        neighborhood = "Bovano",
                        phone = "+240680000000",
                        avatarUrl = "avatar-3",
                        sectionKey = "bovano",
                        neighborhoodGroup = "Bovano",
                        existingConversationId = null,
                    )
                ),
                hasMore = false,
                nextOffset = 1,
                actorNeighborhood = "Bovano",
            )
        )
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
    override suspend fun addParticipants(conversationId: String, participantIds: List<String>): Result<Unit> {
        addedParticipantIds = participantIds
        return Result.success(Unit)
    }
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
    override suspend fun forwardMessage(message: Message, conversationIds: List<String>): Result<ChatForwardResult> =
        Result.success(ChatForwardResult(requestedCount = conversationIds.distinct().size, sentCount = conversationIds.distinct().size))
    override suspend fun flushPendingMessages(): Boolean = true
    override suspend fun retryPendingMessage(clientMessageId: String): Result<Unit> = Result.success(Unit)
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
    override suspend fun forwardMessage(message: Message, conversationIds: List<String>): Result<ChatForwardResult> =
        Result.success(ChatForwardResult(requestedCount = conversationIds.distinct().size, sentCount = conversationIds.distinct().size))
    override suspend fun flushPendingMessages(): Boolean = true
    override suspend fun retryPendingMessage(clientMessageId: String): Result<Unit> = Result.success(Unit)
}
