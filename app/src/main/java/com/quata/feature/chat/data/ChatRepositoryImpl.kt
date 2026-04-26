package com.quata.feature.chat.data

import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.session.SessionManager
import com.quata.feature.chat.domain.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ChatRepositoryImpl(
    private val remote: ChatRemoteDataSource,
    private val sessionManager: SessionManager
) : ChatRepository {
    override suspend fun getConversations(): Result<List<Conversation>> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) MockData.conversations else remote.getConversations().map { it.toDomain() }
    }

    override fun observeMessages(conversationId: String): Flow<List<Message>> = flow {
        if (AppConfig.USE_MOCK_BACKEND) {
            emit(MockData.messages.filter { it.conversationId == conversationId }.ifEmpty { MockData.messages })
        } else {
            val currentUserId = sessionManager.currentSession()?.userId.orEmpty()
            emit(remote.getMessages(conversationId).map { it.toDomain(currentUserId) })
        }
    }

    override suspend fun sendMessage(conversationId: String, text: String): Result<Unit> = runCatching {
        if (text.isBlank()) return@runCatching
        if (AppConfig.USE_MOCK_BACKEND) return@runCatching
        val session = sessionManager.currentSession() ?: error("No hay sesión activa")
        remote.sendMessage(session.userId, session.displayName, conversationId, text)
    }
}
