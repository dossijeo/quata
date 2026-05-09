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
            emit(MockData.messages.filter { it.conversationId == conversationId })
        } else {
            val currentUserId = sessionManager.currentSession()?.userId.orEmpty()
            emit(remote.getMessages(conversationId).map { it.toDomain(currentUserId) })
        }
    }

    override suspend fun sendMessage(conversationId: String, text: String): Result<Unit> = runCatching {
        if (text.isBlank()) return@runCatching
        if (AppConfig.USE_MOCK_BACKEND) return@runCatching
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        remote.sendMessage(session.userId, session.displayName, conversationId, text)
    }

    override suspend fun sendSosMessage(contactIds: List<String>, text: String): Result<String> = runCatching {
        if (contactIds.size != 5) error("Configura cinco contactos de emergencia")
        if (text.isBlank()) error("El mensaje SOS no puede estar vacio")
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        if (AppConfig.USE_MOCK_BACKEND) {
            return@runCatching MockData.addSosConversation(contactIds, text, session.displayName)
        }

        val participantIds = (contactIds + session.userId).distinct()
        val conversation = remote.createConversation(
            title = "SOS emergencia",
            participantIds = participantIds,
            lastMessagePreview = text
        ).firstOrNull() ?: error("No se pudo crear la conversacion SOS")
        remote.sendMessage(session.userId, session.displayName, conversation.id, text)
        conversation.id
    }
}
