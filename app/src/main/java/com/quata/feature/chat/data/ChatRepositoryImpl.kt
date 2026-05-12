package com.quata.feature.chat.data

import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.core.network.supabase.SupabaseConversationUpdateRequest
import com.quata.core.session.SessionManager
import com.quata.feature.chat.domain.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class ChatRepositoryImpl(
    private val remote: ChatRemoteDataSource,
    private val sessionManager: SessionManager
) : ChatRepository {
    private val mockReplyScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _activeConversationId = MutableStateFlow<String?>(null)
    override val activeConversationId: StateFlow<String?> = _activeConversationId

    override fun setActiveConversation(conversationId: String?) {
        _activeConversationId.value = conversationId
    }

    override fun currentUser(): User? {
        if (AppConfig.USE_MOCK_BACKEND) return MockData.currentUser
        val session = sessionManager.currentSession() ?: return null
        return User(
            id = session.userId,
            email = "",
            displayName = session.displayName
        )
    }

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

    override fun observeParticipantCandidates(): Flow<List<User>> {
        return if (AppConfig.USE_MOCK_BACKEND) {
            flowOf(MockData.registeredUsers)
        } else {
            flow {
                while (true) {
                    emit(
                        remote.getDirectoryProfiles().map { profile ->
                            User(
                                id = profile.id,
                                email = profile.email.orEmpty(),
                                displayName = profile.displayName?.takeIf { it.isNotBlank() } ?: profile.email.orEmpty(),
                                neighborhood = profile.neighborhood.orEmpty(),
                                avatarUrl = profile.avatarUrl
                            )
                        }
                    )
                    delay(30_000)
                }
            }
        }
    }

    override suspend fun sendMessage(conversationId: String, text: String): Result<Unit> = runCatching {
        if (text.isBlank()) return@runCatching
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.addMessage(conversationId, text, session.displayName)
            scheduleMockReply(conversationId)
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

    override suspend fun markConversationRead(conversationId: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.markConversationRead(conversationId)
            return@runCatching
        }
        remote.updateConversation(conversationId, SupabaseConversationUpdateRequest(unreadCount = 0))
    }

    override suspend fun setConversationMuted(conversationId: String, muted: Boolean): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.setConversationMuted(conversationId, muted)
            return@runCatching
        }
        remote.updateConversation(conversationId, SupabaseConversationUpdateRequest(isMuted = muted))
    }

    override suspend fun addParticipants(conversationId: String, participantIds: List<String>): Result<Unit> = runCatching {
        if (participantIds.isEmpty()) return@runCatching
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.addParticipants(conversationId, participantIds)
            return@runCatching
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val conversation = remote.getConversations().firstOrNull { it.id == conversationId }
            ?: error("Conversacion no encontrada")
        val currentIds = conversation.participantIds.orEmpty()
        val allIds = (currentIds + participantIds + session.userId).distinct()
        val namesById = remote.getDirectoryProfiles().associate { profile ->
            profile.id to (profile.displayName?.takeIf { it.isNotBlank() } ?: profile.email.orEmpty())
        } + (session.userId to session.displayName)
        remote.updateConversation(
            conversationId,
            SupabaseConversationUpdateRequest(
                participantIds = allIds,
                participantNames = allIds.mapNotNull { namesById[it] },
                isVisible = true
            )
        )
    }

    override suspend fun hideConversation(conversationId: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.hideConversation(conversationId)
            return@runCatching
        }
        remote.updateConversation(conversationId, SupabaseConversationUpdateRequest(isVisible = false))
    }

    private fun scheduleMockReply(conversationId: String) {
        mockReplyScope.launch {
            delay(Random.nextLong(2_000L, 7_000L))
            MockData.addIncomingMockMessage(
                conversationId = conversationId,
                text = MOCK_REPLIES.random(),
                incrementUnread = activeConversationId.value != conversationId
            )
        }
    }

    private companion object {
        val MOCK_REPLIES = listOf(
            "Perfecto, lo veo ahora.",
            "Dale, seguimos por aqui.",
            "Gracias por avisar.",
            "Estoy revisandolo y te digo.",
            "Me parece bien.",
            "Ahora mismo te contesto con mas detalle.",
            "Recibido.",
            "Vamos a ello."
        )
    }
}
