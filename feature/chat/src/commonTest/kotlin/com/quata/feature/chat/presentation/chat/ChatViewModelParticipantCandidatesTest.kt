package com.quata.feature.chat.presentation.chat

import com.quata.core.common.AppDispatchers
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.core.platform.PreferenceStore
import com.quata.feature.chat.domain.ChatConversationCandidate
import com.quata.feature.chat.domain.ChatConversationCandidatePage
import com.quata.feature.chat.domain.ChatForwardResult
import com.quata.feature.chat.domain.ChatInviteContact
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.domain.ChatSyncStatus
import com.quata.feature.chat.presentation.conversations.ConversationsViewModel
import com.quata.feature.chat.presentation.conversations.ConversationSearchPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class ChatViewModelParticipantCandidatesTest {
    @Test
    fun conversationSearchSurvivesColdModelRecreationWithoutCrossingActors() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher)
        val store = MemoryPreferenceStore()

        ConversationsViewModel(
            repository = GroupParticipantRepository(actorId = "actor-a"),
            dispatchers = dispatchers,
            searchPreferences = ConversationSearchPreferences(store),
        ).also { first ->
            testScheduler.advanceUntilIdle()
            first.onConversationQueryChanged("Marcador frío")
            testScheduler.advanceUntilIdle()
            first.close()
        }

        ConversationsViewModel(
            repository = GroupParticipantRepository(actorId = "actor-a"),
            dispatchers = dispatchers,
            searchPreferences = ConversationSearchPreferences(store),
        ).also { relaunched ->
            testScheduler.advanceUntilIdle()
            assertEquals("Marcador frío", relaunched.uiState.value.searchQuery)
            relaunched.close()
        }

        ConversationsViewModel(
            repository = GroupParticipantRepository(actorId = "actor-b"),
            dispatchers = dispatchers,
            searchPreferences = ConversationSearchPreferences(store),
        ).also { otherActor ->
            testScheduler.advanceUntilIdle()
            assertEquals("", otherActor.uiState.value.searchQuery)
            otherActor.close()
        }
    }

    @Test
    fun clearingConversationSearchRemovesColdRelaunchState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher)
        val store = MemoryPreferenceStore()
        val preferences = ConversationSearchPreferences(store)
        val repository = GroupParticipantRepository()
        val model = ConversationsViewModel(repository, dispatchers = dispatchers, searchPreferences = preferences)

        testScheduler.advanceUntilIdle()
        model.onConversationQueryChanged("temporal")
        testScheduler.advanceUntilIdle()
        model.onConversationQueryChanged("")
        testScheduler.advanceUntilIdle()
        model.close()

        val relaunched = ConversationsViewModel(
            GroupParticipantRepository(),
            dispatchers = dispatchers,
            searchPreferences = preferences,
        )
        testScheduler.advanceUntilIdle()
        assertEquals("", relaunched.uiState.value.searchQuery)
        relaunched.close()
    }

    @Test
    fun delayedColdRestoreCannotOverwriteNewSearchInput() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = BlockingPreferenceStore("stale search")
        val model = ConversationsViewModel(
            repository = GroupParticipantRepository(),
            dispatchers = AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
            searchPreferences = ConversationSearchPreferences(store),
        )

        testScheduler.runCurrent()
        model.onConversationQueryChanged("new search")
        store.releaseRead()
        testScheduler.advanceUntilIdle()

        assertEquals("new search", model.uiState.value.searchQuery)
        model.close()
    }

    @Test
    fun actorLossInvalidatesPendingRestoreBeforeAnotherActorIsRestored() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = GroupParticipantRepository(actorId = "actor-a")
        val store = ActorTransitionPreferenceStore()
        val model = ConversationsViewModel(
            repository = repository,
            dispatchers = AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
            searchPreferences = ConversationSearchPreferences(store),
        )

        testScheduler.runCurrent()
        assertTrue(store.actorAReadStarted.isCompleted)
        repository.setActor(null)
        testScheduler.runCurrent()
        assertEquals("", model.uiState.value.searchQuery)

        store.releaseActorARead()
        testScheduler.runCurrent()
        assertEquals("", model.uiState.value.searchQuery)

        repository.setActor("actor-b")
        testScheduler.advanceUntilIdle()
        assertEquals("saved for b", model.uiState.value.searchQuery)
        model.close()
    }

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
    fun conversationsCandidatePaginationAppendsDistinctPagesAndStopsAtEnd() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = GroupParticipantRepository()
        val model = ConversationsViewModel(
            repository = repository,
            dispatchers = AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
        )

        model.openNewConversationPicker()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("person-3"), model.uiState.value.conversationCandidates.map { it.profileId })
        assertEquals(listOf(0), repository.candidateOffsets)

        model.loadMoreConversationCandidates()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("person-3", "person-4"), model.uiState.value.conversationCandidates.map { it.profileId })
        assertEquals(listOf(0, 1), repository.candidateOffsets)
        assertFalse(model.uiState.value.candidateHasMore)

        model.loadMoreConversationCandidates()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(0, 1), repository.candidateOffsets)
        model.close()
    }

    @Test
    fun conversationsGroupCreationUsesExactSelectionAndClearsPickerState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = GroupParticipantRepository()
        val model = ConversationsViewModel(
            repository = repository,
            dispatchers = AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
        )

        model.openNewConversationPicker()
        testScheduler.advanceUntilIdle()
        model.loadMoreConversationCandidates()
        testScheduler.advanceUntilIdle()
        model.uiState.value.conversationCandidates.forEach(model::toggleNewConversationCandidate)
        model.onNewGroupTitleChanged("Grupo focal")
        var opened: String? = null
        model.openSelectedGroupConversation { opened = it }
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("person-3", "person-4"), repository.openedGroupParticipantIds.sorted())
        assertEquals("Grupo focal", repository.openedGroupTitle)
        assertEquals("group", opened)
        assertFalse(model.uiState.value.isNewConversationPickerOpen)
        assertEquals(emptySet(), model.uiState.value.selectedNewConversationProfileIds)
        assertEquals("", model.uiState.value.newGroupTitle)
        model.close()
    }

    @Test
    fun conversationsAcceptExplicitlyPickedContactsWithoutReadingAddressBook() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val model = ConversationsViewModel(
            repository = GroupParticipantRepository(),
            readContacts = { error("address book must not be enumerated for explicit picker contacts") },
            dispatchers = AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
        )
        val picked = ChatInviteContact(
            id = "platform-contact:34611111111",
            displayName = "Ada Invitada",
            phone = "+34 611 111 111",
            phoneKeys = setOf("34611111111"),
            internationalPhone = "+34 611 111 111",
        )

        model.openNewConversationPicker()
        model.loadInviteContacts(listOf(picked))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(picked), model.uiState.value.inviteContacts)
        model.close()
    }

    @Test
    fun conversationsRealtimeRecoveryClearsOnlyLoadErrorAndPreservesOperationalFailure() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = GroupParticipantRepository().apply {
            restoreResult = Result.failure(IllegalStateException("restore-failed"))
        }
        val model = ConversationsViewModel(
            repository = repository,
            text = { text ->
                when (text) {
                    ChatText.LoadConversations -> "load-failed"
                    ChatText.RestoreConversation -> "restore-failed"
                    else -> "other"
                }
            },
            dispatchers = AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
        )

        testScheduler.advanceUntilIdle()
        model.onEvent(com.quata.feature.chat.presentation.conversations.ConversationsUiEvent.RestoreDeletedConversation)
        testScheduler.advanceUntilIdle()
        assertEquals("restore-failed", model.uiState.value.error)

        repository.emitConversationRefresh()
        testScheduler.advanceUntilIdle()

        assertEquals("restore-failed", model.uiState.value.error)
        assertNull(model.uiState.value.loadError)
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

private class GroupParticipantRepository(
    actorId: String = "person-1",
) : ChatRepository {
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
    private val participantCandidates = MutableStateFlow(emptyList<User>())
    private var currentActorId: String? = actorId
    private var actorChangeSequence = 0
    var addedParticipantIds: List<String> = emptyList()
    val candidateOffsets = mutableListOf<Int>()
    var openedGroupParticipantIds: List<String> = emptyList()
    var openedGroupTitle: String? = null
    var restoreResult: Result<Unit> = Result.success(Unit)

    fun emitConversationRefresh() {
        conversations.value = conversations.value.map { conversation ->
            conversation.copy(updatedAt = "refreshed")
        }
    }

    fun setActor(actorId: String?) {
        currentActorId = actorId
        actorChangeSequence += 1
        participantCandidates.value = listOf(User("actor-change-$actorChangeSequence", "", ""))
    }

    override fun setDeviceNetworkAvailable(isAvailable: Boolean) = Unit
    override fun currentUser(): User? = currentActorId?.let { User(it, "gabrielo@example.invalid", "Gabrielo") }
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
    override fun observeParticipantCandidates(): Flow<List<User>> = participantCandidates
    override suspend fun searchConversationCandidates(query: String, limit: Int, offset: Int): Result<ChatConversationCandidatePage> {
        candidateOffsets += offset
        val person3 = ChatConversationCandidate(
            profileId = "person-3", displayName = "Nsue", neighborhood = "Bovano",
            phone = "+240680000000", avatarUrl = "avatar-3", sectionKey = "bovano",
            neighborhoodGroup = "Bovano", existingConversationId = null,
        )
        val person4 = ChatConversationCandidate(
            profileId = "person-4", displayName = "Esono", neighborhood = "Bovano",
            phone = "+240680000001", avatarUrl = "avatar-4", sectionKey = "bovano",
            neighborhoodGroup = "Bovano", existingConversationId = null,
        )
        return Result.success(
            if (offset == 0) ChatConversationCandidatePage(listOf(person3), true, 1, "Bovano")
            else ChatConversationCandidatePage(listOf(person3, person4), false, 3, "Bovano")
        )
    }
    override suspend fun matchRegisteredContactPhones(phoneCandidates: Collection<String>): Result<Set<String>> = Result.success(emptySet())
    override suspend fun openPrivateConversation(peerProfileId: String): Result<String> = Result.failure(UnsupportedOperationException("unused"))
    override suspend fun sendMessage(conversationId: String, text: String, attachmentUri: String?, attachmentName: String?, attachmentMimeType: String?, clientMessageId: String?, expectedActorId: String?): Result<Unit> = Result.success(Unit)
    override suspend fun sendReply(conversationId: String, text: String, replyTo: Message, attachmentUri: String?, attachmentName: String?, attachmentMimeType: String?, clientMessageId: String?): Result<Unit> = Result.success(Unit)
    override suspend fun sendSosMessage(contactIds: List<String>, text: String, lat: Double?, lng: Double?, accuracy: Double?, expectedActorId: String?): Result<String> = Result.success("sos")
    override suspend fun cachedPrivateConversationId(userId: String): String? = null
    override suspend fun cachedCommunityConversationId(communityName: String): String? = null
    override suspend fun openCommunityConversation(communityId: String, title: String, participantIds: List<String>): Result<String> = Result.success("community")
    override suspend fun openGroupConversation(participantIds: List<String>, title: String?): Result<String> {
        openedGroupParticipantIds = participantIds
        openedGroupTitle = title
        return Result.success("group")
    }
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
    override suspend fun restorePendingDeletedConversation(): Result<Unit> = restoreResult
    override suspend fun finalizePendingDeletedConversation(): Result<Unit> = Result.success(Unit)
    override suspend fun editMessage(messageId: String, text: String): Result<Unit> = Result.success(Unit)
    override suspend fun deleteMessage(messageId: String): Result<Unit> = Result.success(Unit)
    override suspend fun toggleFavoriteMessage(messageId: String): Result<Unit> = Result.success(Unit)
    override suspend fun forwardMessage(message: Message, conversationIds: List<String>): Result<ChatForwardResult> =
        Result.success(ChatForwardResult(requestedCount = conversationIds.distinct().size, sentCount = conversationIds.distinct().size))
    override suspend fun flushPendingMessages(): Boolean = true
    override suspend fun retryPendingMessage(clientMessageId: String): Result<Unit> = Result.success(Unit)
}

private class MemoryPreferenceStore : PreferenceStore {
    private val values = mutableMapOf<String, String>()

    override suspend fun getString(key: String): String? = values[key]
    override suspend fun putString(key: String, value: String) { values[key] = value }
    override suspend fun remove(key: String) { values.remove(key) }
}

private class BlockingPreferenceStore(
    private val restored: String,
) : PreferenceStore {
    private val readGate = CompletableDeferred<Unit>()

    fun releaseRead() { readGate.complete(Unit) }

    override suspend fun getString(key: String): String? {
        readGate.await()
        return restored
    }

    override suspend fun putString(key: String, value: String) = Unit
    override suspend fun remove(key: String) = Unit
}

private class ActorTransitionPreferenceStore : PreferenceStore {
    val actorAReadStarted = CompletableDeferred<Unit>()
    private val actorAReadGate = CompletableDeferred<Unit>()

    fun releaseActorARead() { actorAReadGate.complete(Unit) }

    override suspend fun getString(key: String): String? = when {
        key.endsWith("actor-a") -> {
            actorAReadStarted.complete(Unit)
            actorAReadGate.await()
            "stale from a"
        }
        key.endsWith("actor-b") -> "saved for b"
        else -> null
    }

    override suspend fun putString(key: String, value: String) = Unit
    override suspend fun remove(key: String) = Unit
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
    override suspend fun sendMessage(conversationId: String, text: String, attachmentUri: String?, attachmentName: String?, attachmentMimeType: String?, clientMessageId: String?, expectedActorId: String?): Result<Unit> = Result.success(Unit)
    override suspend fun sendReply(conversationId: String, text: String, replyTo: Message, attachmentUri: String?, attachmentName: String?, attachmentMimeType: String?, clientMessageId: String?): Result<Unit> = Result.success(Unit)
    override suspend fun sendSosMessage(contactIds: List<String>, text: String, lat: Double?, lng: Double?, accuracy: Double?, expectedActorId: String?): Result<String> = Result.success("sos")
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
