package com.quata.feature.chat.presentation.chat

import com.quata.core.common.AppDispatchers
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.PrefixClearableFileCacheService
import com.quata.core.platform.PrefixClearablePreferenceStore
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class ChatViewModelComposerActionsTest {
    @Test
    fun composerDraftsAreRestoredAcrossModelsAndClearedAfterSend() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val preferences = ComposerMemoryPreferences()
        val store = ChatComposerDraftStore(preferences)
        val repository = RecordingChatRepository(emptyList())
        val first = chatViewModel(repository, dispatcher, store)

        first.onEvent(ChatUiEvent.MessageChanged("durable draft"))
        first.close()
        testScheduler.advanceUntilIdle()
        assertEquals("durable draft", store.read("me", "conversation-1"))

        val restored = chatViewModel(repository, dispatcher, store)
        testScheduler.advanceUntilIdle()
        assertEquals("durable draft", restored.uiState.value.messageText)
        restored.onEvent(ChatUiEvent.Send)
        testScheduler.advanceUntilIdle()
        assertNull(store.read("me", "conversation-1"))
        restored.close()
    }

    @Test
    fun replyDraftRestoresOnlyAfterItsCurrentConversationTargetIsValidated() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = ChatComposerDraftStore(ComposerMemoryPreferences())
        val target = otherMessage(id = "reply-target", text = "question")
        store.writeRecord(
            "me",
            "conversation-1",
            ChatComposerDraftRecord("answer", ChatComposerDraftMode.Reply, target.id),
        )

        val repository = RecordingChatRepository(listOf(target))
        val model = chatViewModel(repository, dispatcher, store)
        testScheduler.advanceUntilIdle()

        assertEquals("answer", model.uiState.value.messageText)
        assertEquals(target.id, model.uiState.value.replyToMessage?.id)
        assertNull(model.uiState.value.editingMessage)
        model.onEvent(ChatUiEvent.Send)
        testScheduler.advanceUntilIdle()

        assertEquals(target.id, repository.sendReplyCalls.single().replyToMessageId)
        assertNull(store.readRecord("me", "conversation-1"))
        model.close()
    }

    @Test
    fun editDraftRestoresOnlyForAValidOwnedMessage() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = ChatComposerDraftStore(ComposerMemoryPreferences())
        val target = ownMessage(id = "edit-target", text = "before")
        store.writeRecord(
            "me",
            "conversation-1",
            ChatComposerDraftRecord("after", ChatComposerDraftMode.Edit, target.id),
        )

        val repository = RecordingChatRepository(listOf(target))
        val model = chatViewModel(repository, dispatcher, store)
        testScheduler.advanceUntilIdle()

        assertEquals("after", model.uiState.value.messageText)
        assertEquals(target.id, model.uiState.value.editingMessage?.id)
        assertNull(model.uiState.value.replyToMessage)
        model.onEvent(ChatUiEvent.Send)
        testScheduler.advanceUntilIdle()

        assertEquals(EditMessageCall(target.id, "after"), repository.editMessageCalls.single())
        assertNull(store.readRecord("me", "conversation-1"))
        model.close()
    }

    @Test
    fun startingReplyOrEditPersistsItsContextBeforeAnyFurtherInput() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)

        val replyStore = ChatComposerDraftStore(ComposerMemoryPreferences())
        val replyTarget = otherMessage("reply-now")
        val replyModel = chatViewModel(RecordingChatRepository(listOf(replyTarget)), dispatcher, replyStore)
        testScheduler.advanceUntilIdle()
        replyModel.onEvent(ChatUiEvent.MessageSelected(replyTarget.id))
        replyModel.onEvent(ChatUiEvent.StartReply)
        replyModel.close()
        testScheduler.advanceUntilIdle()
        assertEquals(
            ChatComposerDraftRecord("", ChatComposerDraftMode.Reply, replyTarget.id),
            replyStore.readRecord("me", "conversation-1"),
        )

        val editStore = ChatComposerDraftStore(ComposerMemoryPreferences())
        val editTarget = ownMessage("edit-now", text = "original")
        val editModel = chatViewModel(RecordingChatRepository(listOf(editTarget)), dispatcher, editStore)
        testScheduler.advanceUntilIdle()
        editModel.onEvent(ChatUiEvent.MessageSelected(editTarget.id))
        editModel.onEvent(ChatUiEvent.StartEdit)
        editModel.close()
        testScheduler.advanceUntilIdle()
        assertEquals(
            ChatComposerDraftRecord("original", ChatComposerDraftMode.Edit, editTarget.id),
            editStore.readRecord("me", "conversation-1"),
        )
    }

    @Test
    fun contextualRestorePagesHistoryUntilTheTargetIsInAnAuthoritativeSnapshot() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = ChatComposerDraftStore(ComposerMemoryPreferences())
        val target = otherMessage("older-reply")
        store.writeRecord(
            "me",
            "conversation-1",
            ChatComposerDraftRecord("older answer", ChatComposerDraftMode.Reply, target.id),
        )
        val repository = RecordingChatRepository(emptyList()).apply {
            olderMessagesToLoad = listOf(target)
        }

        val model = chatViewModel(repository, dispatcher, store)
        testScheduler.advanceUntilIdle()

        assertEquals(1, repository.loadOlderMessagesCalls)
        assertEquals(target.id, model.uiState.value.replyToMessage?.id)
        assertEquals("older answer", model.uiState.value.messageText)
        model.close()
    }

    @Test
    fun userInputCancelsSuspendedContextRestoreWithoutLeavingHistoryLoadingStuck() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = ChatComposerDraftStore(ComposerMemoryPreferences())
        store.writeRecord(
            "me",
            "conversation-1",
            ChatComposerDraftRecord("stale answer", ChatComposerDraftMode.Reply, "missing-target"),
        )
        val pagingStarted = CompletableDeferred<Unit>()
        val neverCompletes = CompletableDeferred<Unit>()
        val repository = object : ChatRepository by RecordingChatRepository(emptyList()) {
            override suspend fun loadOlderMessages(conversationId: String, limit: Int): Result<Boolean> {
                pagingStarted.complete(Unit)
                neverCompletes.await()
                return Result.success(false)
            }
        }
        val model = chatViewModel(repository, dispatcher, store)

        testScheduler.runCurrent()
        assertTrue(pagingStarted.isCompleted)
        assertTrue(model.uiState.value.isLoadingOlderMessages)

        model.onEvent(ChatUiEvent.MessageChanged("new input"))
        testScheduler.advanceUntilIdle()

        assertEquals("new input", model.uiState.value.messageText)
        assertFalse(model.uiState.value.isLoadingOlderMessages)
        assertEquals("new input", store.read("me", "conversation-1"))
        model.close()
    }

    @Test
    fun missingDeletedOrUnownedTargetsDiscardContextualDraftsFailClosed() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cases = listOf(
            Triple(ChatComposerDraftMode.Reply, "missing", emptyList()),
            Triple(ChatComposerDraftMode.Reply, "deleted", listOf(otherMessage("deleted").copy(isDeleted = true))),
            Triple(ChatComposerDraftMode.Edit, "unowned", listOf(otherMessage("unowned"))),
            Triple(ChatComposerDraftMode.Edit, "local", listOf(ownMessage("local", isLocalEcho = true))),
        )

        for ((mode, targetId, messages) in cases) {
            val store = ChatComposerDraftStore(ComposerMemoryPreferences())
            store.writeRecord(
                "me",
                "conversation-1",
                ChatComposerDraftRecord("private draft", mode, targetId),
            )
            val model = chatViewModel(RecordingChatRepository(messages), dispatcher, store)
            testScheduler.advanceUntilIdle()

            assertEquals("", model.uiState.value.messageText, "$mode/$targetId")
            assertNull(model.uiState.value.replyToMessage, "$mode/$targetId")
            assertNull(model.uiState.value.editingMessage, "$mode/$targetId")
            assertNull(store.readRecord("me", "conversation-1"), "$mode/$targetId")
            model.close()
        }
    }

    @Test
    fun failedSendRestoresAndPersistsThePlainDraft() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = ChatComposerDraftStore(ComposerMemoryPreferences())
        val repository = RecordingChatRepository(emptyList()).apply {
            sendMessageResult = Result.failure(IllegalStateException("offline"))
        }
        val model = chatViewModel(repository, dispatcher, store)

        model.onEvent(ChatUiEvent.MessageChanged("retry later"))
        model.onEvent(ChatUiEvent.Send)
        testScheduler.advanceUntilIdle()

        assertEquals("retry later", model.uiState.value.messageText)
        assertEquals("retry later", store.read("me", "conversation-1"))
        model.close()
    }

    @Test
    fun leavingOrDeletingAConversationClearsItsDraft() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        for (event in listOf(ChatUiEvent.LeaveConversation, ChatUiEvent.DeleteConversation)) {
            val store = ChatComposerDraftStore(ComposerMemoryPreferences())
            store.write("me", "conversation-1", "discard me")
            val model = chatViewModel(RecordingChatRepository(emptyList()), dispatcher, store)
            testScheduler.advanceUntilIdle()

            model.onEvent(event)
            testScheduler.advanceUntilIdle()

            assertNull(store.read("me", "conversation-1"))
            model.close()
        }
    }

    @Test
    fun viewportRestoresAcrossModelsAndPersistsAStableMessageAnchor() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = ChatComposerDraftStore(ComposerMemoryPreferences())
        val lease = store.open("me")
        val initial = ChatConversationViewport.Anchored("message-7", 18.5f)
        store.writeViewport(lease, "conversation-1", initial)

        val first = chatViewModel(RecordingChatRepository(listOf(otherMessage("message-7"))), dispatcher, store)
        testScheduler.advanceUntilIdle()

        assertTrue(first.uiState.value.isViewportRestoreReady)
        assertEquals(initial, first.uiState.value.restoredViewport)
        first.consumeRestoredViewport(preserveUntilUserScroll = true)
        assertNull(first.uiState.value.restoredViewport)
        assertTrue(first.uiState.value.isViewportFallbackProtected)
        assertEquals(initial, store.readViewport(lease, "conversation-1"))

        first.persistViewport(ChatConversationViewport.Latest)
        testScheduler.advanceUntilIdle()
        assertEquals(initial, store.readViewport(lease, "conversation-1"))
        first.allowViewportPersistenceAfterUserScroll()
        assertFalse(first.uiState.value.isViewportFallbackProtected)

        val replacement = ChatConversationViewport.Anchored("message-5", 3.25f)
        first.persistViewport(replacement)
        first.close()
        testScheduler.advanceUntilIdle()
        assertEquals(replacement, store.readViewport(lease, "conversation-1"))

        val restored = chatViewModel(RecordingChatRepository(listOf(otherMessage("message-5"))), dispatcher, store)
        testScheduler.advanceUntilIdle()
        assertEquals(replacement, restored.uiState.value.restoredViewport)
        restored.close()
    }

    @Test
    fun latestViewportAndDestructiveConversationActionsUseTheSameActorCustody() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        for (event in listOf(ChatUiEvent.LeaveConversation, ChatUiEvent.DeleteConversation)) {
            val preferences = ComposerMemoryPreferences()
            val store = ChatComposerDraftStore(preferences)
            val lease = store.open("me")
            store.writeViewport(lease, "conversation-1", ChatConversationViewport.Latest)
            assertEquals(ChatConversationViewport.Latest, store.readViewport(lease, "conversation-1"))

            val model = chatViewModel(RecordingChatRepository(emptyList()), dispatcher, store)
            testScheduler.advanceUntilIdle()
            model.onEvent(event)
            testScheduler.advanceUntilIdle()

            assertNull(store.readViewport(lease, "conversation-1"))
            model.close()
        }
    }

    @Test
    fun viewportCorruptionAndActorRetirementFailClosed() = runTest {
        val preferences = ComposerMemoryPreferences()
        val store = ChatComposerDraftStore(preferences)
        val lease = store.open("me")
        val key = ChatComposerDraftStore.viewportKey("me", 0L, "conversation-1")
        preferences.putString(key, "{\"generation\":0,\"kind\":\"anchored\",\"messageId\":\"\",\"scrollOffsetDp\":-1}")

        assertNull(store.readViewport(lease, "conversation-1"))
        assertNull(preferences.getString(key))

        store.writeViewport(lease, "conversation-1", ChatConversationViewport.Latest)
        store.clearActor("me")
        assertNull(store.readViewport(lease, "conversation-1"))
        assertNull(preferences.getString(key))
    }

    @Test
    fun composerInputWinsOverAConcurrentDelayedRestore() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val preferences = BlockingComposerPreferences(
            mapOf(
                ChatComposerDraftStore.conversationKey("me", 0L, "conversation-1") to
                    "{\"generation\":0,\"text\":\"stored\"}",
            ),
        )
        val model = chatViewModel(
            RecordingChatRepository(emptyList()),
            dispatcher,
            ChatComposerDraftStore(preferences),
        )

        testScheduler.runCurrent()
        model.onEvent(ChatUiEvent.MessageChanged("new input"))
        preferences.releaseReads.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertEquals("new input", model.uiState.value.messageText)
        assertEquals("new input", ChatComposerDraftStore(preferences).read("me", "conversation-1"))
        model.close()
    }

    @Test
    fun composerStoreIsolatesActorsAndConversationsAndFailsClosedOnCorruption() = runTest {
        val preferences = ComposerMemoryPreferences()
        val store = ChatComposerDraftStore(preferences)
        store.write("actor-a", "conversation-1", "one")
        store.write("actor-a", "conversation-2", "two")
        store.write("actor-b", "conversation-1", "other")

        assertEquals("one", store.read("actor-a", "conversation-1"))
        assertEquals("two", store.read("actor-a", "conversation-2"))
        assertEquals("other", store.read("actor-b", "conversation-1"))
        store.clearActor("actor-a")
        assertNull(store.read("actor-a", "conversation-1"))
        assertEquals("other", store.read("actor-b", "conversation-1"))

        val corruptKey = ChatComposerDraftStore.conversationKey("actor-c", 0L, "conversation-1")
        preferences.putString(corruptKey, "not-json")
        assertNull(store.read("actor-c", "conversation-1"))
        assertNull(preferences.getString(corruptKey))

        val invalidContextKey = ChatComposerDraftStore.conversationKey("actor-c", 0L, "conversation-2")
        preferences.putString(
            invalidContextKey,
            "{\"generation\":0,\"text\":\"draft\",\"mode\":\"edit\"}",
        )
        assertNull(store.readRecord("actor-c", "conversation-2"))
        assertNull(preferences.getString(invalidContextKey))
    }

    @Test
    fun separateStoreInstancesCannotOverwriteOtherConversationDrafts() = runTest {
        val preferences = BlockingTwoDraftWritesPreferences()
        val first = ChatComposerDraftStore(preferences)
        val second = ChatComposerDraftStore(preferences)

        val writes = listOf(
            async { first.write("me", "conversation-1", "one") },
            async { second.write("me", "conversation-2", "two") },
        )
        preferences.bothWritesStarted.await()
        preferences.releaseWrites.complete(Unit)
        writes.forEach { it.await() }

        assertEquals("one", first.read("me", "conversation-1"))
        assertEquals("two", second.read("me", "conversation-2"))
    }

    @Test
    fun actorRetirementInvalidatesAWriteThatFinishesAfterLogout() = runTest {
        val preferences = BlockingDraftWritePreferences()
        val store = ChatComposerDraftStore(preferences)
        val lease = store.open("me")
        val staleWrite = async { store.write(lease, "conversation-1", "private") }
        preferences.writeStarted.await()

        store.clearActor("me")
        preferences.releaseDraftWrites.complete(Unit)
        staleWrite.await()

        assertNull(store.read("me", "conversation-1"))
        assertNull(preferences.getString(ChatComposerDraftStore.conversationKey("me", 0L, "conversation-1")))
    }

    @Test
    fun staleWriteCannotOverwriteTheNextSessionDraft() = runTest {
        val preferences = SessionRacePreferences()
        val oldStore = ChatComposerDraftStore(preferences)
        val oldLease = oldStore.open("me")
        val staleWrite = async { oldStore.write(oldLease, "conversation-1", "old session") }
        preferences.oldWriteStarted.await()

        oldStore.clearActor("me")
        val newStore = ChatComposerDraftStore(preferences)
        newStore.write("me", "conversation-1", "new session")
        preferences.releaseOldWrite.complete(Unit)
        staleWrite.await()

        assertEquals("new session", newStore.read("me", "conversation-1"))
        assertNull(preferences.getString(ChatComposerDraftStore.conversationKey("me", 0L, "conversation-1")))
    }

    @Test
    fun delayedAuthenticatedActorStillEnablesDraftPersistence() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val preferences = ComposerMemoryPreferences()
        val store = ChatComposerDraftStore(preferences)
        val base = RecordingChatRepository(emptyList())
        val repository = object : ChatRepository by base {
            override fun currentUser(): User? = null
            override suspend fun currentActorId(): String = "me"
        }
        val model = chatViewModel(repository, dispatcher, store)

        model.onEvent(ChatUiEvent.MessageChanged("late actor"))
        testScheduler.advanceUntilIdle()

        assertEquals("late actor", store.read("me", "conversation-1"))
        model.close()
    }

    @Test
    fun initialReadFailurePublishesOnlyLocalizedReadMessage() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        for (language in listOf("es", "fr", "en")) {
            val repository = object : ChatRepository by RecordingChatRepository(emptyList()) {
                override fun observeMessages(conversationId: String): Flow<List<Message>> = kotlinx.coroutines.flow.flow {
                    throw IllegalStateException("web_postgrest_rlsdenied:postgrest_rpc_http_403")
                }
            }
            val model = ChatViewModel("conversation-1", repository,
                text = { chatTextForLanguage(it, language) },
                dispatchers = AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher))
            try {
                testScheduler.advanceUntilIdle()
                assertEquals(chatTextForLanguage(ChatText.LoadMessages, language), model.uiState.value.messageLoadFailure)
                assertNull(model.uiState.value.error)
                assertFalse(model.uiState.value.isLoading)
                assertFalse(model.uiState.value.hasReceivedMessageSnapshot)
            } finally { model.close() }
        }
    }

    @Test
    fun historyReadFailurePreservesIndependentSendError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val recording = RecordingChatRepository(emptyList()).apply {
            sendMessageResult = Result.failure(IllegalStateException("send transport failed"))
        }
        val repository = object : ChatRepository by recording {
            override suspend fun loadOlderMessages(conversationId: String, limit: Int): Result<Boolean> =
                Result.failure(IllegalStateException("web_postgrest_rlsdenied:postgrest_rpc_http_403"))
        }
        val model = chatViewModel(repository, dispatcher)
        try {
            testScheduler.advanceUntilIdle()
            model.onEvent(ChatUiEvent.MessageChanged("draft"))
            model.onEvent(ChatUiEvent.Send)
            testScheduler.advanceUntilIdle()
            assertEquals("send", model.uiState.value.error)
            assertTrue(model.loadOlderMessages())
            testScheduler.advanceUntilIdle()
            assertEquals("load-messages", model.uiState.value.messageLoadFailure)
            assertEquals("send", model.uiState.value.error)
            assertFalse(model.uiState.value.isLoadingOlderMessages)
        } finally { model.close() }
    }

    @Test
    fun historySuccessCannotHideAConcurrentTerminalObservationFailure() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val visibleMessage = otherMessage(id = "visible-message")
        val failObservation = CompletableDeferred<Unit>()
        val finishHistory = CompletableDeferred<Unit>()
        var subscriptions = 0
        val repository = object : ChatRepository by RecordingChatRepository(listOf(visibleMessage)) {
            override fun observeMessages(conversationId: String): Flow<List<Message>> = flow {
                subscriptions += 1
                emit(listOf(visibleMessage))
                if (subscriptions == 1) {
                    failObservation.await()
                    error("network_unavailable")
                }
                awaitCancellation()
            }

            override suspend fun loadOlderMessages(conversationId: String, limit: Int): Result<Boolean> {
                finishHistory.await()
                return Result.success(false)
            }
        }
        val model = ChatViewModel(
            "conversation-1",
            repository,
            text = { "load-messages" },
            dispatchers = AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
        )
        try {
            testScheduler.advanceUntilIdle()
            assertTrue(model.loadOlderMessages())
            failObservation.complete(Unit)
            testScheduler.runCurrent()
            assertEquals("load-messages", model.uiState.value.messageLoadFailure)

            finishHistory.complete(Unit)
            testScheduler.advanceUntilIdle()

            assertEquals("load-messages", model.uiState.value.messageLoadFailure)
            assertEquals(listOf("visible-message"), model.uiState.value.messages.map(Message::id))

            model.retryMessageLoading()
            testScheduler.runCurrent()
            assertEquals(2, subscriptions)
            assertNull(model.uiState.value.messageLoadFailure)
        } finally { model.close() }
    }

    @Test
    fun initialNetworkFailureRequiresExplicitRetryAndThenRecoversWithoutLosingTheDraft() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val recoveredMessage = otherMessage(id = "recovered-message")
        var subscriptions = 0
        val repository = object : ChatRepository by RecordingChatRepository(emptyList()) {
            override fun observeMessages(conversationId: String): Flow<List<Message>> = flow {
                subscriptions += 1
                if (subscriptions == 1) error("network_unavailable")
                emit(listOf(recoveredMessage))
            }
        }
        val model = ChatViewModel(
            "conversation-1",
            repository,
            text = { "load-messages" },
            dispatchers = AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
        )
        try {
            testScheduler.advanceUntilIdle()
            assertEquals(1, subscriptions)
            assertEquals("load-messages", model.uiState.value.messageLoadFailure)

            model.onEvent(ChatUiEvent.MessageChanged("draft survives retry"))
            testScheduler.advanceUntilIdle()
            assertEquals(1, subscriptions)

            model.retryMessageLoading()
            testScheduler.advanceUntilIdle()

            assertEquals(2, subscriptions)
            assertNull(model.uiState.value.messageLoadFailure)
            assertEquals("draft survives retry", model.uiState.value.messageText)
            assertEquals(listOf("recovered-message"), model.uiState.value.messages.map(Message::id))
        } finally { model.close() }
    }

    @Test
    fun successfulHistoryRetryClearsTheFailureWithoutDroppingLoadedMessages() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val visibleMessage = otherMessage(id = "visible-message")
        val base = RecordingChatRepository(listOf(visibleMessage))
        var historyAttempts = 0
        val repository = object : ChatRepository by base {
            override suspend fun loadOlderMessages(conversationId: String, limit: Int): Result<Boolean> {
                historyAttempts += 1
                return if (historyAttempts == 1) {
                    Result.failure(IllegalStateException("network_unavailable"))
                } else {
                    Result.success(false)
                }
            }
        }
        val model = ChatViewModel(
            "conversation-1",
            repository,
            text = { "load-messages" },
            dispatchers = AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
        )
        try {
            testScheduler.advanceUntilIdle()
            assertTrue(model.loadOlderMessages())
            testScheduler.advanceUntilIdle()
            assertEquals("load-messages", model.uiState.value.messageLoadFailure)
            assertEquals(listOf("visible-message"), model.uiState.value.messages.map(Message::id))

            model.retryMessageLoading()
            testScheduler.advanceUntilIdle()

            assertEquals(2, historyAttempts)
            assertNull(model.uiState.value.messageLoadFailure)
            assertFalse(model.uiState.value.hasMoreHistory)
            assertEquals(listOf("visible-message"), model.uiState.value.messages.map(Message::id))
        } finally { model.close() }
    }

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
    fun failedAttachmentSendRestoresTextButClearsPreparedAttachment() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = RecordingChatRepository(
            messages = listOf(otherMessage(id = "message-1")),
        ).apply {
            sendMessageResult = Result.failure(IllegalStateException("register failed"))
        }
        val model = chatViewModel(repository, dispatcher)

        testScheduler.advanceUntilIdle()

        model.onEvent(ChatUiEvent.MessageChanged("keep this text"))
        model.onEvent(ChatUiEvent.AttachmentSelected("file://document.txt", "document.txt", "text/plain"))
        model.onEvent(ChatUiEvent.Send)
        testScheduler.advanceUntilIdle()

        assertEquals("keep this text", model.uiState.value.messageText)
        assertNull(model.uiState.value.attachmentUri)
        assertNull(model.uiState.value.attachmentName)
        assertNull(model.uiState.value.attachmentMimeType)
        assertNotNull(model.uiState.value.error)
        assertFalse(model.uiState.value.messages.any { it.isLocalEcho })

        model.close()
    }

    @Test
    fun durableAttachmentRestoresAcrossModelsAndIsRemovedAfterSend() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val preferences = ComposerMemoryPreferences()
        val files = ComposerMemoryFiles()
        val store = ChatComposerDraftStore(preferences, files)
        val repository = RecordingChatRepository(emptyList())
        val first = chatViewModel(repository, dispatcher, store)

        first.onEvent(ChatUiEvent.MessageChanged("with attachment"))
        first.onEvent(ChatUiEvent.AttachmentSelected("content://temporary/photo", "photo.jpg", "image/jpeg"))
        testScheduler.advanceUntilIdle()
        val stagedReference = first.uiState.value.attachmentUri
        val stagedKey = assertNotNull(first.uiState.value.attachmentCacheKey)
        assertEquals("cache://$stagedKey", stagedReference)
        assertTrue(files.contains(stagedKey))
        first.close()

        val restored = chatViewModel(repository, dispatcher, store)
        testScheduler.advanceUntilIdle()
        assertEquals("with attachment", restored.uiState.value.messageText)
        assertEquals(stagedKey, restored.uiState.value.attachmentCacheKey)
        assertEquals(stagedReference, restored.uiState.value.attachmentUri)

        restored.onEvent(ChatUiEvent.Send)
        testScheduler.advanceUntilIdle()
        assertEquals(stagedReference, repository.sendMessageCalls.single().attachmentUri)
        assertNull(store.readRecord("me", "conversation-1"))
        assertFalse(files.contains(stagedKey))
        restored.close()
    }

    @Test
    fun attachmentOnlyDraftRestoresAcrossModels() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val files = ComposerMemoryFiles()
        val store = ChatComposerDraftStore(ComposerMemoryPreferences(), files)
        val repository = RecordingChatRepository(emptyList())
        val first = chatViewModel(repository, dispatcher, store)

        first.onEvent(ChatUiEvent.AttachmentSelected("content://temporary/only", "only.jpg", "image/jpeg"))
        testScheduler.advanceUntilIdle()
        val stagedKey = assertNotNull(first.uiState.value.attachmentCacheKey)
        first.close()

        val restored = chatViewModel(repository, dispatcher, store)
        testScheduler.advanceUntilIdle()
        assertEquals("", restored.uiState.value.messageText)
        assertEquals(stagedKey, restored.uiState.value.attachmentCacheKey)
        assertEquals("only.jpg", restored.uiState.value.attachmentName)
        assertTrue(files.contains(stagedKey))
        restored.close()
    }

    @Test
    fun failedSendRestoresTheDurableAttachmentAndKeepsItForRetry() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val files = ComposerMemoryFiles()
        val store = ChatComposerDraftStore(ComposerMemoryPreferences(), files)
        val repository = RecordingChatRepository(emptyList()).apply {
            sendMessageResult = Result.failure(IllegalStateException("offline"))
        }
        val model = chatViewModel(repository, dispatcher, store)

        model.onEvent(ChatUiEvent.MessageChanged("retry with bytes"))
        model.onEvent(ChatUiEvent.AttachmentSelected("content://temporary/retry", "retry.txt", "text/plain"))
        testScheduler.advanceUntilIdle()
        val stagedKey = assertNotNull(model.uiState.value.attachmentCacheKey)
        val stagedReference = assertNotNull(model.uiState.value.attachmentUri)

        model.onEvent(ChatUiEvent.Send)
        testScheduler.advanceUntilIdle()
        assertEquals("retry with bytes", model.uiState.value.messageText)
        assertEquals(stagedKey, model.uiState.value.attachmentCacheKey)
        assertEquals(stagedReference, model.uiState.value.attachmentUri)
        assertTrue(files.contains(stagedKey))
        assertEquals(stagedKey, store.readRecord("me", "conversation-1")?.attachment?.cacheKey)

        repository.sendMessageResult = Result.success(Unit)
        model.onEvent(ChatUiEvent.Send)
        testScheduler.advanceUntilIdle()
        assertEquals(2, repository.sendMessageCalls.size)
        assertEquals(stagedReference, repository.sendMessageCalls.last().attachmentUri)
        assertFalse(files.contains(stagedKey))
        model.close()
    }

    @Test
    fun sendInFlightKeepsItsAttachmentWhileAReplacementDraftIsPersisted() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val files = ComposerMemoryFiles()
        val store = ChatComposerDraftStore(ComposerMemoryPreferences(), files)
        val repository = RecordingChatRepository(emptyList())
        val sendGate = CompletableDeferred<Unit>()
        repository.sendMessageGate = sendGate
        val model = chatViewModel(repository, dispatcher, store)

        model.onEvent(ChatUiEvent.MessageChanged("first"))
        model.onEvent(ChatUiEvent.AttachmentSelected("content://temporary/first", "first.txt", "text/plain"))
        testScheduler.advanceUntilIdle()
        val sentKey = assertNotNull(model.uiState.value.attachmentCacheKey)

        model.onEvent(ChatUiEvent.Send)
        testScheduler.runCurrent()
        assertTrue(files.contains(sentKey))

        model.onEvent(ChatUiEvent.MessageChanged("new draft"))
        model.onEvent(ChatUiEvent.AttachmentSelected("content://temporary/second", "second.txt", "text/plain"))
        testScheduler.runCurrent()
        val replacementKey = assertNotNull(model.uiState.value.attachmentCacheKey)
        assertTrue(files.contains(sentKey))
        assertTrue(files.contains(replacementKey))

        sendGate.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertEquals("new draft", model.uiState.value.messageText)
        assertEquals(replacementKey, model.uiState.value.attachmentCacheKey)
        assertEquals(replacementKey, store.readRecord("me", "conversation-1")?.attachment?.cacheKey)
        assertFalse(files.contains(sentKey))
        assertTrue(files.contains(replacementKey))
        model.close()
    }

    @Test
    fun closeWaitsForTheDraftWriteCreatedByAttachmentStaging() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val files = ComposerMemoryFiles(storeGate = CompletableDeferred())
        val store = ChatComposerDraftStore(ComposerMemoryPreferences(), files)
        val model = chatViewModel(RecordingChatRepository(emptyList()), dispatcher, store)

        model.onEvent(ChatUiEvent.MessageChanged("survives close"))
        model.onEvent(ChatUiEvent.AttachmentSelected("content://temporary/late", "late.txt", "text/plain"))
        testScheduler.runCurrent()
        files.storeStarted.await()
        model.close()

        files.storeGate?.complete(Unit)
        testScheduler.advanceUntilIdle()

        val restored = assertNotNull(store.readRecord("me", "conversation-1"))
        assertEquals("survives close", restored.text)
        assertEquals("late.txt", restored.attachment?.name)
    }

    @Test
    fun failedAttachmentCleanupRemainsDurableAndRetries() = runTest {
        val preferences = ComposerMemoryPreferences()
        val files = ComposerMemoryFiles(removeFailuresRemaining = 1)
        val store = ChatComposerDraftStore(preferences, files)
        val lease = store.open("me")
        val first = assertNotNull(
            (store.stageAttachment(lease, "first", PlatformFile("content://first")) as? PlatformResult.Success)?.value,
        )
        val second = assertNotNull(
            (store.stageAttachment(lease, "second", PlatformFile("content://second")) as? PlatformResult.Success)?.value,
        )
        store.writeRecord(lease, "conversation-1", ChatComposerDraftRecord("one", attachment = first))

        store.writeRecord(lease, "conversation-1", ChatComposerDraftRecord("two", attachment = second))
        assertTrue(files.contains(first.cacheKey))

        assertEquals(second.cacheKey, store.readRecord(lease, "conversation-1")?.attachment?.cacheKey)
        assertFalse(files.contains(first.cacheKey))
        assertTrue(files.contains(second.cacheKey))
    }

    @Test
    fun suspendedCleanupCannotOverwriteANewerDraftRecord() = runTest {
        val removeGate = CompletableDeferred<Unit>()
        val files = ComposerMemoryFiles(removeGate = removeGate)
        val preferences = ComposerMemoryPreferences()
        val store = ChatComposerDraftStore(preferences, files)
        val concurrentStore = ChatComposerDraftStore(preferences, files)
        val lease = store.open("me")
        val first = assertNotNull(
            (store.stageAttachment(lease, "first", PlatformFile("content://first")) as? PlatformResult.Success)?.value,
        )
        val second = assertNotNull(
            (store.stageAttachment(lease, "second", PlatformFile("content://second")) as? PlatformResult.Success)?.value,
        )
        val latest = assertNotNull(
            (store.stageAttachment(lease, "latest", PlatformFile("content://latest")) as? PlatformResult.Success)?.value,
        )
        store.writeRecord(lease, "conversation-1", ChatComposerDraftRecord("old", attachment = first))
        store.retainAttachment(first.cacheKey)
        store.writeRecord(lease, "conversation-1", ChatComposerDraftRecord("new", attachment = second))

        val cleanup = async { store.releaseAttachment(lease, "conversation-1", first.cacheKey) }
        testScheduler.runCurrent()
        files.removeStarted.await()
        val newerWrite = async {
            concurrentStore.writeRecord("me", "conversation-1", ChatComposerDraftRecord("latest", attachment = latest))
        }
        testScheduler.runCurrent()

        removeGate.complete(Unit)
        cleanup.await()
        newerWrite.await()
        val persisted = assertNotNull(concurrentStore.readRecord("me", "conversation-1"))
        assertEquals("latest", persisted.text)
        assertEquals(latest.cacheKey, persisted.attachment?.cacheKey)
    }

    @Test
    fun separateStoreCannotDeleteAnAttachmentRetainedByAnInFlightSend() = runTest {
        val preferences = ComposerMemoryPreferences()
        val files = ComposerMemoryFiles()
        val sendingStore = ChatComposerDraftStore(preferences, files)
        val replacementStore = ChatComposerDraftStore(preferences, files)
        val sendingLease = sendingStore.open("me")
        val sent = assertNotNull(
            (sendingStore.stageAttachment(sendingLease, "sent", PlatformFile("content://sent")) as? PlatformResult.Success)?.value,
        )
        sendingStore.writeRecord(sendingLease, "conversation-1", ChatComposerDraftRecord("sent", attachment = sent))
        sendingStore.retainAttachment(sent.cacheKey)
        val replacement = assertNotNull(
            (replacementStore.stageAttachment(
                replacementStore.open("me"),
                "replacement",
                PlatformFile("content://replacement"),
            ) as? PlatformResult.Success)?.value,
        )

        replacementStore.writeRecord(
            "me",
            "conversation-1",
            ChatComposerDraftRecord("replacement", attachment = replacement),
        )
        assertTrue(files.contains(sent.cacheKey))

        sendingStore.releaseAttachment(sendingLease, "conversation-1", sent.cacheKey)
        assertFalse(files.contains(sent.cacheKey))
        assertTrue(files.contains(replacement.cacheKey))
    }

    @Test
    fun separateStoreCleanupWaitsForTheInFlightAttachmentEffect() = runTest {
        val preferences = ComposerMemoryPreferences()
        val files = ComposerMemoryFiles()
        val sendingStore = ChatComposerDraftStore(preferences, files)
        val replacementStore = ChatComposerDraftStore(preferences, files)
        val lease = sendingStore.open("me")
        val sent = assertNotNull(
            (sendingStore.stageAttachment(lease, "sent-effect", PlatformFile("content://sent")) as? PlatformResult.Success)?.value,
        )
        val replacement = assertNotNull(
            (replacementStore.stageAttachment(
                replacementStore.open("me"),
                "replacement-effect",
                PlatformFile("content://replacement"),
            ) as? PlatformResult.Success)?.value,
        )
        sendingStore.writeRecord(lease, "conversation-1", ChatComposerDraftRecord("sent", attachment = sent))
        val effectStarted = CompletableDeferred<Unit>()
        val finishEffect = CompletableDeferred<Unit>()
        val effect = async {
            sendingStore.withAttachmentCustody(lease) {
                effectStarted.complete(Unit)
                finishEffect.await()
            }
        }
        effectStarted.await()

        val replacementWrite = async {
            replacementStore.writeRecord(
                "me",
                "conversation-1",
                ChatComposerDraftRecord("replacement", attachment = replacement),
            )
        }
        testScheduler.runCurrent()
        assertFalse(replacementWrite.isCompleted)
        assertTrue(files.contains(sent.cacheKey))

        finishEffect.complete(Unit)
        effect.await()
        replacementWrite.await()
        assertFalse(files.contains(sent.cacheKey))
        assertTrue(files.contains(replacement.cacheKey))
    }

    @Test
    fun failedActorAttachmentCleanupRetriesAfterRetirement() = runTest {
        val files = ComposerMemoryFiles(prefixRemoveFailuresRemaining = 1)
        val store = ChatComposerDraftStore(ComposerMemoryPreferences(), files)
        val lease = store.open("me")
        val staged = assertNotNull(
            (store.stageAttachment(lease, "private", PlatformFile("content://private")) as? PlatformResult.Success)?.value,
        )
        store.writeRecord(lease, "conversation-1", ChatComposerDraftRecord("private", attachment = staged))

        store.clearActor("me")
        assertTrue(files.contains(staged.cacheKey))

        store.open("me")
        assertFalse(files.contains(staged.cacheKey))
    }

    @Test
    fun suspendedRetiredCleanupPreservesANewerIosRetirement() = runTest {
        val releasePrefixCleanup = CompletableDeferred<Unit>()
        val preferences = ComposerMemoryPreferences(
            mapOf(
                ChatComposerDraftStore.retirementKey("me") to "1",
                ChatComposerDraftStore.retiredCleanupKey("me") to "[0]",
            ),
        )
        val files = ComposerMemoryFiles(
            prefixRemoveGate = releasePrefixCleanup,
            prefixRemoveFailuresRemaining = 1,
        )
        val generationZeroKey = "${ChatComposerDraftStore.attachmentGenerationPrefix("me", 0)}old"
        val generationOneKey = "${ChatComposerDraftStore.attachmentGenerationPrefix("me", 1)}new"
        files.store(generationZeroKey, PlatformFile("content://old"))
        files.store(generationOneKey, PlatformFile("content://new"))
        val store = ChatComposerDraftStore(preferences, files)

        val oldReconciliation = async { store.open("me") }
        files.prefixRemoveStarted.await()
        assertEquals("0", preferences.getString(ChatComposerDraftStore.retiredCleanupCursorKey("me")))

        // Mirrors Swift writing after a stale absence observation made before common migrated v1.
        preferences.putString(ChatComposerDraftStore.retiredCleanupCursorKey("me"), "0")
        preferences.putString(ChatComposerDraftStore.retirementKey("me"), "2")

        releasePrefixCleanup.complete(Unit)
        oldReconciliation.await()
        assertTrue(files.contains(generationZeroKey))
        assertTrue(files.contains(generationOneKey))
        assertEquals("0", preferences.getString(ChatComposerDraftStore.retiredCleanupCursorKey("me")))

        store.open("me")
        assertFalse(files.contains(generationOneKey))
        assertEquals("2", preferences.getString(ChatComposerDraftStore.retiredCleanupCursorKey("me")))
    }

    @Test
    fun replacingAndClearingAttachmentDeletesOnlyTheSupersededCachedBytes() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val files = ComposerMemoryFiles()
        val store = ChatComposerDraftStore(ComposerMemoryPreferences(), files)
        val model = chatViewModel(RecordingChatRepository(emptyList()), dispatcher, store)

        model.onEvent(ChatUiEvent.MessageChanged("keep text"))
        model.onEvent(ChatUiEvent.AttachmentSelected("content://temporary/one", "one.txt", "text/plain"))
        testScheduler.advanceUntilIdle()
        val firstKey = assertNotNull(model.uiState.value.attachmentCacheKey)

        model.onEvent(ChatUiEvent.AttachmentSelected("content://temporary/two", "two.txt", "text/plain"))
        testScheduler.advanceUntilIdle()
        val secondKey = assertNotNull(model.uiState.value.attachmentCacheKey)
        assertFalse(files.contains(firstKey))
        assertTrue(files.contains(secondKey))

        model.onEvent(ChatUiEvent.ClearAttachment)
        testScheduler.advanceUntilIdle()
        assertEquals("keep text", model.uiState.value.messageText)
        assertNull(model.uiState.value.attachmentCacheKey)
        assertNull(model.uiState.value.attachmentUri)
        assertFalse(files.contains(secondKey))
        assertEquals("keep text", store.read("me", "conversation-1"))
        model.close()
    }

    @Test
    fun attachmentStagingFailureNeverPersistsTheTemporaryReference() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val files = ComposerMemoryFiles(storeFailure = "copy failed")
        val store = ChatComposerDraftStore(ComposerMemoryPreferences(), files)
        val model = chatViewModel(RecordingChatRepository(emptyList()), dispatcher, store)

        model.onEvent(ChatUiEvent.MessageChanged("safe text"))
        model.onEvent(ChatUiEvent.AttachmentSelected("content://temporary/private", "private.txt", "text/plain"))
        testScheduler.advanceUntilIdle()

        assertEquals("preserve-attachment", model.uiState.value.error)
        assertNull(model.uiState.value.attachmentCacheKey)
        assertNull(model.uiState.value.attachmentUri)
        val stored = assertNotNull(store.readRecord("me", "conversation-1"))
        assertEquals("safe text", stored.text)
        assertNull(stored.attachment)
        model.close()
    }

    @Test
    fun actorRetirementDeletesItsAttachmentGenerationAndRejectsTheOldLease() = runTest {
        val preferences = ComposerMemoryPreferences()
        val files = ComposerMemoryFiles()
        val store = ChatComposerDraftStore(preferences, files)
        val oldLease = store.open("me")
        val staged = assertNotNull(
            (store.stageAttachment(oldLease, "old", PlatformFile("content://old", "old.txt", "text/plain"))
                as? PlatformResult.Success)?.value,
        )
        store.writeRecord(oldLease, "conversation-1", ChatComposerDraftRecord("private", attachment = staged))
        assertTrue(files.contains(staged.cacheKey))

        store.clearActor("me")
        assertFalse(files.contains(staged.cacheKey))
        assertNull(store.readRecord("me", "conversation-1"))
        assertTrue(store.stageAttachment(oldLease, "stale", PlatformFile("content://stale")) is PlatformResult.Failure)
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
        assertEquals(own.id, model.uiState.value.selectedMessageId)
        assertEquals(own.text, model.uiState.value.messages.single { it.id == own.id }.text)

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
    fun failedMuteRestoresTheExactConversationAndSurfacesTheCommonError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = RecordingChatRepository(emptyList()).apply {
            setConversationMutedResult = Result.failure(IllegalStateException("mute failed"))
        }
        val before = Conversation(
            id = "conversation-1",
            title = "Chat",
            lastMessagePreview = "Preview",
            participantIds = listOf("me", "peer"),
            participantNames = listOf("Me", "Peer"),
        )
        val model = chatViewModel(repository, dispatcher)
        testScheduler.advanceUntilIdle()

        model.onEvent(ChatUiEvent.ConversationMutedChanged(true))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(MuteCall("conversation-1", true)), repository.setConversationMutedCalls)
        assertEquals(before, model.uiState.value.conversation)
        assertFalse(model.uiState.value.conversation!!.isMuted)
        assertFalse(model.uiState.value.isConversationActionInProgress)
        assertEquals("update", model.uiState.value.error)

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
        model.onEvent(ChatUiEvent.StartReply)
        model.onEvent(ChatUiEvent.ToggleFavoriteSelected)
        model.onEvent(ChatUiEvent.DeleteSelectedMessage)
        model.onEvent(ChatUiEvent.ReportSelectedMessage)
        model.onEvent(ChatUiEvent.OpenForwardDialog)
        testScheduler.advanceUntilIdle()

        assertNull(model.uiState.value.editingMessage)
        assertNull(model.uiState.value.replyToMessage)
        assertFalse(model.uiState.value.isForwardDialogOpen)
        assertTrue(repository.toggleFavoriteMessageCalls.isEmpty())
        assertTrue(repository.deleteMessageCalls.isEmpty())
        assertTrue(repository.reportMessageCalls.isEmpty())
        assertTrue(repository.forwardMessageCalls.isEmpty())

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
        assertEquals("before", model.uiState.value.messages.single { it.id == own.id }.text)

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

    @Test
    fun failedGroupParticipantActionsPreserveConversationAndAllowCleanRetry() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = RecordingChatRepository(emptyList()).apply {
            promoteModeratorResult = Result.failure(IllegalStateException("promote rejected"))
            demoteModeratorResult = Result.failure(IllegalStateException("demote rejected"))
            removeParticipantResult = Result.failure(IllegalStateException("remove rejected"))
            blockParticipantResult = Result.failure(IllegalStateException("block rejected"))
        }
        val model = chatViewModel(repository, dispatcher)
        testScheduler.advanceUntilIdle()
        val before = model.uiState.value.conversation

        val cases = listOf(
            ChatUiEvent.PromoteModerator("peer") to "promote-participant",
            ChatUiEvent.DemoteModerator("peer") to "demote-participant",
            ChatUiEvent.RemoveParticipant("peer") to "remove-participant",
            ChatUiEvent.BlockParticipant("peer") to "block-participant",
        )
        cases.forEach { (event, expectedError) ->
            model.onEvent(event)
            testScheduler.advanceUntilIdle()
            assertEquals(before, model.uiState.value.conversation)
            assertEquals(expectedError, model.uiState.value.error)
            assertFalse(model.uiState.value.isConversationActionInProgress)
        }

        repository.promoteModeratorResult = Result.success(Unit)
        model.onEvent(ChatUiEvent.PromoteModerator("peer"))
        testScheduler.advanceUntilIdle()
        assertNull(model.uiState.value.error)
        assertFalse(model.uiState.value.isConversationActionInProgress)
        assertEquals(before, model.uiState.value.conversation)
        assertEquals(2, repository.promoteModeratorCalls.size)

        model.close()
    }
}

private fun chatViewModel(
    repository: ChatRepository,
    dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    composerDraftStore: ChatComposerDraftStore? = null,
) = ChatViewModel(
    conversationId = "conversation-1",
    repository = repository,
    text = { it.camelToKebab() },
    composerDraftStore = composerDraftStore,
    dispatchers = AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
)

private open class ComposerMemoryPreferences(
    initial: Map<String, String> = emptyMap(),
) : PrefixClearablePreferenceStore {
    protected val values = initial.toMutableMap()
    override suspend fun getString(key: String): String? = values[key]
    override suspend fun putString(key: String, value: String) { values[key] = value }
    override suspend fun remove(key: String) { values.remove(key) }
    override suspend fun removeByPrefix(prefix: String) {
        values.keys.filter { it.startsWith(prefix) }.forEach(values::remove)
    }
}

private class ComposerMemoryFiles(
    private val storeFailure: String? = null,
    val storeGate: CompletableDeferred<Unit>? = null,
    val removeGate: CompletableDeferred<Unit>? = null,
    val prefixRemoveGate: CompletableDeferred<Unit>? = null,
    var removeFailuresRemaining: Int = 0,
    var prefixRemoveFailuresRemaining: Int = 0,
) : PrefixClearableFileCacheService {
    private val values = mutableMapOf<String, PlatformFile>()
    val storeStarted = CompletableDeferred<Unit>()
    val removeStarted = CompletableDeferred<Unit>()
    val prefixRemoveStarted = CompletableDeferred<Unit>()

    fun contains(key: String): Boolean = key in values

    override suspend fun store(cacheKey: String, file: PlatformFile): PlatformResult<PlatformFile> {
        storeFailure?.let { return PlatformResult.Failure(it) }
        storeStarted.complete(Unit)
        storeGate?.await()
        val cached = file.copy(reference = "cache://$cacheKey")
        values[cacheKey] = cached
        return PlatformResult.Success(cached)
    }

    override suspend fun get(cacheKey: String): PlatformResult<PlatformFile> =
        values[cacheKey]?.let { PlatformResult.Success(it) } ?: PlatformResult.Failure("missing")

    override suspend fun remove(cacheKey: String): PlatformResult<Unit> {
        if (removeFailuresRemaining > 0) {
            removeFailuresRemaining -= 1
            return PlatformResult.Failure("remove failed")
        }
        removeStarted.complete(Unit)
        removeGate?.await()
        values.remove(cacheKey)
        return PlatformResult.Success(Unit)
    }

    override suspend fun removeByPrefix(prefix: String): PlatformResult<Unit> {
        prefixRemoveStarted.complete(Unit)
        prefixRemoveGate?.await()
        if (prefixRemoveFailuresRemaining > 0) {
            prefixRemoveFailuresRemaining -= 1
            return PlatformResult.Failure("prefix remove failed")
        }
        values.keys.filter { it.startsWith(prefix) }.forEach(values::remove)
        return PlatformResult.Success(Unit)
    }
}

private class BlockingComposerPreferences(initial: Map<String, String>) : ComposerMemoryPreferences(initial) {
    val releaseReads = CompletableDeferred<Unit>()
    override suspend fun getString(key: String): String? {
        releaseReads.await()
        return super.getString(key)
    }
}

private class BlockingDraftWritePreferences : ComposerMemoryPreferences() {
    val writeStarted = CompletableDeferred<Unit>()
    val releaseDraftWrites = CompletableDeferred<Unit>()

    override suspend fun putString(key: String, value: String) {
        if (key.startsWith(ChatComposerDraftStore.actorPrefix("me"))) {
            writeStarted.complete(Unit)
            releaseDraftWrites.await()
        }
        super.putString(key, value)
    }
}

private class BlockingTwoDraftWritesPreferences : ComposerMemoryPreferences() {
    val bothWritesStarted = CompletableDeferred<Unit>()
    val releaseWrites = CompletableDeferred<Unit>()
    private var started = 0

    override suspend fun putString(key: String, value: String) {
        if (key.startsWith(ChatComposerDraftStore.actorPrefix("me"))) {
            started += 1
            if (started == 2) bothWritesStarted.complete(Unit)
            releaseWrites.await()
        }
        super.putString(key, value)
    }
}

private class SessionRacePreferences : ComposerMemoryPreferences() {
    val oldWriteStarted = CompletableDeferred<Unit>()
    val releaseOldWrite = CompletableDeferred<Unit>()

    override suspend fun putString(key: String, value: String) {
        if (key.contains(".g0.")) {
            oldWriteStarted.complete(Unit)
            releaseOldWrite.await()
        }
        super.putString(key, value)
    }
}

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
private data class MuteCall(val conversationId: String, val muted: Boolean)

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
    val setConversationMutedCalls = mutableListOf<MuteCall>()

    var sendMessageResult: Result<Unit> = Result.success(Unit)
    var sendMessageGate: CompletableDeferred<Unit>? = null
    var sendReplyResult: Result<Unit> = Result.success(Unit)
    var editMessageResult: Result<Unit> = Result.success(Unit)
    var deleteMessageResult: Result<Unit> = Result.success(Unit)
    var reportMessageResult: Result<Unit> = Result.success(Unit)
    var toggleFavoriteMessageResult: Result<Unit> = Result.success(Unit)
    var forwardMessageResult: Result<ChatForwardResult>? = null
    var setConversationMutedResult: Result<Unit> = Result.success(Unit)
    var olderMessagesToLoad: List<Message> = emptyList()
    var loadOlderMessagesCalls: Int = 0
    var promoteModeratorResult: Result<Unit> = Result.success(Unit)
    var demoteModeratorResult: Result<Unit> = Result.success(Unit)
    var removeParticipantResult: Result<Unit> = Result.success(Unit)
    var blockParticipantResult: Result<Unit> = Result.success(Unit)
    val promoteModeratorCalls = mutableListOf<String>()
    val demoteModeratorCalls = mutableListOf<String>()
    val removeParticipantCalls = mutableListOf<String>()
    val blockParticipantCalls = mutableListOf<String>()
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
    override suspend fun loadOlderMessages(conversationId: String, limit: Int): Result<Boolean> {
        loadOlderMessagesCalls += 1
        if (olderMessagesToLoad.isNotEmpty()) {
            messages.value = (messages.value + olderMessagesToLoad).distinctBy(Message::id)
            olderMessagesToLoad = emptyList()
        }
        return Result.success(false)
    }
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
        expectedActorId: String?,
    ): Result<Unit> {
        sendMessageCalls += SendMessageCall(
            conversationId = conversationId,
            text = text,
            attachmentUri = attachmentUri,
            attachmentName = attachmentName,
            attachmentMimeType = attachmentMimeType,
            hasClientMessageId = !clientMessageId.isNullOrBlank(),
        )
        sendMessageGate?.await()
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
    override suspend fun sendSosMessage(contactIds: List<String>, text: String, lat: Double?, lng: Double?, accuracy: Double?, expectedActorId: String?): Result<String> = Result.success("sos")
    override suspend fun cachedPrivateConversationId(userId: String): String? = null
    override suspend fun cachedCommunityConversationId(communityName: String): String? = null
    override suspend fun openCommunityConversation(communityId: String, title: String, participantIds: List<String>): Result<String> = Result.success("community")
    override suspend fun openGroupConversation(participantIds: List<String>, title: String?): Result<String> = Result.success("group")
    override suspend fun markConversationRead(conversationId: String): Result<Unit> = Result.success(Unit)
    override suspend fun setConversationMuted(conversationId: String, muted: Boolean): Result<Unit> {
        setConversationMutedCalls += MuteCall(conversationId, muted)
        return setConversationMutedResult
    }
    override suspend fun setMemberInvitesEnabled(conversationId: String, enabled: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun addParticipants(conversationId: String, participantIds: List<String>): Result<Unit> = Result.success(Unit)
    override suspend fun promoteModerator(conversationId: String, userId: String): Result<Unit> {
        promoteModeratorCalls += userId
        return promoteModeratorResult
    }
    override suspend fun demoteModerator(conversationId: String, userId: String): Result<Unit> {
        demoteModeratorCalls += userId
        return demoteModeratorResult
    }
    override suspend fun removeParticipant(conversationId: String, userId: String): Result<Unit> {
        removeParticipantCalls += userId
        return removeParticipantResult
    }
    override suspend fun blockParticipant(conversationId: String, userId: String): Result<Unit> {
        blockParticipantCalls += userId
        return blockParticipantResult
    }
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
