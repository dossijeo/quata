package com.quata.feature.chat.data

import com.quata.core.preferences.SessionStorage
import com.quata.core.model.AuthSession
import com.quata.core.session.IosAuthSessionRefresher
import com.quata.core.session.IosRenewableAuthSession
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.presentation.chat.DocumentRetryEvidenceChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosNotificationReplyDurableRecoveryTest {
    @Test
    fun failedDirectSendRemainsActorBoundAndUsesTheSameIdempotencyKey() = runTest {
        val store = MemoryChatOutgoingStore()
        val repository = PendingRepository(store, online = false)
        val calls = mutableListOf<List<String>>()
        val runtime = runtime(store, repository, actor = "actor-a", scope = this) { conversation, actor, text, id ->
            calls += listOf(conversation, actor, text, id)
            NotificationReplyOutcome.Failed
        }

        val outcome = runtime.sendDurablyForTest("sb:7", "actor-a", " reply text ", ReplyId)

        assertEquals(NotificationReplyOutcome.Queued, outcome)
        assertEquals(listOf(listOf("sb:7", "actor-a", " reply text ", ReplyId)), calls)
        val durable = store.load("actor-a").single()
        assertEquals("sb:7", durable.conversationId)
        assertEquals("reply text", durable.text)
        assertEquals(ReplyId, durable.clientMessageId)
        assertEquals(null, durable.leaseToken)
        assertTrue(store.load("actor-b").isEmpty())
        runtime.sessionEnded()
    }

    @Test
    fun restoredSameActorFlushesOnceAndRemovesOnlyItsQueuedNotification() = runTest {
        val store = MemoryChatOutgoingStore()
        assertTrue(store.insert(pending("actor-a")))
        val repository = PendingRepository(store, online = true)
        val delivered = mutableListOf<String>()
        val runtime = runtime(store, repository, actor = "actor-a", scope = this) { _, _, _, _ ->
            error("restored replies must use the shared outbox")
        }
        runtime.setPendingReplyDeliveredHandler(delivered::add)

        runtime.recoverPendingRepliesForTest()
        advanceUntilIdle()

        assertEquals(1, repository.flushes)
        assertEquals(listOf(ReplyId), delivered)
        assertTrue(store.load("actor-a").isEmpty())
        runtime.sessionEnded()
    }

    @Test
    fun restoredDifferentActorCannotReplayOrDismissThePreviousActorsReply() = runTest {
        val store = MemoryChatOutgoingStore()
        assertTrue(store.insert(pending("actor-a")))
        val repository = PendingRepository(store, online = true)
        val delivered = mutableListOf<String>()
        val runtime = runtime(store, repository, actor = "actor-b", scope = this) { _, _, _, _ ->
            error("a different actor must never send the queued reply")
        }
        runtime.setPendingReplyDeliveredHandler(delivered::add)

        runtime.recoverPendingRepliesForTest()
        delay(100)

        assertEquals(0, repository.flushes)
        assertTrue(delivered.isEmpty())
        assertEquals(ReplyId, store.load("actor-a").single().clientMessageId)
        assertTrue(store.load("actor-b").isEmpty())
        runtime.sessionEnded()
    }

    @Test
    fun actorReplacementDuringDirectSendKeepsThePreviousActorsReplyRecoverable() = runTest {
        val store = MemoryChatOutgoingStore()
        val repository = PendingRepository(store, online = false)
        val delivered = mutableListOf<String>()
        var actor = "actor-a"
        val runtime = IosNotificationReplyRuntime(
            configuration = IosChatRuntimeConfiguration("https://example.invalid", "public-test-key"),
            authSession = IosRenewableAuthSession(IosAuthSessionRefresher { null }, EmptySessionStorage()),
            chatRepository = repository,
            outgoingStore = store,
            actorProvider = { actor },
            directReply = { _, _, _, _ ->
                actor = "actor-b"
                NotificationReplyOutcome.Rejected
            },
            scope = this,
        )
        runtime.setPendingReplyDeliveredHandler(delivered::add)

        val outcome = runtime.sendDurablyForTest("sb:7", "actor-a", "reply text", ReplyId)
        runCurrent()

        assertEquals(NotificationReplyOutcome.Queued, outcome)
        assertEquals(ReplyId, store.load("actor-a").single().clientMessageId)
        assertEquals(null, store.load("actor-a").single().leaseToken)
        assertEquals(0, repository.flushes)
        assertTrue(delivered.isEmpty())

        actor = "actor-a"
        repository.online = true
        runtime.recoverPendingRepliesForTest()
        advanceUntilIdle()

        assertTrue(store.load("actor-a").isEmpty())
        assertEquals(listOf(ReplyId), delivered)
        runtime.sessionEnded()
    }

    @Test
    fun watcherUsesThePersistentStoreWhenRepositoryCacheReportsMissing() = runTest {
        val store = MemoryChatOutgoingStore()
        val repository = PendingRepository(store, online = false)
        val delivered = mutableListOf<String>()
        val runtime = runtime(store, repository, actor = "actor-a", scope = this) { _, _, _, _ ->
            NotificationReplyOutcome.Failed
        }
        runtime.setPendingReplyDeliveredHandler(delivered::add)

        assertEquals(
            NotificationReplyOutcome.Queued,
            runtime.sendDurablyForTest("sb:7", "actor-a", "reply text", ReplyId),
        )
        runCurrent()

        assertEquals(0, repository.pendingChecks)
        assertTrue(delivered.isEmpty())
        assertEquals(ReplyId, store.load("actor-a").single().clientMessageId)
        runtime.sessionEnded()
    }

    @Test
    fun restoredReplyRetriesAfterAnInFlightCrashLeaseExpires() = runTest {
        val store = MemoryChatOutgoingStore()
        assertTrue(store.insert(pending("actor-a").copy(
            leaseToken = "crashed-process-lease",
            leaseUntilMillis = 30_000L,
        )))
        val repository = PendingRepository(store, online = true).also { it.claimNowMillis = 2L }
        val delivered = mutableListOf<String>()
        val runtime = runtime(store, repository, actor = "actor-a", scope = this) { _, _, _, _ ->
            error("restored replies must use the shared outbox")
        }
        runtime.setPendingReplyDeliveredHandler(delivered::add)

        runtime.recoverPendingRepliesForTest()
        runCurrent()

        assertEquals(1, repository.flushes)
        assertEquals(ReplyId, store.load("actor-a").single().clientMessageId)
        assertTrue(delivered.isEmpty())

        repository.claimNowMillis = 30_001L
        advanceUntilIdle()

        assertEquals(2, repository.flushes)
        assertTrue(store.load("actor-a").isEmpty())
        assertEquals(listOf(ReplyId), delivered)
        runtime.sessionEnded()
    }

    @Test
    fun successfulDirectSendLeavesNoReplayableOutboxEntry() = runTest {
        val store = MemoryChatOutgoingStore()
        val repository = PendingRepository(store, online = true)
        val runtime = runtime(store, repository, actor = "actor-a", scope = this) { _, _, _, _ ->
            NotificationReplyOutcome.Sent
        }

        val outcome = runtime.sendDurablyForTest("sb:7", "actor-a", "reply text", ReplyId)

        assertEquals(NotificationReplyOutcome.Sent, outcome)
        assertFalse(repository.isMessagePending(ReplyId))
        assertTrue(store.load("actor-a").isEmpty())
        runtime.sessionEnded()
    }

    private fun runtime(
        store: ChatOutgoingStore,
        repository: ChatRepository,
        actor: String,
        scope: CoroutineScope,
        directReply: suspend (String, String, String, String) -> NotificationReplyOutcome,
    ) = IosNotificationReplyRuntime(
        configuration = IosChatRuntimeConfiguration("https://example.invalid", "public-test-key"),
        authSession = IosRenewableAuthSession(IosAuthSessionRefresher { null }, EmptySessionStorage()),
        chatRepository = repository,
        outgoingStore = store,
        actorProvider = { actor },
        directReply = directReply,
        scope = scope,
    )

    private fun pending(actor: String) = StoredChatOutgoing(
        actorId = actor,
        conversationId = "sb:7",
        text = "reply text",
        clientMessageId = ReplyId,
        createdAtMillis = 1L,
    )

    private class PendingRepository(
        private val store: ChatOutgoingStore,
        var online: Boolean,
    ) : ChatRepository by DocumentRetryEvidenceChatRepository("file:///tmp/reply-test") {
        var flushes = 0
        var pendingChecks = 0
        var claimNowMillis = 2L

        override suspend fun flushPendingMessages(): Boolean {
            flushes += 1
            if (!online) return false
            store.load("actor-a").forEach { message ->
                val token = "test-recovery-lease"
                store.claim(message.actorId, message.clientMessageId, token, claimNowMillis, claimNowMillis + 1L)?.let {
                    store.removeClaimed(message.actorId, message.clientMessageId, token)
                }
            }
            return true
        }

        override suspend fun isMessagePending(clientMessageId: String): Boolean {
            pendingChecks += 1
            return false
        }
    }

    private class EmptySessionStorage : SessionStorage {
        override fun saveSession(session: AuthSession) = Unit
        override fun getSession(): AuthSession? = null
        override fun clear() = Unit
    }

    private companion object {
        const val ReplyId = "notification-reply-00000000-0000-0000-0000-000000000001"
    }
}
