package com.quata.feature.chat.domain

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    suspend fun getConversations(): Result<List<Conversation>>
    fun observeConversations(): Flow<List<Conversation>>
    fun observeMessages(conversationId: String): Flow<List<Message>>
    suspend fun sendMessage(conversationId: String, text: String): Result<Unit>
    suspend fun sendSosMessage(contactIds: List<String>, text: String): Result<String>
}
