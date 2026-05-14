package com.quata.feature.chat.data

import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.core.network.supabase.SupabaseConversationUpdateRequest
import com.quata.core.session.SessionManager
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.domain.SosRateLimitException
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
        val session = sessionManager.currentSession() ?: return null
        if (AppConfig.USE_MOCK_BACKEND) {
            return MockData.profileById(session.userId)?.toUser() ?: MockData.currentUser
        }
        return User(
            id = session.userId,
            email = "",
            displayName = session.displayName
        )
    }

    override suspend fun getConversations(): Result<List<Conversation>> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.conversations
        } else {
            remote.getConversations().map { it.toDomain() }.withLiveUnreadCounts()
        }
    }

    override fun observeConversations(): Flow<List<Conversation>> {
        return if (AppConfig.USE_MOCK_BACKEND) {
            MockData.conversationsFlow
        } else {
            flow {
                while (true) {
                    emit(remote.getConversations().map { it.toDomain() }.withLiveUnreadCounts())
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
            MockData.addMessage(conversationId, text, session.userId, session.displayName)
            scheduleMockReply(conversationId, session.userId, session.displayName)
            return@runCatching
        }
        remote.sendMessage(session.userId, session.displayName, conversationId, text)
    }

    override suspend fun sendSosMessage(contactIds: List<String>, text: String): Result<String> = runCatching {
        if (contactIds.size != 5) error("Configura cinco contactos de emergencia")
        if (text.isBlank()) error("El mensaje SOS no puede estar vacio")
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        if (AppConfig.USE_MOCK_BACKEND) {
            return@runCatching MockData.addSosConversation(contactIds, text, session.userId, session.displayName)
        }

        val participantIds = (contactIds + session.userId).distinct()
        val existingConversation = remote.getConversations()
            .map { it.toDomain() }
            .firstOrNull { conversation ->
                conversation.isEmergency && conversation.hasSameParticipants(participantIds)
            }
        if (existingConversation != null) {
            val lastMessage = remote.getMessages(existingConversation.id)
                .map { it.toDomain(session.userId) }
                .lastOrNull()
            remote.updateConversation(existingConversation.id, SupabaseConversationUpdateRequest(isVisible = true))
            if (lastMessage?.senderId == session.userId && lastMessage.text.isSosText()) {
                val sentAtMillis = lastMessage.sentAtMillis ?: 0L
                val elapsed = System.currentTimeMillis() - sentAtMillis
                if (elapsed in 0 until SosCooldownMillis) {
                    throw SosRateLimitException(SosCooldownMillis - elapsed)
                }
            }
            remote.sendMessage(session.userId, session.displayName, existingConversation.id, text)
            return@runCatching existingConversation.id
        }

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
        val currentUserId = sessionManager.currentSession()?.userId ?: error("No hay sesion activa")
        remote.markIncomingMessagesRead(conversationId, currentUserId)
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
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.addParticipants(conversationId, participantIds, session.userId, session.displayName)
            return@runCatching
        }
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

    private fun scheduleMockReply(conversationId: String, currentUserId: String, currentUserName: String) {
        mockReplyScope.launch {
            delay(Random.nextLong(2_000L, 7_000L))
            MockData.addIncomingMockMessage(
                conversationId = conversationId,
                text = MOCK_REPLIES.random(),
                currentUserId = currentUserId,
                currentUserName = currentUserName,
                incrementUnread = activeConversationId.value != conversationId
            )
        }
    }

    private suspend fun List<Conversation>.withLiveUnreadCounts(): List<Conversation> {
        val currentUserId = sessionManager.currentSession()?.userId.orEmpty()
        return map { conversation ->
            val unreadCount = remote.getMessages(conversation.id)
                .map { it.toDomain(currentUserId) }
                .count { !it.isMine && !it.isRead }
            conversation.copy(unreadCount = unreadCount)
        }
    }

    private fun Conversation.hasSameParticipants(participantIds: List<String>): Boolean =
        this.participantIds.size == participantIds.size && this.participantIds.toSet() == participantIds.toSet()

    private fun String.isSosText(): Boolean =
        contains("SOS", ignoreCase = true) || contains("https://maps.google.com/?q=")

    private companion object {
        const val SosCooldownMillis = 5L * 60L * 1000L

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
