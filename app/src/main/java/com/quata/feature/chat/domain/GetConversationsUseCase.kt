package com.quata.feature.chat.domain

class GetConversationsUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke() = repository.getConversations()
}
