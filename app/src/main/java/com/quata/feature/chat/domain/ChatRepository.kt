package com.quata.feature.chat.domain

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun currentUser(): User?
    suspend fun getConversations(): Result<List<Conversation>>
    fun observeConversations(): Flow<List<Conversation>>
    fun observeMessages(conversationId: String): Flow<List<Message>>
    fun observeParticipantCandidates(): Flow<List<User>>
    suspend fun sendMessage(conversationId: String, text: String): Result<Unit>
    suspend fun sendSosMessage(contactIds: List<String>, text: String): Result<String>
    suspend fun setConversationMuted(conversationId: String, muted: Boolean): Result<Unit>
    suspend fun addParticipants(conversationId: String, participantIds: List<String>): Result<Unit>
    suspend fun hideConversation(conversationId: String): Result<Unit>
}
