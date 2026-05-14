package com.quata.feature.chat.domain

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ChatRepository {
    val activeConversationId: StateFlow<String?>
    val pendingDeletedConversation: StateFlow<Conversation?>
    fun currentUser(): User?
    fun setActiveConversation(conversationId: String?)
    suspend fun getConversations(): Result<List<Conversation>>
    fun observeConversations(): Flow<List<Conversation>>
    fun observeMessages(conversationId: String): Flow<List<Message>>
    fun observeParticipantCandidates(): Flow<List<User>>
    suspend fun sendMessage(conversationId: String, text: String): Result<Unit>
    suspend fun sendReply(conversationId: String, text: String, replyTo: Message): Result<Unit>
    suspend fun sendSosMessage(contactIds: List<String>, text: String): Result<String>
    suspend fun markConversationRead(conversationId: String): Result<Unit>
    suspend fun setConversationMuted(conversationId: String, muted: Boolean): Result<Unit>
    suspend fun setMemberInvitesEnabled(conversationId: String, enabled: Boolean): Result<Unit>
    suspend fun addParticipants(conversationId: String, participantIds: List<String>): Result<Unit>
    suspend fun promoteModerator(conversationId: String, userId: String): Result<Unit>
    suspend fun removeParticipant(conversationId: String, userId: String): Result<Unit>
    suspend fun blockParticipant(conversationId: String, userId: String): Result<Unit>
    suspend fun leaveConversation(conversationId: String): Result<Unit>
    suspend fun hideConversation(conversationId: String): Result<Unit>
    suspend fun deleteConversation(conversationId: String): Result<Unit>
    suspend fun restorePendingDeletedConversation(): Result<Unit>
    suspend fun finalizePendingDeletedConversation(): Result<Unit>
    suspend fun editMessage(messageId: String, text: String): Result<Unit>
    suspend fun deleteMessage(messageId: String): Result<Unit>
    suspend fun toggleFavoriteMessage(messageId: String): Result<Unit>
    suspend fun forwardMessage(message: Message, conversationIds: List<String>): Result<Unit>
}
