package com.quata.feature.chat.domain

/** Shared conversations fetch. */

class GetConversationsUseCase(private val repository: ChatRepository) {
    suspend operator fun invoke() = repository.getConversations()
}
