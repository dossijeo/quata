package com.quata.feature.chat.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.MessageDeliveryState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SupabaseChatCacheStoreInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun cleanBefore() {
        context.deleteDatabase("quata_chat_cache.db")
    }

    @After
    fun cleanAfter() {
        context.deleteDatabase("quata_chat_cache.db")
    }

    @Test
    fun cacheIsIsolatedByProfileAndSurvivesStoreRecreation() = runBlocking {
        val firstStore = SupabaseChatCacheStore(context)
        firstStore.replaceConversations("profile-a", listOf(conversation("sb:1", "Cuenta A")))
        firstStore.replaceConversations("profile-b", listOf(conversation("sb:2", "Cuenta B")))
        firstStore.replaceMessages("profile-a", "sb:1", listOf(message("1", "sb:1")))

        val reopened = SupabaseChatCacheStore(context)
        assertEquals(listOf("sb:1"), reopened.cachedConversations("profile-a").map { it.id })
        assertEquals(listOf("sb:2"), reopened.cachedConversations("profile-b").map { it.id })
        assertEquals(listOf("1"), reopened.cachedMessages("profile-a", "sb:1").map { it.id })
        assertTrue(reopened.cachedMessages("profile-b", "sb:1").isEmpty())
    }

    @Test
    fun outboxPersistsAttemptsAndRemoteAttachmentId() = runBlocking {
        val store = SupabaseChatCacheStore(context)
        val outgoing = PendingOutgoingMessage(
            profileId = "profile-a",
            clientMessageId = "client-1",
            conversationId = "sb:7",
            text = "mensaje pendiente",
            attachmentUri = "file:///tmp/staged"
        )
        store.enqueueOutgoing(outgoing)
        store.markOutgoingUploaded("profile-a", "client-1", 42L)
        store.markOutgoingAttempt("profile-a", "client-1", "offline")

        val restored = SupabaseChatCacheStore(context).pendingOutgoing("profile-a").single()
        assertEquals(42L, restored.remoteFileId)
        assertEquals(1, restored.attempts)
        assertEquals("offline", restored.lastError)
        assertTrue(SupabaseChatCacheStore(context).pendingOutgoing("profile-b").isEmpty())
    }

    @Test
    fun replySnapshotSurvivesDatabaseRecreation() = runBlocking {
        val reply = message("20", "sb:7").copy(
            replyToMessageId = "1",
            replyToSenderName = "Ana",
            replyToText = "mensaje citado"
        )
        SupabaseChatCacheStore(context).replaceMessages("profile-a", "sb:7", listOf(reply))

        val restored = SupabaseChatCacheStore(context).cachedMessages("profile-a", "sb:7").single()

        assertEquals("1", restored.replyToMessageId)
        assertEquals("Ana", restored.replyToSenderName)
        assertEquals("mensaje citado", restored.replyToText)
    }

    private fun conversation(id: String, title: String) = Conversation(id = id, title = title, lastMessagePreview = title)

    private fun message(id: String, conversationId: String) = Message(
        id = id,
        conversationId = conversationId,
        senderId = "profile-a",
        senderName = "A",
        text = "hola",
        sentAt = "ahora",
        sentAtMillis = 1L,
        isMine = true,
        deliveryState = MessageDeliveryState.Sent
    )
}
