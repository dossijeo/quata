package com.quata.feature.chat.presentation.chat

import com.quata.core.model.Message

/** One route-scoped, consumable request to focus a message from a public Chat link. */
sealed interface ChatMessageDeepLinkRequest {
    data object NoTarget : ChatMessageDeepLinkRequest
    data class Pending(val messageId: String) : ChatMessageDeepLinkRequest
    data class LoadingOlder(val messageId: String) : ChatMessageDeepLinkRequest
    data class Focused(val messageId: String, val index: Int) : ChatMessageDeepLinkRequest
    data class Unavailable(val messageId: String) : ChatMessageDeepLinkRequest
    data class LoadFailed(val messageId: String, val error: String) : ChatMessageDeepLinkRequest
    data class Cancelled(val messageId: String) : ChatMessageDeepLinkRequest
}

fun chatMessageDeepLinkRequest(messageId: String?): ChatMessageDeepLinkRequest =
    messageId?.takeIf(String::isNotBlank)?.let(ChatMessageDeepLinkRequest::Pending)
        ?: ChatMessageDeepLinkRequest.NoTarget

/**
 * Resolves a pending target only from authenticated conversation data. A recent page that does
 * not contain the id is never treated as not-found: it requests another existing history page.
 * [Unavailable] is reached only after the repository reports that history is exhausted.
 */
fun resolveChatMessageDeepLinkRequest(
    request: ChatMessageDeepLinkRequest,
    hasReceivedMessageSnapshot: Boolean,
    messages: List<Message>,
    hasMoreHistory: Boolean,
    messageLoadFailure: String? = null,
): ChatMessageDeepLinkRequest = when (request) {
    is ChatMessageDeepLinkRequest.Pending -> when {
        messageLoadFailure != null -> ChatMessageDeepLinkRequest.LoadFailed(request.messageId, messageLoadFailure)
        !hasReceivedMessageSnapshot -> request
        else -> messages.indexOfFirst { it.id == request.messageId }.let { index ->
            when {
                index >= 0 -> ChatMessageDeepLinkRequest.Focused(request.messageId, index)
                hasMoreHistory -> ChatMessageDeepLinkRequest.LoadingOlder(request.messageId)
                else -> ChatMessageDeepLinkRequest.Unavailable(request.messageId)
            }
        }
    }
    else -> request
}

/** Allows the next authenticated history page to be examined after [LoadingOlder] completes. */
fun resumeChatMessageDeepLinkRequest(request: ChatMessageDeepLinkRequest): ChatMessageDeepLinkRequest =
    (request as? ChatMessageDeepLinkRequest.LoadingOlder)
        ?.let { ChatMessageDeepLinkRequest.Pending(it.messageId) }
        ?: request

/** Explicit user retry only; failures never resume from ordinary state updates or polling. */
fun retryChatMessageDeepLinkRequest(request: ChatMessageDeepLinkRequest): ChatMessageDeepLinkRequest =
    (request as? ChatMessageDeepLinkRequest.LoadFailed)
        ?.let { ChatMessageDeepLinkRequest.Pending(it.messageId) }
        ?: request

/** A manual selection wins over a still-pending deep-link request for this route instance. */
fun cancelChatMessageDeepLinkRequest(request: ChatMessageDeepLinkRequest): ChatMessageDeepLinkRequest = when (request) {
    is ChatMessageDeepLinkRequest.Pending -> ChatMessageDeepLinkRequest.Cancelled(request.messageId)
    is ChatMessageDeepLinkRequest.LoadingOlder -> ChatMessageDeepLinkRequest.Cancelled(request.messageId)
    is ChatMessageDeepLinkRequest.Focused -> ChatMessageDeepLinkRequest.Cancelled(request.messageId)
    is ChatMessageDeepLinkRequest.LoadFailed -> ChatMessageDeepLinkRequest.Cancelled(request.messageId)
    else -> request
}
