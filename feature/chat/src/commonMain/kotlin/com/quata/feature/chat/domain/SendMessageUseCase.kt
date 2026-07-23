package com.quata.feature.chat.domain

/** Shared send action. */

class SendMessageUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke(conversationId: String, text: String) = repository.sendMessage(conversationId, text)
}
