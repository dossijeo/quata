package com.quata.feature.chat.presentation.chat

import com.quata.core.model.Message
import org.junit.Assert.assertFalse
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

    private fun message(id: String, isMine: Boolean = false) = Message(
        id = id,
        conversationId = "sb:7",
        senderId = "profile",
        senderName = "Usuario",
        text = "mensaje",
        sentAt = "2026-07-14T10:00:00Z",
        sentAtMillis = id.toLong(),
        isMine = isMine
    )
}
