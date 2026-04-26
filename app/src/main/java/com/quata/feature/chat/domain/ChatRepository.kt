package com.quata.feature.chat.domain

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    suspend fun getConversations(): Result<List<Conversation>>
    fun observeMessages(conversationId: String): Flow<List<Message>>
    suspend fun sendMessage(conversationId: String, text: String): Result<Unit>
}
