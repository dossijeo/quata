package com.quata.feature.chat.domain

class ObserveMessagesUseCase(private val repository: ChatRepository) {
    operator fun invoke(conversationId: String) = repository.observeMessages(conversationId)
}
