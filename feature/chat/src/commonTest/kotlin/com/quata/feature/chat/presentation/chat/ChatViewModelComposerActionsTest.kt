package com.quata.feature.chat.presentation.chat

import com.quata.core.common.AppDispatchers
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.feature.chat.domain.ChatConversationCandidatePage
import com.quata.feature.chat.domain.ChatForwardResult
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.domain.ChatSyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class ChatViewModelComposerActionsTest {
    @Test
    fun composerSendsTextAttachmentPayloadAndClearsTypingAcrossPlatforms() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = RecordingChatRepository(
            messages = listOf(otherMessage(id = "message-1")),
        )
        val model = chatViewModel(repository, dispatcher)

        testScheduler.advanceUntilIdle()

        model.onEvent(ChatUiEvent.MessageChanged("hello team"))
        model.onEvent(ChatUiEvent.AttachmentSelected("file://image.jpg", "image.jpg", "image/jpeg"))
        model.onEvent(ChatUiEvent.Send)
        testScheduler.advanceUntilIdle()

        assertEquals(
            listOf(TypingCall("conversation-1", true), TypingCall("conversation-1", false)),
            repository.typingCalls,
        )
        assertEquals(
            SendMessageCall(
                conversationId = "conversation-1",
                text = "hello team",
                attachmentUri = "file://image.jpg",
                attachmentName = "image.jpg",
                attachmentMimeType = "image/jpeg",
                hasClientMessageId = true,
            ),
            repository.sendMessageCalls.single(),
        )
        assertEquals("", model.uiState.value.messageText)
        assertNull(model.uiState.value.attachmentUri)
        assertTrue(model.uiState.value.messages.any { it.isLocalEcho && it.deliveryState.name == "Sent" })

        model.close()
    }

    @Test
    fun replyAndEditModesDispatchSharedRepositoryCallsAndCanBeCancelled() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val own = ownMessage(id = "own-1", text = "before")
        val other = otherMessage(id = "other-1", text = "question")
        val repository = RecordingChatRepository(messages = listOf(other, own))
        val model = chatViewModel(repository, dispatcher)

        testScheduler.advanceUntilIdle()

        model.onEvent(ChatUiEvent.MessageSelected(other.id))
        model.onEvent(ChatUiEvent.StartReply)
        assertEquals(other.id, model.uiState.value.replyToMessage?.id)

        model.onEvent(ChatUiEvent.MessageChanged("answer"))
        model.onEvent(ChatUiEvent.Send)
        testScheduler.advanceUntilIdle()

        assertEquals(
            SendReplyCall(
                conversationId = "conversation-1",
                text = "answer",
                replyToMessageId = other.id,
                hasClientMessageId = true,
            ),
            repository.sendReplyCalls.single(),
        )
        assertNull(model.uiState.value.replyToMessage)

        model.onEvent(ChatUiEvent.MessageSelected(own.id))
        model.onEvent(ChatUiEvent.StartEdit)
        assertEquals(own.id, model.uiState.value.editingMessage?.id)
        assertEquals("before", model.uiState.value.messageText)

        model.onEvent(ChatUiEvent.CancelEdit)
        assertNull(model.uiState.value.editingMessage)
        assertEquals("", model.uiState.value.messageText)

        model.onEvent(ChatUiEvent.MessageSelected(own.id))
        model.onEvent(ChatUiEvent.StartEdit)
        model.onEvent(ChatUiEvent.MessageChanged("after"))
        model.onEvent(ChatUiEvent.Send)
        testScheduler.advanceUntilIdle()

        assertEquals(EditMessageCall(own.id, "after"), repository.editMessageCalls.single())
        assertNull(model.uiState.value.editingMessage)
        assertEquals("", model.uiState.value.messageText)

        model.close()
    }

    @Test
    fun selectedMessageActionsRespectOwnershipAndSurfaceFailures() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val own = ownMessage(id = "own-1")
        val other = otherMessage(id = "other-1")
        val repository = RecordingChatRepository(messages = listOf(other, own))
        val model = chatViewModel(repository, dispatcher)

        testScheduler.advanceUntilIdle()

        model.onEvent(ChatUiEvent.MessageSelected(own.id))
        model.onEvent(ChatUiEvent.ToggleFavoriteSelected)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(own.id), repository.toggleFavoriteMessageCalls)
        assertNull(model.uiState.value.selectedMessageId)

        model.onEvent(ChatUiEvent.MessageSelected(own.id))
        repository.deleteMessageResult = Result.failure(IllegalStateException("delete failed"))
        model.onEvent(ChatUiEvent.DeleteSelectedMessage)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(own.id), repository.deleteMessageCalls)
        assertEquals("delete-message", model.uiState.value.error)

        model.onEvent(ChatUiEvent.ClearError)
        model.onEvent(ChatUiEvent.MessageSelected(other.id))
        model.onEvent(ChatUiEvent.ReportSelectedMessage)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(other.id), repository.reportMessageCalls)
        assertEquals("report-sent", model.uiState.value.notice)
        assertNull(model.uiState.value.selectedMessageId)

        model.close()
    }

    @Test
    fun messageActionGuardsRejectLocalEchoDeletedAndWrongOwnerTargets() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val own = ownMessage(id = "own-1")
        val other = otherMessage(id = "other-1")
        val local = ownMessage(id = "local:abc", isLocalEcho = true)
        val deleted = ownMessage(id = "deleted-1", isDeleted = true)
        val repository = RecordingChatRepository(messages = listOf(other, own, local, deleted))
        val model = chatViewModel(repository, dispatcher)

        testScheduler.advanceUntilIdle()

        model.onEvent(ChatUiEvent.MessageSelected(other.id))
        model.onEvent(ChatUiEvent.StartEdit)
        assertNull(model.uiState.value.editingMessage)

        model.onEvent(ChatUiEvent.MessageSelected(deleted.id))
        model.onEvent(ChatUiEvent.StartEdit)
        assertNull(model.uiState.value.editingMessage)

        model.onEvent(ChatUiEvent.MessageSelected(local.id))
        model.onEvent(ChatUiEvent.StartReply)
        model.onEvent(ChatUiEvent.ToggleFavoriteSelected)
        model.onEvent(ChatUiEvent.DeleteSelectedMessage)
        testScheduler.advanceUntilIdle()

        assertNull(model.uiState.value.replyToMessage)
        assertTrue(repository.toggleFavoriteMessageCalls.isEmpty())
        assertTrue(repository.deleteMessageCalls.isEmpty())

        model.onEvent(ChatUiEvent.MessageSelected(other.id))
        model.onEvent(ChatUiEvent.DeleteSelectedMessage)
        model.onEvent(ChatUiEvent.MessageSelected(own.id))
        model.onEvent(ChatUiEvent.ReportSelectedMessage)
        testScheduler.advanceUntilIdle()

        assertTrue(repository.deleteMessageCalls.isEmpty())
        assertTrue(repository.reportMessageCalls.isEmpty())

        model.close()
    }

    @Test
    fun sendAndEditFailuresRestoreDraftsWithCommonErrors() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val own = ownMessage(id = "own-1", text = "before")
        val repository = RecordingChatRepository(messages = listOf(own))
        val model = chatViewModel(repository, dispatcher)

        testScheduler.advanceUntilIdle()

        repository.sendMessageResult = Result.failure(IllegalStateException("send failed"))
        model.onEvent(ChatUiEvent.MessageChanged("retry me"))
        model.onEvent(ChatUiEvent.Send)
        testScheduler.advanceUntilIdle()

        assertEquals("send", model.uiState.value.error)
        assertEquals("retry me", model.uiState.value.messageText)
        assertFalse(model.uiState.value.messages.any { it.isLocalEcho })

        model.onEvent(ChatUiEvent.ClearError)
        repository.sendMessageResult = Result.success(Unit)
        repository.editMessageResult = Result.failure(IllegalStateException("edit failed"))
        model.onEvent(ChatUiEvent.MessageSelected(own.id))
        model.onEvent(ChatUiEvent.StartEdit)
        model.onEvent(ChatUiEvent.MessageChanged("edited draft"))
        model.onEvent(ChatUiEvent.Send)
        testScheduler.advanceUntilIdle()

        assertEquals("send", model.uiState.value.error)
        assertEquals("edited draft", model.uiState.value.messageText)
        assertEquals(own.id, model.uiState.value.editingMessage?.id)
        assertEquals(own.id, model.uiState.value.selectedMessageId)

        model.close()
    }

    @Test
    fun forwardMessageKeepsSelectionStableUntilEveryDestinationSucceeds() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val own = ownMessage(id = "123")
        val repository = RecordingChatRepository(messages = listOf(own)).apply {
            openPrivateConversationResults["profile-a"] = Result.success("conversation-2")
            openPrivateConversationResults["profile-b"] = Result.success("conversation-3")
        }
        val model = chatViewModel(repository, dispatcher)

        testScheduler.advanceUntilIdle()

        model.onEvent(ChatUiEvent.MessageSelected(own.id))
        model.onEvent(ChatUiEvent.OpenForwardDialog)
        model.onEvent(ChatUiEvent.ForwardProfileToggled("profile-a"))
        model.onEvent(ChatUiEvent.ForwardProfileToggled("profile-b"))
        model.onEvent(ChatUiEvent.SendForward)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("profile-a", "profile-b"), repository.openPrivateConversationCalls)
        assertEquals(ForwardMessageCall(own.id, listOf("conversation-2", "conversation-3")), repository.forwardMessageCalls.single())
        assertFalse(model.uiState.value.isForwardDialogOpen)
        assertTrue(model.uiState.value.selectedForwardProfileIds.isEmpty())
        assertNull(model.uiState.value.selectedMessageId)

        model.close()
    }

    @Test
    fun forwardMessagePartialFailureKeepsPickerOpenAndDoesNotDropRetryContext() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val own = ownMessage(id = "456")
        val repository = RecordingChatRepository(messages = listOf(own)).apply {
            openPrivateConversationResults["profile-a"] = Result.success("conversation-2")
            openPrivateConversationResults["profile-b"] = Result.failure(IllegalStateException("open failed"))
            forwardMessageResult = Result.success(ChatForwardResult(requestedCount = 1, sentCount = 1, errorCount = 1))
        }
        val model = chatViewModel(repository, dispatcher)

        testScheduler.advanceUntilIdle()

        model.onEvent(ChatUiEvent.MessageSelected(own.id))
        model.onEvent(ChatUiEvent.OpenForwardDialog)
        model.onEvent(ChatUiEvent.ForwardProfileToggled("profile-a"))
        model.onEvent(ChatUiEvent.ForwardProfileToggled("profile-b"))
        model.onEvent(ChatUiEvent.SendForward)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("profile-a", "profile-b"), repository.openPrivateConversationCalls)
        assertEquals(ForwardMessageCall(own.id, listOf("conversation-2")), repository.forwardMessageCalls.single())
        assertTrue(model.uiState.value.isForwardDialogOpen)
        assertEquals(listOf("profile-a", "profile-b"), model.uiState.value.selectedForwardProfileIds)
        assertEquals(own.id, model.uiState.value.selectedMessageId)
        assertEquals("forward", model.uiState.value.error)
        assertFalse(model.uiState.value.isConversationActionInProgress)

        model.close()
    }
}

private fun chatViewModel(
    repository: RecordingChatRepository,
    dispatcher: kotlinx.coroutines.CoroutineDispatcher,
) = ChatViewModel(
    conversationId = "conversation-1",
    repository = repository,
    text = { it.camelToKebab() },
    dispatchers = AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
)

private fun ChatText.camelToKebab(): String =
    name.replace(Regex("([a-z])([A-Z])"), "$1-$2").lowercase()

private fun ownMessage(
    id: String,
    text: String = "own",
    isDeleted: Boolean = false,
    isLocalEcho: Boolean = false,
) = Message(
    id = id,
    conversationId = "conversation-1",
    senderId = "me",
    senderName = "Me",
    text = text,
    sentAt = "2026-08-06T12:00:00Z",
    sentAtMillis = 1000L,
    isMine = true,
    isDeleted = isDeleted,
    isLocalEcho = isLocalEcho,
)

private fun otherMessage(id: String, text: String = "other") = Message(
    id = id,
    conversationId = "conversation-1",
    senderId = "peer",
    senderName = "Peer",
    text = text,
    sentAt = "2026-08-06T12:00:01Z",
    sentAtMillis = 2000L,
    isMine = false,
)

private data class TypingCall(val conversationId: String, val isTyping: Boolean)

private data class SendMessageCall(
    val conversationId: String,
    val text: String,
    val attachmentUri: String?,
    val attachmentName: String?,
    val attachmentMimeType: String?,
    val hasClientMessageId: Boolean,
)

private data class SendReplyCall(
    val conversationId: String,
    val text: String,
    val replyToMessageId: String,
    val hasClientMessageId: Boolean,
)

private data class EditMessageCall(val messageId: String, val text: String)
private data class ForwardMessageCall(val messageId: String, val conversationIds: List<String>)

private class RecordingChatRepository(messages: List<Message>) : ChatRepository {
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
                title = "Chat",
                lastMessagePreview = "Preview",
                participantIds = listOf("me", "peer"),
                participantNames = listOf("Me", "Peer"),
            )
        )
    )
    private val messages = MutableStateFlow(messages)
    private val participantCandidates = MutableStateFlow(emptyList<User>())

    val typingCalls = mutableListOf<TypingCall>()
    val sendMessageCalls = mutableListOf<SendMessageCall>()
    val sendReplyCalls = mutableListOf<SendReplyCall>()
    val editMessageCalls = mutableListOf<EditMessageCall>()
    val deleteMessageCalls = mutableListOf<String>()
    val reportMessageCalls = mutableListOf<String>()
    val toggleFavoriteMessageCalls = mutableListOf<String>()
    val openPrivateConversationCalls = mutableListOf<String>()
    val forwardMessageCalls = mutableListOf<ForwardMessageCall>()

    var sendMessageResult: Result<Unit> = Result.success(Unit)
    var sendReplyResult: Result<Unit> = Result.success(Unit)
    var editMessageResult: Result<Unit> = Result.success(Unit)
    var deleteMessageResult: Result<Unit> = Result.success(Unit)
    var reportMessageResult: Result<Unit> = Result.success(Unit)
    var toggleFavoriteMessageResult: Result<Unit> = Result.success(Unit)
    var forwardMessageResult: Result<ChatForwardResult>? = null
    val openPrivateConversationResults = mutableMapOf<String, Result<String>>()

    override fun setDeviceNetworkAvailable(isAvailable: Boolean) = Unit
    override fun currentUser(): User = User("me", "me@example.invalid", "Me")
    override fun setActiveConversation(conversationId: String?) {
        activeConversationId.value = conversationId
    }
    override fun setConversationVisible(conversationId: String, visible: Boolean) = Unit
    override fun setAppForeground(isForeground: Boolean) {
        isAppForeground.value = isForeground
    }
    override fun setTyping(conversationId: String, isTyping: Boolean) {
        typingCalls += TypingCall(conversationId, isTyping)
    }
    override fun cleanupEmptyConversation(conversationId: String) = Unit
    override fun clearChatNotifications() = Unit
    override suspend fun getConversations(): Result<List<Conversation>> = Result.success(conversations.value)
    override fun observeConversations(): Flow<List<Conversation>> = conversations
    override fun observeMessages(conversationId: String): Flow<List<Message>> = messages
    override suspend fun loadOlderMessages(conversationId: String, limit: Int): Result<Boolean> = Result.success(false)
    override fun observeParticipantCandidates(): Flow<List<User>> = participantCandidates
    override suspend fun searchConversationCandidates(query: String, limit: Int, offset: Int): Result<ChatConversationCandidatePage> =
        Result.failure(UnsupportedOperationException("unused"))
    override suspend fun matchRegisteredContactPhones(phoneCandidates: Collection<String>): Result<Set<String>> = Result.success(emptySet())
    override suspend fun openPrivateConversation(peerProfileId: String): Result<String> {
        openPrivateConversationCalls += peerProfileId
        return openPrivateConversationResults[peerProfileId]
            ?: Result.failure(UnsupportedOperationException("unused"))
    }
    override suspend fun sendMessage(
        conversationId: String,
        text: String,
        attachmentUri: String?,
        attachmentName: String?,
        attachmentMimeType: String?,
        clientMessageId: String?,
    ): Result<Unit> {
        sendMessageCalls += SendMessageCall(
            conversationId = conversationId,
            text = text,
            attachmentUri = attachmentUri,
            attachmentName = attachmentName,
            attachmentMimeType = attachmentMimeType,
            hasClientMessageId = !clientMessageId.isNullOrBlank(),
        )
        return sendMessageResult
    }
    override suspend fun sendReply(
        conversationId: String,
        text: String,
        replyTo: Message,
        attachmentUri: String?,
        attachmentName: String?,
        attachmentMimeType: String?,
        clientMessageId: String?,
    ): Result<Unit> {
        sendReplyCalls += SendReplyCall(
            conversationId = conversationId,
            text = text,
            replyToMessageId = replyTo.id,
            hasClientMessageId = !clientMessageId.isNullOrBlank(),
        )
        return sendReplyResult
    }
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
    override suspend fun reportMessage(messageId: String): Result<Unit> {
        reportMessageCalls += messageId
        return reportMessageResult
    }
    override suspend fun leaveConversation(conversationId: String): Result<Unit> = Result.success(Unit)
    override suspend fun hideConversation(conversationId: String): Result<Unit> = Result.success(Unit)
    override suspend fun deleteConversation(conversationId: String): Result<Unit> = Result.success(Unit)
    override suspend fun restorePendingDeletedConversation(): Result<Unit> = Result.success(Unit)
    override suspend fun finalizePendingDeletedConversation(): Result<Unit> = Result.success(Unit)
    override suspend fun editMessage(messageId: String, text: String): Result<Unit> {
        editMessageCalls += EditMessageCall(messageId, text)
        return editMessageResult
    }
    override suspend fun deleteMessage(messageId: String): Result<Unit> {
        deleteMessageCalls += messageId
        return deleteMessageResult
    }
    override suspend fun toggleFavoriteMessage(messageId: String): Result<Unit> {
        toggleFavoriteMessageCalls += messageId
        return toggleFavoriteMessageResult
    }
    override suspend fun forwardMessage(message: Message, conversationIds: List<String>): Result<ChatForwardResult> {
        forwardMessageCalls += ForwardMessageCall(message.id, conversationIds)
        return forwardMessageResult
            ?: Result.success(ChatForwardResult(requestedCount = conversationIds.distinct().size, sentCount = conversationIds.distinct().size))
    }
    override suspend fun flushPendingMessages(): Boolean = true
    override suspend fun retryPendingMessage(clientMessageId: String): Result<Unit> = Result.success(Unit)
}
