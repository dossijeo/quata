package com.quata.feature.chat.presentation.chat

import com.quata.core.model.Message
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAutoScrollPolicyTest {

    @Test
    fun followsInitialContentAndReplyLayoutEnrichmentAtBottom() {
        val plain = message("2").chatLayoutKey()
        val enriched = message("2").copy(replyToSenderName = "Ana", replyToText = "citado").chatLayoutKey()

        assertTrue(shouldFollowChatLayoutUpdate(emptyList(), listOf(plain), false))
        assertTrue(shouldFollowChatLayoutUpdate(listOf(plain), listOf(enriched), false))
    }

    @Test
    fun respectsUserReadingHistoryButAlwaysFollowsOwnNewMessage() {
        val current = listOf(message("2").chatLayoutKey())
        val incoming = current + message("3", isMine = false).chatLayoutKey()
        val outgoing = current + message("4", isMine = true).chatLayoutKey()

        assertFalse(shouldFollowChatLayoutUpdate(current, incoming, true))
        assertTrue(shouldFollowChatLayoutUpdate(current, outgoing, true))
    }

    @Test
    fun loadingOlderHistoryDoesNotReturnDetachedUserToBottom() {
        val current = listOf(message("2").chatLayoutKey(), message("3").chatLayoutKey())
        val withHistory = listOf(message("1").chatLayoutKey()) + current

        assertFalse(shouldFollowChatLayoutUpdate(current, withHistory, true))
    }

    @Test
    fun reconciliationKeepsVisualIdentityAndDoesNotRequestScroll() {
        val optimistic = message("local:temp-1234", isMine = true).copy(
            clientMessageId = "temp-1234",
            isLocalEcho = true,
            deliveryState = com.quata.core.model.MessageDeliveryState.Pending
        )
        val confirmed = optimistic.copy(
            id = "9876",
            isLocalEcho = false,
            deliveryState = com.quata.core.model.MessageDeliveryState.Sent
        )

        assertEquals("temp-1234", optimistic.composeKey())
        assertEquals(optimistic.composeKey(), confirmed.composeKey())
        assertEquals(optimistic.chatLayoutKey(), confirmed.chatLayoutKey())
        assertFalse(
            shouldFollowChatLayoutUpdate(
                listOf(optimistic.chatLayoutKey()),
                listOf(confirmed.chatLayoutKey()),
                userHasDetachedFromBottom = false
            )
        )
    }

    @Test
    fun oldServerMessagesFallBackToServerId() {
        assertEquals("42", message("42").composeKey())
    }

    @Test
    fun detachedReaderFollowsOnlyOwnMessageAppendedAfterPreviousTail() {
        val previous = listOf(message("2").chatLayoutKey(), message("3").chatLayoutKey())
        val prependedOwnHistory = listOf(message("1", isMine = true).chatLayoutKey()) + previous
        val appendedOwnMessage = previous + message("4", isMine = true).chatLayoutKey()

        assertFalse(shouldFollowChatLayoutUpdate(previous, prependedOwnHistory, true))
        assertTrue(shouldFollowChatLayoutUpdate(previous, appendedOwnMessage, true))
    }

    private fun message(id: String, isMine: Boolean = false) = Message(
        id = id,
        conversationId = "sb:7",
        senderId = "profile",
        senderName = "Usuario",
        text = "mensaje",
        sentAt = "2026-07-14T10:00:00Z",
        sentAtMillis = id.substringAfterLast(':').toLongOrNull(),
        isMine = isMine
    )
}
