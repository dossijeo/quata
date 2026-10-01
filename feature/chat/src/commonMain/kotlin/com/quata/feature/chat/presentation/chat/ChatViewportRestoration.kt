package com.quata.feature.chat.presentation.chat

import com.quata.core.model.Message

data class ChatViewportRestoreDecision(
    val initialViewport: ChatConversationViewport? = null,
    val isInitialViewportReady: Boolean,
    val shouldLoadOlderMessages: Boolean = false,
    val shouldDiscardStoredViewport: Boolean = false,
    val shouldConsumeStoredViewport: Boolean = false,
    val shouldPreserveStoredViewportUntilUserScroll: Boolean = false,
)

fun resolveChatViewportRestore(
    storedViewport: ChatConversationViewport?,
    storeReadComplete: Boolean,
    focusedMessageId: String?,
    hasReceivedMessageSnapshot: Boolean,
    messages: List<Message>,
    hasMoreHistory: Boolean,
    messageLoadFailure: String?,
): ChatViewportRestoreDecision {
    if (focusedMessageId != null) {
        return ChatViewportRestoreDecision(
            isInitialViewportReady = true,
            shouldConsumeStoredViewport = storeReadComplete && storedViewport != null,
        )
    }
    if (!storeReadComplete) return ChatViewportRestoreDecision(isInitialViewportReady = false)
    when (storedViewport) {
        null -> return ChatViewportRestoreDecision(isInitialViewportReady = true)
        ChatConversationViewport.Latest -> return ChatViewportRestoreDecision(
            initialViewport = storedViewport,
            isInitialViewportReady = true,
        )
        is ChatConversationViewport.Anchored -> {
            if (messages.any { it.id == storedViewport.messageId && !it.isLocalEcho }) {
                return ChatViewportRestoreDecision(
                    initialViewport = storedViewport,
                    isInitialViewportReady = true,
                )
            }
        }
    }
    if (!hasReceivedMessageSnapshot) return ChatViewportRestoreDecision(isInitialViewportReady = false)
    if (messageLoadFailure != null) {
        return ChatViewportRestoreDecision(
            isInitialViewportReady = true,
            shouldConsumeStoredViewport = true,
            shouldPreserveStoredViewportUntilUserScroll = true,
        )
    }
    if (!hasMoreHistory) {
        return ChatViewportRestoreDecision(
            isInitialViewportReady = true,
            shouldDiscardStoredViewport = true,
        )
    }
    return ChatViewportRestoreDecision(
        isInitialViewportReady = false,
        shouldLoadOlderMessages = true,
    )
}
