package com.quata.feature.chat.data

import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.session.SessionManager
import com.quata.feature.chat.domain.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay

class ChatRepositoryImpl(
    private val remote: ChatRemoteDataSource,
    private val sessionManager: SessionManager
) : ChatRepository {
    override suspend fun getConversations(): Result<List<Conversation>> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) MockData.conversations else remote.getConversations().map { it.toDomain() }
    }

    override fun observeConversations(): Flow<List<Conversation>> {
        return if (AppConfig.USE_MOCK_BACKEND) {
            MockData.conversationsFlow
        } else {
            flow {
                while (true) {
                    emit(remote.getConversations().map { it.toDomain() })
                    delay(5_000)
                }
            }
        }
    }

    override fun observeMessages(conversationId: String): Flow<List<Message>> = flow {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.messagesFlow.map { messages ->
                messages.filter { it.conversationId == conversationId }
            }.collect { emit(it) }
        } else {
            while (true) {
                val currentUserId = sessionManager.currentSession()?.userId.orEmpty()
                emit(remote.getMessages(conversationId).map { it.toDomain(currentUserId) })
                delay(5_000)
            }
        }
    }

    override suspend fun sendMessage(conversationId: String, text: String): Result<Unit> = runCatching {
        if (text.isBlank()) return@runCatching
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.addMessage(conversationId, text, session.displayName)
            return@runCatching
        }
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
            title = "\uD83D\uDEA8 SOS",
            participantIds = participantIds,
            lastMessagePreview = text
        ).firstOrNull() ?: error("No se pudo crear la conversacion SOS")
        remote.sendMessage(session.userId, session.displayName, conversation.id, text)
        conversation.id
    }
}
