package com.quata.feature.chat.presentation.chat

import com.quata.core.model.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatViewportRestorationTest {
    @Test
    fun storeReadAndFocusedMessageGateInitialPositioning() {
        val waiting = resolveChatViewportRestore(
            storedViewport = null,
            storeReadComplete = false,
            focusedMessageId = null,
            hasReceivedMessageSnapshot = true,
            messages = listOf(message("recent")),
            hasMoreHistory = true,
            messageLoadFailure = null,
        )
        assertFalse(waiting.isInitialViewportReady)

        val stored = ChatConversationViewport.Anchored("older", 12f)
        val focused = resolveChatViewportRestore(
            storedViewport = stored,
            storeReadComplete = true,
            focusedMessageId = "deep-link",
            hasReceivedMessageSnapshot = true,
            messages = listOf(message("older")),
            hasMoreHistory = true,
            messageLoadFailure = null,
        )
        assertTrue(focused.isInitialViewportReady)
        assertTrue(focused.shouldConsumeStoredViewport)
        assertNull(focused.initialViewport)
    }

    @Test
    fun anchorPagesUntilAnAuthoritativeMessageIsPresent() {
        val stored = ChatConversationViewport.Anchored("older", 7.5f)
        val page = resolveChatViewportRestore(
            storedViewport = stored,
            storeReadComplete = true,
            focusedMessageId = null,
            hasReceivedMessageSnapshot = true,
            messages = listOf(message("recent")),
            hasMoreHistory = true,
            messageLoadFailure = null,
        )
        assertFalse(page.isInitialViewportReady)
        assertTrue(page.shouldLoadOlderMessages)

        val resolved = resolveChatViewportRestore(
            storedViewport = stored,
            storeReadComplete = true,
            focusedMessageId = null,
            hasReceivedMessageSnapshot = true,
            messages = listOf(message("older"), message("recent")),
            hasMoreHistory = true,
            messageLoadFailure = null,
        )
        assertTrue(resolved.isInitialViewportReady)
        assertEquals(stored, resolved.initialViewport)
        assertFalse(resolved.shouldLoadOlderMessages)
    }

    @Test
    fun staleAnchorIsDiscardedOnlyAfterHistoryIsAuthoritativelyExhausted() {
        val stored = ChatConversationViewport.Anchored("missing", 0f)
        val decision = resolveChatViewportRestore(
            storedViewport = stored,
            storeReadComplete = true,
            focusedMessageId = null,
            hasReceivedMessageSnapshot = true,
            messages = listOf(message("recent")),
            hasMoreHistory = false,
            messageLoadFailure = null,
        )
        assertTrue(decision.isInitialViewportReady)
        assertTrue(decision.shouldDiscardStoredViewport)
        assertFalse(decision.shouldConsumeStoredViewport)
        assertNull(decision.initialViewport)
    }

    @Test
    fun transientReadFailureFallsBackWithoutDeletingTheDurableAnchor() {
        val decision = resolveChatViewportRestore(
            storedViewport = ChatConversationViewport.Anchored("missing", 0f),
            storeReadComplete = true,
            focusedMessageId = null,
            hasReceivedMessageSnapshot = true,
            messages = listOf(message("recent")),
            hasMoreHistory = true,
            messageLoadFailure = "offline",
        )
        assertTrue(decision.isInitialViewportReady)
        assertTrue(decision.shouldConsumeStoredViewport)
        assertTrue(decision.shouldPreserveStoredViewportUntilUserScroll)
        assertFalse(decision.shouldDiscardStoredViewport)
        assertNull(decision.initialViewport)
    }

    @Test
    fun localEchoCannotSatisfyADurableAnchorAndLatestNeedsNoHistory() {
        val anchor = ChatConversationViewport.Anchored("local", 1f)
        val local = resolveChatViewportRestore(
            storedViewport = anchor,
            storeReadComplete = true,
            focusedMessageId = null,
            hasReceivedMessageSnapshot = true,
            messages = listOf(message("local").copy(isLocalEcho = true)),
            hasMoreHistory = true,
            messageLoadFailure = null,
        )
        assertTrue(local.shouldLoadOlderMessages)

        val latest = resolveChatViewportRestore(
            storedViewport = ChatConversationViewport.Latest,
            storeReadComplete = true,
            focusedMessageId = null,
            hasReceivedMessageSnapshot = false,
            messages = emptyList(),
            hasMoreHistory = true,
            messageLoadFailure = null,
        )
        assertTrue(latest.isInitialViewportReady)
        assertEquals(ChatConversationViewport.Latest, latest.initialViewport)
    }
}

private fun message(id: String) = Message(
    id = id,
    conversationId = "conversation-1",
    senderId = "peer",
    senderName = "Peer",
    text = id,
    sentAt = "2026-10-01T00:00:00Z",
)
