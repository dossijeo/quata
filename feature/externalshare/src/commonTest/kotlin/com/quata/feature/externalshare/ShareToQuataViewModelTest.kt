package com.quata.feature.externalshare

import com.quata.core.common.AppDispatchers
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShareToQuataViewModelTest {
    @Test
    fun `sends text payload to selected existing conversation`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = RecordingExternalShareRepository(
            candidates = listOf(candidate("peer-1", existingConversationId = "conversation-1")),
        )
        val model = model(repository, payload(text = "shared text"), dispatcher)

        testScheduler.advanceUntilIdle()
        model.toggle("peer-1")
        model.send()
        testScheduler.advanceUntilIdle()

        assertMessagesEqual(listOf(SentMessage("conversation-1", "shared text")), repository.sentMessages)
        assertTrue(model.uiState.value.isComplete)
        assertEquals("conversation-1", model.uiState.value.completedConversationId)
        model.close()
    }

    @Test
    fun `opens missing private conversation before sending`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = RecordingExternalShareRepository(
            candidates = listOf(candidate("peer-2", existingConversationId = null)),
            openedConversations = mapOf("peer-2" to "conversation-opened"),
        )
        val model = model(repository, payload(text = "hello"), dispatcher)

        testScheduler.advanceUntilIdle()
        model.toggle("peer-2")
        model.send()
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("peer-2"), repository.openedProfileIds)
        assertMessagesEqual(listOf(SentMessage("conversation-opened", "hello")), repository.sentMessages)
        assertTrue(model.uiState.value.isComplete)
        model.close()
    }

    @Test
    fun `sends attachment payload as one message per attachment with text only on first message`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = RecordingExternalShareRepository(
            candidates = listOf(candidate("peer-1", existingConversationId = "conversation-1")),
        )
        val payload = payload(
            text = "caption",
            attachments = listOf(
                ExternalShareAttachment(uri = "content://share/one.pdf", name = "one.pdf", mimeType = "application/pdf"),
                ExternalShareAttachment(uri = "content://share/two.png", name = "two.png", mimeType = "image/png"),
            ),
        )
        val model = model(repository, payload, dispatcher)

        testScheduler.advanceUntilIdle()
        model.toggle("peer-1")
        model.send()
        testScheduler.advanceUntilIdle()

        assertMessagesEqual(
            listOf(
                SentMessage("conversation-1", "caption", "content://share/one.pdf", "one.pdf", "application/pdf"),
                SentMessage("conversation-1", "", "content://share/two.png", "two.png", "image/png"),
            ),
            repository.sentMessages,
        )
        assertEquals(2, repository.sentMessages.mapNotNull { it.clientMessageId }.distinct().size)
        assertTrue(model.uiState.value.isComplete)
        model.close()
    }

    @Test
    fun `direct conversation payload starts sending immediately and completes without picker selection`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = RecordingExternalShareRepository()
        val model = model(repository, payload(text = "direct", directConversationId = "conversation-direct"), dispatcher)

        testScheduler.advanceUntilIdle()

        assertMessagesEqual(listOf(SentMessage("conversation-direct", "direct")), repository.sentMessages)
        assertTrue(model.uiState.value.isComplete)
        assertEquals("conversation-direct", model.uiState.value.completedConversationId)
        model.close()
    }

    @Test
    fun `send failure is fail closed and keeps payload unsent`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = RecordingExternalShareRepository(
            candidates = listOf(candidate("peer-1", existingConversationId = "conversation-1")),
            failingConversationId = "conversation-1",
        )
        val model = model(repository, payload(text = "will fail"), dispatcher)

        testScheduler.advanceUntilIdle()
        model.toggle("peer-1")
        model.send()
        testScheduler.advanceUntilIdle()

        assertMessagesEqual(listOf(SentMessage("conversation-1", "will fail")), repository.sentMessages)
        assertFalse(model.uiState.value.isSending)
        assertFalse(model.uiState.value.isComplete)
        assertEquals("send failed", model.uiState.value.error)
        model.close()
    }

    @Test
    fun `missing destination cannot be sent and surfaces common error`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = RecordingExternalShareRepository(
            candidates = listOf(candidate("peer-1", existingConversationId = "conversation-1")),
        )
        val model = model(repository, payload(text = "not sent"), dispatcher)

        testScheduler.advanceUntilIdle()
        model.toggle("stale-peer")
        model.send()
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList(), repository.sentMessages)
        assertFalse(model.uiState.value.isComplete)
        assertEquals("send failed", model.uiState.value.error)
        model.close()
    }

    @Test
    fun `recent candidates exclude favorites emergencies hidden and current user only conversations`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = RecordingExternalShareRepository(
            conversations = listOf(
                conversation("visible-private", listOf("me", "peer-1"), "Peer One", updatedAtMillis = 30),
                conversation("favorite", listOf("me", "peer-2"), "Favorite", updatedAtMillis = 40),
                conversation("emergency", listOf("me", "peer-3"), "Emergency", isEmergency = true, updatedAtMillis = 50),
                conversation("hidden", listOf("me", "peer-4"), "Hidden", isVisible = false, updatedAtMillis = 60),
                conversation("self-only", listOf("me"), "Self", updatedAtMillis = 70),
                conversation("group", listOf("me", "peer-5", "peer-6"), "Group", isGroup = true, updatedAtMillis = 20),
            ),
        )
        val model = ShareToQuataViewModel(
            repository = repository,
            payload = payload(text = "shared"),
            conversationTitle = { it.title },
            isFavoriteConversation = { it == "favorite" },
            dispatchers = dispatchers(dispatcher),
        )

        testScheduler.advanceUntilIdle()

        assertEquals(listOf("Peer One", "Group"), model.uiState.value.recentCandidates.map { it.displayName })
        model.close()
    }

    private fun model(
        repository: RecordingExternalShareRepository,
        payload: ExternalSharePayload,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ): ShareToQuataViewModel =
        ShareToQuataViewModel(
            repository = repository,
            payload = payload,
            text = { "send failed" },
            dispatchers = dispatchers(dispatcher),
        )

    private fun dispatchers(dispatcher: kotlinx.coroutines.CoroutineDispatcher): AppDispatchers =
        AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher)

    private fun assertMessagesEqual(expected: List<SentMessage>, actual: List<SentMessage>) {
        assertEquals(expected, actual.map { it.copy(clientMessageId = null) })
    }
}

private data class SentMessage(
    val conversationId: String,
    val text: String,
    val attachmentUri: String? = null,
    val attachmentName: String? = null,
    val attachmentMimeType: String? = null,
    val clientMessageId: String? = null,
)

private class RecordingExternalShareRepository(
    private val conversations: List<Conversation> = emptyList(),
    private val candidates: List<ChatConversationCandidate> = emptyList(),
    private val openedConversations: Map<String, String> = emptyMap(),
    private val failingConversationId: String? = null,
) : ChatRepository {
    override val activeConversationId = MutableStateFlow<String?>(null)
    override val isAppForeground = MutableStateFlow(true)
    override val pendingDeletedConversation = MutableStateFlow<Conversation?>(null)
    override val isRealtimeOnline = MutableStateFlow(true)
    override val typingProfileIds = MutableStateFlow(emptySet<String>())
    override val syncStatus = MutableStateFlow(ChatSyncStatus.Online)
    val sentMessages = mutableListOf<SentMessage>()
    val openedProfileIds = mutableListOf<String>()

    override fun setDeviceNetworkAvailable(isAvailable: Boolean) = Unit
    override fun currentUser(): User? = User("me", "me@example.invalid", "Me")
    override fun setActiveConversation(conversationId: String?) = Unit
    override fun setConversationVisible(conversationId: String, visible: Boolean) = Unit
    override fun setAppForeground(isForeground: Boolean) { isAppForeground.value = isForeground }
    override fun setTyping(conversationId: String, isTyping: Boolean) = Unit
    override fun cleanupEmptyConversation(conversationId: String) = Unit
    override fun clearChatNotifications() = Unit
    override suspend fun getConversations(): Result<List<Conversation>> = Result.success(conversations)
    override fun observeConversations(): Flow<List<Conversation>> = emptyFlow()
    override fun observeMessages(conversationId: String): Flow<List<Message>> = emptyFlow()
    override suspend fun loadOlderMessages(conversationId: String, limit: Int): Result<Boolean> = Result.success(false)
    override fun observeParticipantCandidates(): Flow<List<User>> = emptyFlow()
    override suspend fun searchConversationCandidates(query: String, limit: Int, offset: Int): Result<ChatConversationCandidatePage> =
        Result.success(ChatConversationCandidatePage(candidates, hasMore = false, nextOffset = candidates.size, actorNeighborhood = "Bovano"))
    override suspend fun matchRegisteredContactPhones(phoneCandidates: Collection<String>): Result<Set<String>> = Result.success(emptySet())
    override suspend fun openPrivateConversation(peerProfileId: String): Result<String> {
        openedProfileIds += peerProfileId
        return openedConversations[peerProfileId]?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("no conversation for $peerProfileId"))
    }
    override suspend fun sendMessage(
        conversationId: String,
        text: String,
        attachmentUri: String?,
        attachmentName: String?,
        attachmentMimeType: String?,
        clientMessageId: String?,
    ): Result<Unit> {
        sentMessages += SentMessage(conversationId, text, attachmentUri, attachmentName, attachmentMimeType, clientMessageId)
        return if (conversationId == failingConversationId) Result.failure(IllegalStateException("send failed")) else Result.success(Unit)
    }
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

private fun payload(
    text: String,
    attachments: List<ExternalShareAttachment> = emptyList(),
    directConversationId: String? = null,
) = ExternalSharePayload(
    id = "share-test",
    text = text,
    attachments = attachments,
    directConversationId = directConversationId,
)

private fun candidate(profileId: String, existingConversationId: String?): ChatConversationCandidate =
    ChatConversationCandidate(
        profileId = profileId,
        displayName = profileId,
        neighborhood = "Bovano",
        phone = "+240680000000",
        avatarUrl = null,
        sectionKey = "bovano",
        neighborhoodGroup = "Bovano",
        existingConversationId = existingConversationId,
    )

private fun conversation(
    id: String,
    participantIds: List<String>,
    title: String,
    isGroup: Boolean = false,
    isEmergency: Boolean = false,
    isVisible: Boolean = true,
    updatedAtMillis: Long,
) = Conversation(
    id = id,
    title = title,
    lastMessagePreview = "preview",
    updatedAtMillis = updatedAtMillis,
    participantIds = participantIds,
    isGroup = isGroup,
    isEmergency = isEmergency,
    isVisible = isVisible,
)
