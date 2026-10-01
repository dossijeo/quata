package com.quata.feature.chat.presentation.chat

import com.quata.core.model.Message
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatAutoScrollPolicyTest {
    @Test
    fun attachedReaderFollowsIncomingMessages() {
        assertTrue(shouldFollowChatLayoutUpdate(layout(false), layout(false, false), false))
    }

    @Test
    fun detachedReaderDoesNotFollowIncomingMessages() {
        assertFalse(shouldFollowChatLayoutUpdate(layout(false), layout(false, false), true))
    }

    @Test
    fun detachedReaderFollowsTheirOwnNewMessage() {
        assertTrue(shouldFollowChatLayoutUpdate(layout(false), layout(false, true), true))
    }

    @Test
    fun detachedReaderDoesNotFollowPrependedHistory() {
        val previous = listOf(message(1, false).chatLayoutKey())
        val current = listOf(message(0, false).chatLayoutKey(), message(1, false).chatLayoutKey())
        assertFalse(shouldFollowChatLayoutUpdate(previous, current, true))
    }

    private fun layout(vararg own: Boolean): List<ChatMessageLayoutKey> =
        own.mapIndexed { index, isMine -> message(index + 1, isMine).chatLayoutKey() }

    private fun message(index: Int, isMine: Boolean) = Message(
        id = "message-$index",
        conversationId = "conversation-1",
        senderId = if (isMine) "me" else "peer",
        senderName = if (isMine) "Me" else "Peer",
        text = "Message $index",
        sentAt = "2026-10-01T00:00:00Z",
        isMine = isMine,
    )
}
