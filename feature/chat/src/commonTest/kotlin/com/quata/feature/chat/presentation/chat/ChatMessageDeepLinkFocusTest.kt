package com.quata.feature.chat.presentation.chat

import com.quata.core.model.Message
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatMessageDeepLinkFocusTest {
    @Test
    fun oldTargetRequestsAuthenticatedHistoryInsteadOfBeingMarkedUnavailableFromRecentSnapshot() {
        val resolved = resolveChatMessageDeepLinkRequest(
            request = chatMessageDeepLinkRequest("old-message"),
            hasReceivedMessageSnapshot = true,
            messages = listOf(message("recent-message")),
            hasMoreHistory = true,
        )

        assertEquals(ChatMessageDeepLinkRequest.LoadingOlder("old-message"), resolved)
    }

    @Test
    fun lateHistoryPageFocusesTheExactTargetAndConsumesTheRequest() {
        val afterPage = resumeChatMessageDeepLinkRequest(ChatMessageDeepLinkRequest.LoadingOlder("old-message"))
        val focused = resolveChatMessageDeepLinkRequest(
            request = afterPage,
            hasReceivedMessageSnapshot = true,
            messages = listOf(message("old-message"), message("recent-message")),
            hasMoreHistory = true,
        )

        assertEquals(ChatMessageDeepLinkRequest.Focused("old-message", 0), focused)
        assertEquals(
            focused,
            resolveChatMessageDeepLinkRequest(focused, true, listOf(message("recent-message"), message("old-message")), true),
        )
    }

    @Test
    fun targetIsUnavailableOnlyAfterTheRepositoryReportsHistoryExhausted() {
        val request = resumeChatMessageDeepLinkRequest(ChatMessageDeepLinkRequest.LoadingOlder("missing-message"))
        assertEquals(
            ChatMessageDeepLinkRequest.Unavailable("missing-message"),
            resolveChatMessageDeepLinkRequest(request, true, listOf(message("recent-message")), hasMoreHistory = false),
        )
    }

    @Test
    fun unavailableTargetRecoversIfALaterSnapshotContainsTheMessage() {
        val unavailable = ChatMessageDeepLinkRequest.Unavailable("late-message")

        assertEquals(
            ChatMessageDeepLinkRequest.Focused("late-message", 1),
            resolveChatMessageDeepLinkRequest(
                request = unavailable,
                hasReceivedMessageSnapshot = true,
                messages = listOf(message("recent-message"), message("late-message")),
                hasMoreHistory = false,
            ),
        )
    }

    @Test
    fun historyFailureIsTerminalAndDoesNotAutoRetryFromLaterSnapshots() {
        val failed = resolveChatMessageDeepLinkRequest(
            request = ChatMessageDeepLinkRequest.Pending("old-message"),
            hasReceivedMessageSnapshot = true,
            messages = listOf(message("recent-message")),
            hasMoreHistory = true,
            messageLoadFailure = "offline",
        )

        assertEquals(ChatMessageDeepLinkRequest.LoadFailed("old-message", "offline"), failed)
        assertEquals(
            failed,
            resolveChatMessageDeepLinkRequest(failed, true, listOf(message("old-message")), true, null),
        )
    }

    @Test
    fun initialSnapshotFailurePausesRequestUntilExplicitRetry() {
        val failed = resolveChatMessageDeepLinkRequest(
            request = ChatMessageDeepLinkRequest.Pending("message-1"),
            hasReceivedMessageSnapshot = false,
            messages = emptyList(),
            hasMoreHistory = true,
            messageLoadFailure = "session expired",
        )

        assertEquals(ChatMessageDeepLinkRequest.LoadFailed("message-1", "session expired"), failed)
        assertEquals(ChatMessageDeepLinkRequest.Pending("message-1"), retryChatMessageDeepLinkRequest(failed))
    }

    @Test
    fun manualSelectionCancelsPendingRequestAndCancellationSurvivesLaterSnapshots() {
        val cancelled = cancelChatMessageDeepLinkRequest(ChatMessageDeepLinkRequest.LoadingOlder("old-message"))
        assertEquals(ChatMessageDeepLinkRequest.Cancelled("old-message"), cancelled)
        assertEquals(
            cancelled,
            resolveChatMessageDeepLinkRequest(cancelled, true, listOf(message("old-message")), hasMoreHistory = true),
        )
    }

    @Test
    fun manualSelectionCancelsFocusedRequestBeforeItsOneShotScrollIsConsumed() {
        assertEquals(
            ChatMessageDeepLinkRequest.Cancelled("message-1"),
            cancelChatMessageDeepLinkRequest(ChatMessageDeepLinkRequest.Focused("message-1", 0)),
        )
    }

    @Test
    fun noOrBlankTargetKeepsNormalConversationOpening() {
        assertEquals(ChatMessageDeepLinkRequest.NoTarget, chatMessageDeepLinkRequest(null))
        assertEquals(ChatMessageDeepLinkRequest.NoTarget, chatMessageDeepLinkRequest("   "))
        assertEquals(
            ChatMessageDeepLinkRequest.Pending("message-1"),
            resolveChatMessageDeepLinkRequest(chatMessageDeepLinkRequest("message-1"), false, emptyList(), true),
        )
    }

    private fun message(id: String) = Message(
        id = id,
        conversationId = "conversation-7",
        senderId = "profile-1",
        senderName = "Ada",
        text = "Message $id",
        sentAt = "now",
    )
}
