package com.quata.feature.chat.domain

/** Shared messages observation. */

class ObserveMessagesUseCase(private val repository: ChatRepository) {
    operator fun invoke(conversationId: String) = repository.observeMessages(conversationId)
}
