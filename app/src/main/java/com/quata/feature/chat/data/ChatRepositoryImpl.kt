package com.quata.feature.chat.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import com.quata.R
import com.quata.bettermessages.BetterMessagesHttpException
import com.quata.bettermessages.BetterMessagesRepository
import com.quata.bettermessages.BmMessage
import com.quata.bettermessages.BmThread
import com.quata.bettermessages.BmThreadResponse
import com.quata.bettermessages.BmUser
import com.quata.core.common.UserFacingException
import com.quata.core.common.mapFailureToUserFacing
import com.quata.core.common.serverMessageOrNull
import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.model.AuthSession
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.core.navigation.AppDestinations
import com.quata.core.session.SessionManager
import com.quata.data.supabase.CommunityProfile
import com.quata.data.supabase.CommunityWallStats
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.domain.SosRateLimitException
import com.quata.wordpress.QuataWordPressClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class ChatRepositoryImpl(
    private val appContext: Context,
    private val remote: ChatRemoteDataSource,
    private val betterMessagesRepository: BetterMessagesRepository,
    private val wordpressClient: QuataWordPressClient,
    private val sessionManager: SessionManager
) : ChatRepository {
    private val mockReplyScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val realConversationsScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val realConversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val betterMessagesReadStateStore = BetterMessagesReadStateStore(appContext)
    private val betterMessagesAbandonedConversationStore = BetterMessagesAbandonedConversationStore(appContext)
    private val betterMessagesConversationTypeStore = BetterMessagesConversationTypeStore(appContext)
    private val betterMessagesByThreadId = ConcurrentHashMap<Int, List<BmMessage>>()
    private var realConversationsPollJob: Job? = null
    private val _activeConversationId = MutableStateFlow<String?>(null)
    override val activeConversationId: StateFlow<String?> = _activeConversationId
    private val _pendingDeletedConversation = MutableStateFlow<Conversation?>(null)
    override val pendingDeletedConversation: StateFlow<Conversation?> = _pendingDeletedConversation
    private val threadIdsByPeerProfileId = mutableMapOf<String, Int>()
    private val betterMessagesWpUserIdsByProfileId = ConcurrentHashMap<String, Int>()
    private val betterMessagesProfileIdsByWpUserId = ConcurrentHashMap<Int, String>()
    private val betterMessagesGroupThreadIds = ConcurrentHashMap.newKeySet<Int>()
    private val ensuredWallMemberships = mutableSetOf<String>()
    private val betterMessagesSessionMutex = Mutex()
    private var betterMessagesSession: BetterMessagesSessionContext? = null
    private var realConversationsLoadedForProfileId: String? = null
    private var realConversationsLastRefreshedAt: Long = 0L

    override fun setActiveConversation(conversationId: String?) {
        _activeConversationId.value = conversationId
    }

    override fun currentUser(): User? {
        val session = sessionManager.currentSession() ?: return null
        if (AppConfig.USE_MOCK_BACKEND) {
            return MockData.profileById(session.userId)?.toUser() ?: MockData.currentUser
        }
        return User(id = session.userId, email = "", displayName = session.displayName)
    }

    override suspend fun getConversations(): Result<List<Conversation>> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.conversations
        } else {
            ensureRealConversationsPolling()
            realConversations.value.takeIf { it.isNotEmpty() } ?: loadRealConversations().also(::setRealConversations)
        }
    }.mapFailureToUserFacing(appContext, R.string.error_load_chats)

    override fun observeConversations(): Flow<List<Conversation>> {
        return if (AppConfig.USE_MOCK_BACKEND) {
            MockData.conversationsFlow
        } else {
            ensureRealConversationsPolling()
            flow {
                activeConversationId.value
                    ?.takeIf { activeId ->
                        activeId != AppDestinations.FavoriteMessagesConversationId &&
                            realConversations.value.none { it.id == activeId }
                    }
                    ?.let { activeId ->
                        runCatching { loadRealConversation(activeId) }
                            .getOrNull()
                            ?.let(::upsertRealConversation)
                    }
                emitAll(realConversations)
            }
        }
    }

    override fun observeMessages(conversationId: String): Flow<List<Message>> = flow {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.messagesFlow.map { messages ->
                if (conversationId == AppDestinations.FavoriteMessagesConversationId) {
                    val visibleConversationIds = MockData.conversations
                        .filter { conversation ->
                            conversation.isVisible && sessionManager.currentSession()?.userId !in conversation.blockedUserIds
                        }
                        .map { it.id }
                        .toSet()
                    messages.filter { it.isFavorite && it.conversationId in visibleConversationIds && !it.isDeleted }
                } else {
                    messages.filter { it.conversationId == conversationId }
                }
            }.collect { emit(it) }
        } else {
            conversationId.betterMessagesThreadIdOrNull()?.let { threadId ->
                emitAll(observeBetterMessagesMessages(threadId))
                return@flow
            }
            while (true) {
                runCatching { loadRealMessages(conversationId) }
                    .onSuccess { emit(it) }
                    .onFailure { emit(emptyList()) }
                delay(MESSAGES_POLL_DELAY_MILLIS)
            }
        }
    }

    override fun observeParticipantCandidates(): Flow<List<User>> {
        return if (AppConfig.USE_MOCK_BACKEND) {
            flowOf(MockData.registeredUsers)
        } else {
            flow {
                emit(runCatching { remote.getProfiles().map { it.toUser() } }.getOrDefault(emptyList()))
            }
        }
    }

    override suspend fun sendMessage(
        conversationId: String,
        text: String,
        attachmentUri: String?,
        attachmentName: String?,
        attachmentMimeType: String?
    ): Result<Unit> = runCatching {
        if (text.isBlank() && attachmentUri.isNullOrBlank()) return@runCatching
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.addMessage(
                conversationId = conversationId,
                text = text,
                senderId = session.userId,
                senderName = session.displayName,
                attachmentUri = attachmentUri,
                attachmentName = attachmentName,
                attachmentMimeType = attachmentMimeType
            )
            scheduleMockReply(conversationId, session.userId, session.displayName)
            return@runCatching
        }

        conversationId.betterMessagesThreadIdOrNull()?.let { threadId ->
            if (attachmentUri.isNullOrBlank()) {
                val response = betterMessagesRepository.sendText(session.userId, threadId, text)
                if (!response.result) error("Better Messages no pudo enviar el mensaje")
                cacheBetterMessagesUpdate(threadId, response.update)
            } else {
                val media = appContext.copyUriToCache(attachmentUri, attachmentName, attachmentMimeType)
                try {
                    val upload = betterMessagesRepository.uploadFile(session.userId, threadId, media.file, media.mimeType)
                    val fileId = upload.id ?: error(upload.error ?: "Better Messages no devolvio id de archivo")
                    val response = betterMessagesRepository.sendFiles(session.userId, threadId, listOf(fileId), text)
                    if (!response.result) error("Better Messages no pudo enviar el adjunto")
                    cacheBetterMessagesUpdate(threadId, response.update)
                } finally {
                    media.delete()
                }
            }
            return@runCatching
        }

        val wallId = conversationId.wallIdOrNull() ?: error("Conversacion no reconocida")
        if (!attachmentUri.isNullOrBlank() && attachmentMimeType?.startsWith("image/") == true) {
            val media = appContext.readUriBytes(attachmentUri, attachmentName, attachmentMimeType)
            val upload = remote.uploadCommunityChatImage(session.userId, media.bytes, media.extension, media.mimeType)
            val imageUrl = upload.publicUrl ?: error("Supabase no devolvio URL de imagen")
            remote.sendCommunityImageMessage(wallId, session.userId, imageUrl, media.fileName, media.mimeType, text)
        } else {
            remote.sendCommunityMessage(wallId, session.userId, text.ifBlank { attachmentUri.orEmpty() })
        }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun sendReply(conversationId: String, text: String, replyTo: Message): Result<Unit> = runCatching {
        if (text.isBlank()) return@runCatching
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.addMessage(conversationId, text, session.userId, session.displayName, replyTo = replyTo)
            scheduleMockReply(conversationId, session.userId, session.displayName)
            return@runCatching
        }
        val threadId = conversationId.betterMessagesThreadIdOrNull()
        val replyMessageId = replyTo.id.bmMessagePartsOrNull()?.second
        if (threadId != null && replyMessageId != null) {
            val response = betterMessagesRepository.sendReply(session.userId, threadId, text, replyMessageId)
            if (!response.result) error("Better Messages no pudo enviar la respuesta")
            cacheBetterMessagesUpdate(threadId, response.update)
        } else {
            sendMessage(conversationId, text).getOrThrow()
        }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun sendSosMessage(contactIds: List<String>, text: String): Result<String> = runCatching {
        if (contactIds.isEmpty()) error("Configura al menos un contacto de emergencia")
        if (text.isBlank()) error("El mensaje SOS no puede estar vacio")
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        if (AppConfig.USE_MOCK_BACKEND) {
            return@runCatching MockData.addSosConversation(contactIds, text, session.userId, session.displayName)
        }
        remote.sendSos(session.userId, text)
        runCatching { betterMessagesRepository.sendSos(session.userId, text) }
        "sos"
    }.mapFailureToUserFacing(appContext, R.string.sos_send_error)

    override suspend fun markConversationRead(conversationId: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.markConversationRead(conversationId)
            return@runCatching
        }
        val session = sessionManager.currentSession() ?: return@runCatching
        val threadId = conversationId.betterMessagesThreadIdOrNull() ?: return@runCatching
        betterMessagesReadStateStore.markThreadRead(
            profileId = session.userId,
            threadId = threadId,
            messages = betterMessagesByThreadId[threadId].orEmpty()
        )
        realConversations.value = realConversations.value.map { conversation ->
            if (conversation.id == conversationId) conversation.copy(unreadCount = 0) else conversation
        }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun setConversationMuted(conversationId: String, muted: Boolean): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.setConversationMuted(conversationId, muted)
            return@runCatching
        }
        val previousConversations = realConversations.value
        realConversations.value = previousConversations.updateMutedConversation(conversationId, muted)
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        try {
            conversationId.betterMessagesThreadIdOrNull()?.let {
                val changed = betterMessagesRepository.muteThread(session.userId, it, muted)
                if (!changed) error("Better Messages no pudo actualizar el silencio")
            }
        } catch (error: Throwable) {
            realConversations.value = previousConversations
            throw error
        }
        Unit
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun setMemberInvitesEnabled(conversationId: String, enabled: Boolean): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.setMemberInvitesEnabled(conversationId, enabled)
        } else {
            error("Permisos de invitacion no disponibles en Android todavia")
        }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun addParticipants(conversationId: String, participantIds: List<String>): Result<Unit> = runCatching {
        if (participantIds.isEmpty()) return@runCatching
        if (AppConfig.USE_MOCK_BACKEND) {
            val session = sessionManager.currentSession() ?: error("No hay sesion activa")
            MockData.addParticipants(conversationId, participantIds, session.userId, session.displayName)
        } else {
            val session = sessionManager.currentSession() ?: error("No hay sesion activa")
            val threadId = conversationId.betterMessagesThreadIdOrNull() ?: error("Conversacion no reconocida")
            ensureBetterMessagesSession(session.userId)
            val currentConversation = realConversations.value.firstOrNull { it.id == conversationId }
                ?: loadBetterMessagesConversation(session.userId, threadId)?.also(::upsertRealConversation)
            val selectedProfiles = runCatching { remote.getProfiles(participantIds) }
                .getOrDefault(emptyList())
                .associateBy { it.id }
            val existingIds = currentConversation?.participantIds.orEmpty().toSet()
            val existingWpIds = existingIds.mapNotNull { it.removePrefix("wp:").toIntOrNull() }.toSet()
            val existingNames = currentConversation?.participantNames.orEmpty()
                .map { it.normalizeName() }
                .toSet()
            val newParticipantIds = participantIds
                .distinct()
                .filterNot { profileId ->
                    profileId == session.userId ||
                        profileId in existingIds ||
                        (selectedProfiles[profileId]?.displayName()?.normalizeName()?.let { it in existingNames } == true)
                }
            if (newParticipantIds.isEmpty()) return@runCatching
            val wpUserIds = newParticipantIds
                .map { profileId -> profileId.toBetterMessagesUserId() }
                .filterNot { it in existingWpIds }
                .distinct()
            if (wpUserIds.isEmpty()) return@runCatching
            val added = try {
                betterMessagesRepository.addParticipant(session.userId, threadId, wpUserIds)
            } catch (error: BetterMessagesHttpException) {
                if (error.statusCode == 403) {
                    val serverMessage = error.serverMessageOrNull()?.takeIf { it.isNotBlank() }
                    throw UserFacingException(
                        serverMessage?.let { appContext.getString(R.string.error_conversation_add_participant_forbidden_details, it) }
                            ?: appContext.getString(R.string.error_conversation_add_participant_forbidden),
                        error
                    )
                }
                throw error
            }
            if (!added) throw UserFacingException(appContext.getString(R.string.error_conversation_add_participant_forbidden))
            markBetterMessagesGroupThread(session.userId, threadId)
            loadBetterMessagesConversation(session.userId, threadId)?.let(::upsertRealConversation)
        }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun promoteModerator(conversationId: String, userId: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.promoteModerator(conversationId, userId)
        } else {
            val session = sessionManager.currentSession() ?: error("No hay sesion activa")
            val threadId = conversationId.betterMessagesThreadIdOrNull() ?: error("Conversacion no reconocida")
            ensureBetterMessagesSession(session.userId)
            val wpUserId = userId.toBetterMessagesUserId()
            val promoted = betterMessagesRepository.makeModerator(session.userId, threadId, wpUserId)
            if (!promoted) error("No se pudo ascender a moderador")
            realConversations.value = realConversations.value.map { conversation ->
                if (conversation.id == conversationId) {
                    conversation.copy(moderatorIds = (conversation.moderatorIds + userId).distinct())
                } else {
                    conversation
                }
            }
            loadBetterMessagesConversation(session.userId, threadId)?.let(::upsertRealConversation)
        }
        Unit
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun removeParticipant(conversationId: String, userId: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.removeParticipant(conversationId, userId)
        } else {
            error("Expulsar participantes no disponible en backend real")
        }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun blockParticipant(conversationId: String, userId: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.blockParticipant(conversationId, userId)
        } else {
            error("Bloqueo de participantes no disponible en backend real")
        }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun leaveConversation(conversationId: String): Result<Unit> = runCatching {
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.leaveConversation(conversationId, session.userId)
            return@runCatching
        }
        conversationId.betterMessagesThreadIdOrNull()?.let {
            val left = betterMessagesRepository.leaveThread(session.userId, it)
            if (!left) error("No se pudo abandonar la conversacion")
            betterMessagesAbandonedConversationStore.markAbandoned(session.userId, it)
        }
        realConversations.value = realConversations.value.filterNot { it.id == conversationId }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun hideConversation(conversationId: String): Result<Unit> = deleteConversation(conversationId)

    override suspend fun deleteConversation(conversationId: String): Result<Unit> = runCatching {
        val conversation = getConversations().getOrNull()?.firstOrNull { it.id == conversationId }
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.hideConversation(conversationId)
            _pendingDeletedConversation.value = conversation?.copy(isVisible = false)
            return@runCatching
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        conversationId.betterMessagesThreadIdOrNull()?.let {
            betterMessagesRepository.deleteThread(session.userId, it)
            betterMessagesAbandonedConversationStore.markAbandoned(session.userId, it)
        }
        _pendingDeletedConversation.value = conversation?.copy(isVisible = false)
        realConversations.value = realConversations.value.filterNot { it.id == conversationId }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun restorePendingDeletedConversation(): Result<Unit> = runCatching {
        val conversation = _pendingDeletedConversation.value ?: return@runCatching
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.restoreConversation(conversation.id)
        } else {
            val session = sessionManager.currentSession() ?: error("No hay sesion activa")
            conversation.id.betterMessagesThreadIdOrNull()?.let {
                betterMessagesRepository.restoreThread(session.userId, it)
                betterMessagesAbandonedConversationStore.clearAbandoned(session.userId, it)
            }
        }
        _pendingDeletedConversation.value = null
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun finalizePendingDeletedConversation(): Result<Unit> = runCatching {
        val conversation = _pendingDeletedConversation.value ?: return@runCatching
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.deleteConversation(conversation.id)
        }
        _pendingDeletedConversation.value = null
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun editMessage(messageId: String, text: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.editMessage(messageId, text)
            return@runCatching
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val (threadId, bmMessageId) = messageId.bmMessagePartsOrNull() ?: error("Mensaje no reconocido")
        val response = betterMessagesRepository.saveMessage(
            profileId = session.userId,
            threadId = threadId,
            messageId = bmMessageId,
            message = text
        )
        response.messages
            .filter { it.threadId == threadId }
            .takeIf { it.isNotEmpty() }
            ?.let { betterMessagesByThreadId[threadId] = it }
        Unit
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun deleteMessage(messageId: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.deleteMessage(messageId)
            return@runCatching
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val (threadId, bmMessageId) = messageId.bmMessagePartsOrNull() ?: error("Mensaje no reconocido")
        betterMessagesRepository.deleteMessages(session.userId, threadId, listOf(bmMessageId))
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun toggleFavoriteMessage(messageId: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.toggleFavoriteMessage(messageId)
            return@runCatching
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val (threadId, bmMessageId) = messageId.bmMessagePartsOrNull() ?: error("Mensaje no reconocido")
        val current = loadBetterMessages(threadId).firstOrNull { it.id == messageId }
        betterMessagesRepository.favoriteMessage(session.userId, threadId, bmMessageId, favorite = current?.isFavorite != true)
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun forwardMessage(message: Message, conversationIds: List<String>): Result<Unit> = runCatching {
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.forwardMessage(message, conversationIds, session.userId, session.displayName)
            return@runCatching
        }
        val bmMessageId = message.id.bmMessagePartsOrNull()?.second
        val targetThreadIds = conversationIds.mapNotNull { it.betterMessagesThreadIdOrNull() }
        if (bmMessageId != null && targetThreadIds.isNotEmpty()) {
            betterMessagesRepository.forwardMessage(session.userId, bmMessageId, targetThreadIds)
        }
        conversationIds.filter { it.wallIdOrNull() != null }.forEach { targetConversationId ->
            sendMessage(targetConversationId, message.text).getOrThrow()
        }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    private suspend fun loadRealConversations(): List<Conversation> {
        val session = sessionManager.currentSession() ?: run {
            realConversationsLoadedForProfileId = null
            realConversationsLastRefreshedAt = 0L
            return emptyList()
        }
        val privateChats = remote.getPrivateChats(session.userId)
        val abandonedBetterMessagesThreadIds = betterMessagesAbandonedConversationStore.abandonedThreadIds(session.userId)
        val peerIds = privateChats.mapNotNull { chat ->
            listOfNotNull(chat.user_low_id, chat.user_high_id, chat.requester_profile_id, chat.target_profile_id)
                .distinct()
                .firstOrNull { it != session.userId }
        }.distinct()
        val profilesById = if (peerIds.isEmpty()) emptyMap() else remote.getProfiles(peerIds).associateBy { it.id }
        val privateConversations = privateChats.mapNotNull { chat ->
            runCatching {
                if (!chat.hasVisibleMessage()) return@runCatching null
                val peerId = listOfNotNull(chat.user_low_id, chat.user_high_id, chat.requester_profile_id, chat.target_profile_id)
                    .distinct()
                    .firstOrNull { it != session.userId } ?: return@runCatching null
                val peer = profilesById[peerId]
                val cachedThreadId = threadIdsByPeerProfileId[peerId]
                if (cachedThreadId != null && shouldSkipThreadInInboxRefresh(cachedThreadId)) return@runCatching null
                val threadId = cachedThreadId ?: resolvePrivateThreadId(session.userId, peerId) ?: return@runCatching null
                if (shouldSkipThreadInInboxRefresh(threadId)) return@runCatching null
                if (threadId in abandonedBetterMessagesThreadIds) return@runCatching null
                val threadResponse = runCatching { loadBetterMessagesThread(session.userId, threadId) }.getOrNull()
                val threadMessages = threadResponse?.messages.orEmpty()
                if (threadMessages.isEmpty()) return@runCatching null
                val thread = threadResponse?.threads?.firstOrNull { it.threadId == threadId } ?: BmThread(threadId = threadId)
                val context = ensureBetterMessagesSession(session.userId)
                val currentWpUserId = inferCurrentWpUserId(
                    profile = context.profile,
                    users = threadResponse?.users.orEmpty(),
                    fallbackUserId = context.currentWpUserId
                )
                val unreadCount = betterMessagesReadStateStore.unreadCount(
                    profileId = session.userId,
                    threadId = threadId,
                    messages = threadMessages,
                    currentWpUserId = currentWpUserId
                )
                val lastMessage = threadResponse?.messages?.maxByOrNull {
                    it.created_at.toEpochMillisFromBetterMessagesOrNull() ?: 0L
                }
                thread.toConversation(
                    title = peer?.displayName().orEmpty(),
                    peerProfileId = peerId,
                    peerName = peer?.displayName() ?: "Usuario",
                    peerAvatarUrl = peer?.avatar_url ?: peer?.avatar,
                    currentProfileId = session.userId
                ).copy(
                    lastMessagePreview = lastMessage?.message?.stripHtml() ?: chat.last_message_preview.orEmpty(),
                    unreadCount = unreadCount,
                    updatedAt = lastMessage?.created_at?.toDisplayTime() ?: chat.last_message_at.orEmpty(),
                    updatedAtMillis = lastMessage?.created_at?.toEpochMillisFromBetterMessagesOrNull()
                        ?: chat.last_message_at?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() },
                    isGroup = false
                )
            }.getOrNull()
        }
        val privateConversationIds = privateConversations.map { it.id }.toSet()
        val privateThreadIds = privateConversations.mapNotNull { it.id.betterMessagesThreadIdOrNull() }.toSet()
        val bmConversations = runCatching {
            loadBetterMessagesInbox(session.userId, privateThreadIds)
        }.getOrDefault(emptyList())
        val mergedPrivateConversations = privateConversations.map { privateConversation ->
            val threadId = privateConversation.id.betterMessagesThreadIdOrNull()
            bmConversations.firstOrNull { bmConversation ->
                bmConversation.id == privateConversation.id &&
                    bmConversation.isGroup &&
                    threadId != null &&
                    isKnownBetterMessagesGroupThread(session.userId, threadId)
            } ?: privateConversation
        }
        val bmOnlyConversations = bmConversations.filterNot { it.id in privateConversationIds }
        val activeWallId = activeConversationId.value?.wallIdOrNull()
        val wallConversations = if (activeWallId == null) {
            emptyList()
        } else {
            remote.getActiveWalls().filter { it.id == activeWallId }
        }.map { wall -> wall.toWallConversation(session) }
        return (mergedPrivateConversations + bmOnlyConversations + wallConversations)
            .distinctBy { it.id }
            .sortedByDescending { it.updatedAtMillis ?: 0L }
            .also {
                realConversationsLoadedForProfileId = session.userId
                realConversationsLastRefreshedAt = System.currentTimeMillis()
            }
    }

    private suspend fun loadRealConversation(conversationId: String): Conversation? {
        val session = sessionManager.currentSession() ?: return null
        conversationId.betterMessagesThreadIdOrNull()?.let { threadId ->
            if (betterMessagesAbandonedConversationStore.isAbandoned(session.userId, threadId)) return null
            return loadBetterMessagesConversation(session.userId, threadId)
        }
        conversationId.wallIdOrNull()?.let { wallId ->
            return remote.getActiveWalls()
                .firstOrNull { it.id == wallId }
                ?.let { wall -> wall.toWallConversation(session) }
        }
        return null
    }

    private suspend fun CommunityWallStats.toWallConversation(session: AuthSession): Conversation {
        ensureCurrentWallMember(id, session.userId)
        val followedProfileIds = runCatching {
            remote.getWallFollows(id).mapNotNull { it.profile_id }
        }.getOrDefault(emptyList())
        val wallNames = listOfNotNull(name, normalized_name, slug).map { it.normalizeName() }.toSet()
        val profiles = runCatching { remote.getProfiles() }.getOrDefault(emptyList())
        val neighborhoodProfileIds = profiles
            .filter { profile -> profile.matchesWall(wallNames) }
            .map { it.id }
        val memberIds = (followedProfileIds + neighborhoodProfileIds + session.userId).distinct()
        val profilesById = profiles
            .filter { it.id in memberIds }
            .associateBy { it.id }
        val participantNames = memberIds.map { profileId ->
            profilesById[profileId]?.displayName()
                ?: if (profileId == session.userId) session.displayName else profileId
        }
        return toConversation().copy(
            lastMessagePreview = "",
            updatedAt = chat_last_at.orEmpty(),
            updatedAtMillis = chat_last_at?.let { value ->
                runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull()
            },
            participantIds = memberIds,
            participantNames = participantNames,
            participantAvatarUrls = memberIds.map { profileId ->
                profilesById[profileId]?.avatar_url ?: profilesById[profileId]?.avatar
            },
            isGroup = true
        )
    }

    private suspend fun ensureCurrentWallMember(wallId: String, profileId: String) {
        val key = "$wallId:$profileId"
        if (key in ensuredWallMemberships) return
        runCatching { remote.ensureWallFollow(wallId, profileId) }
            .onSuccess { ensuredWallMemberships += key }
    }

    private suspend fun loadBetterMessagesConversation(profileId: String, threadId: Int): Conversation? {
        if (betterMessagesAbandonedConversationStore.isAbandoned(profileId, threadId)) return null
        val context = ensureBetterMessagesSession(profileId)
        val threadResponse = loadBetterMessagesThread(profileId, threadId)
        val thread = threadResponse.threads.firstOrNull { it.threadId == threadId } ?: BmThread(threadId = threadId)
        val usersByWpId = threadResponse.users.associateByWpId()
        val currentWpUserId = inferCurrentWpUserId(
            profile = context.profile,
            users = threadResponse.users,
            fallbackUserId = context.currentWpUserId
        )
        val unreadCount = betterMessagesReadStateStore.unreadCount(
            profileId = profileId,
            threadId = threadId,
            messages = threadResponse.messages,
            currentWpUserId = currentWpUserId
        )
        val profiles = (runCatching { remote.getProfiles() }.getOrDefault(emptyList()) + listOfNotNull(context.profile))
            .distinctBy { it.id }
        if (thread.type == "group") markBetterMessagesGroupThread(profileId, threadId)
        val isKnownGroup = isKnownBetterMessagesGroupThread(profileId, threadId)
        val isKnownPrivateThread = threadId in threadIdsByPeerProfileId.values && !isKnownGroup
        val resolvedParticipants = thread.resolveBetterMessagesParticipants(
            usersByWpId = usersByWpId,
            currentWpUserId = currentWpUserId,
            currentProfileId = profileId,
            profiles = profiles
        )
        return thread.toBetterMessagesConversation(
            usersByWpId = usersByWpId,
            currentWpUserId = currentWpUserId,
            messages = threadResponse.messages,
            resolvedParticipants = resolvedParticipants,
            forceGroup = isKnownGroup,
            isKnownPrivateThread = isKnownPrivateThread
        ).copy(
            unreadCount = unreadCount
        )
    }

    private fun upsertRealConversation(conversation: Conversation) {
        realConversations.value = (listOf(conversation) + realConversations.value.filterNot { it.id == conversation.id })
            .distinctBy { it.id }
            .sortedByDescending { it.updatedAtMillis ?: 0L }
    }

    private fun setRealConversations(conversations: List<Conversation>) {
        realConversations.value = conversations.withActiveConversationPreserved()
    }

    private fun List<Conversation>.withActiveConversationPreserved(): List<Conversation> {
        val activeId = activeConversationId.value
            ?.takeIf { it != AppDestinations.FavoriteMessagesConversationId }
            ?: return distinctBy { it.id }.sortedByDescending { it.updatedAtMillis ?: 0L }
        val activeConversation = realConversations.value.firstOrNull { it.id == activeId }
            ?.takeIf { active -> none { it.id == active.id } }
        return (this + listOfNotNull(activeConversation))
            .distinctBy { it.id }
            .sortedByDescending { it.updatedAtMillis ?: 0L }
    }

    private fun shouldSkipThreadInInboxRefresh(threadId: Int): Boolean =
        activeConversationId.value?.betterMessagesThreadIdOrNull() == threadId

    private suspend fun refreshVisibleBetterMessagesConversationDetails(profileId: String) {
        val current = realConversations.value
        if (current.isEmpty()) return
        val abandonedThreadIds = betterMessagesAbandonedConversationStore.abandonedThreadIds(profileId)
        val refreshed = current.mapNotNull { conversation ->
            val threadId = conversation.id.betterMessagesThreadIdOrNull() ?: return@mapNotNull conversation
            if (threadId in abandonedThreadIds) return@mapNotNull null
            val refreshedConversation = runCatching { loadBetterMessagesConversation(profileId, threadId) }
                .getOrNull()
                ?: return@mapNotNull conversation
            if (!conversation.isGroup && refreshedConversation.isGroup && !isKnownBetterMessagesGroupThread(profileId, threadId)) {
                conversation.copy(
                    lastMessagePreview = refreshedConversation.lastMessagePreview,
                    unreadCount = refreshedConversation.unreadCount,
                    updatedAt = refreshedConversation.updatedAt,
                    updatedAtMillis = refreshedConversation.updatedAtMillis,
                    isMuted = refreshedConversation.isMuted
                )
            } else {
                refreshedConversation
            }
        }
        setRealConversations(refreshed)
    }

    private fun List<Conversation>.updateMutedConversation(conversationId: String, muted: Boolean): List<Conversation> =
        map { conversation ->
            if (conversation.id == conversationId) conversation.copy(isMuted = muted) else conversation
        }

    private fun ensureRealConversationsPolling() {
        if (AppConfig.USE_MOCK_BACKEND || realConversationsPollJob?.isActive == true) return
        realConversationsPollJob = realConversationsScope.launch {
            var lastBetterMessagesUpdate = 0L
            var lastInboxRefreshAt = 0L
            var lastDetailsRefreshAt = 0L
            while (true) {
                val session = sessionManager.currentSession()
                if (session == null) {
                    realConversations.value = emptyList()
                    lastBetterMessagesUpdate = 0L
                    lastInboxRefreshAt = 0L
                    lastDetailsRefreshAt = 0L
                    realConversationsLoadedForProfileId = null
                    realConversationsLastRefreshedAt = 0L
                    delay(CONVERSATIONS_POLL_DELAY_MILLIS)
                    continue
                }

                activeConversationId.value
                    ?.takeIf { activeId ->
                        activeId != AppDestinations.FavoriteMessagesConversationId &&
                            realConversations.value.none { it.id == activeId }
                    }
                    ?.let { activeId ->
                        runCatching { loadRealConversation(activeId) }
                            .getOrNull()
                            ?.let(::upsertRealConversation)
                    }

                val activeBetterMessagesThreadId = activeConversationId.value?.betterMessagesThreadIdOrNull()
                val now = System.currentTimeMillis()
                val shouldLoadInbox = realConversationsLoadedForProfileId != session.userId ||
                    realConversations.value.isEmpty() ||
                    now - lastInboxRefreshAt >= INBOX_REFRESH_INTERVAL_MILLIS

                if (shouldLoadInbox) {
                    runCatching { loadRealConversations() }
                        .onSuccess { conversations ->
                            setRealConversations(conversations)
                            lastBetterMessagesUpdate = System.currentTimeMillis()
                            lastInboxRefreshAt = System.currentTimeMillis()
                            lastDetailsRefreshAt = lastInboxRefreshAt
                        }
                } else {
                    if (now - lastDetailsRefreshAt >= CONVERSATION_DETAILS_REFRESH_INTERVAL_MILLIS) {
                        runCatching { refreshVisibleBetterMessagesConversationDetails(session.userId) }
                            .onSuccess { lastDetailsRefreshAt = System.currentTimeMillis() }
                    }
                    if (activeBetterMessagesThreadId != null) {
                        delay(CONVERSATIONS_POLL_DELAY_MILLIS)
                        continue
                    }
                    runCatching {
                        pollBetterMessagesUpdates(
                            profileId = session.userId,
                            currentConversations = realConversations.value,
                            lastUpdate = lastBetterMessagesUpdate
                        )
                    }.onSuccess { poll ->
                        lastBetterMessagesUpdate = poll.nextLastUpdate
                        if (poll.hasUpdates) {
                            runCatching { loadRealConversations() }
                                .onSuccess { conversations ->
                                    setRealConversations(conversations)
                                    lastInboxRefreshAt = System.currentTimeMillis()
                                }
                        }
                    }
                }

                delay(CONVERSATIONS_POLL_DELAY_MILLIS)
            }
        }
    }

    private suspend fun pollBetterMessagesUpdates(
        profileId: String,
        currentConversations: List<Conversation>,
        lastUpdate: Long
    ): ConversationPollResult {
        val knownThreadIds = currentConversations
            .mapNotNull { it.id.betterMessagesThreadIdOrNull() }
            .distinct()
        val visibleThreads = activeConversationId.value
            ?.betterMessagesThreadIdOrNull()
            ?.let(::listOf)
            .orEmpty()
        val response = withBetterMessagesSession(profileId) {
            betterMessagesRepository.checkNewInCurrentSession(
                lastUpdate = lastUpdate,
                visibleThreads = visibleThreads,
                threadIds = knownThreadIds
            )
        }
        return ConversationPollResult(
            hasUpdates = response.threads.isNotEmpty() || response.messages.isNotEmpty(),
            nextLastUpdate = response.currentTime ?: System.currentTimeMillis()
        )
    }

    private suspend fun loadBetterMessagesInbox(
        profileId: String,
        privateThreadIds: Set<Int> = emptySet()
    ): List<Conversation> {
        val context = ensureBetterMessagesSession(profileId)
        val inbox = withBetterMessagesSession(profileId) {
            betterMessagesRepository.checkNewInCurrentSession(lastUpdate = 0)
        }
        val currentWpUserId = inferCurrentWpUserId(
            profile = context.profile,
            users = inbox.users,
            fallbackUserId = context.currentWpUserId
        )
        val messagesByThreadId = inbox.messages.groupBy { it.threadId }
        val abandonedThreadIds = betterMessagesAbandonedConversationStore.abandonedThreadIds(profileId)
        val activeThreadId = activeConversationId.value?.betterMessagesThreadIdOrNull()
        val profiles = (runCatching { remote.getProfiles() }.getOrDefault(emptyList()) + listOfNotNull(context.profile))
            .distinctBy { it.id }
        return inbox.threads
            .filter { thread -> thread.threadId > 0 && thread.isHidden != 1 && thread.isDeleted != 1 }
            .filterNot { thread -> thread.threadId in abandonedThreadIds }
            .filterNot { thread -> thread.threadId == activeThreadId }
            .filter { thread -> currentWpUserId == null || currentWpUserId in thread.participants }
            .mapNotNull { thread ->
                val knownMessages = messagesByThreadId[thread.threadId].orEmpty()
                val threadResponse = runCatching {
                    loadBetterMessagesThread(
                        profileId = profileId,
                        threadId = thread.threadId
                    )
                }.getOrNull()
                val fullThread = threadResponse?.threads?.firstOrNull { it.threadId == thread.threadId } ?: thread
                if (fullThread.type == "group") markBetterMessagesGroupThread(profileId, fullThread.threadId)
                val usersByWpId = (inbox.users + threadResponse?.users.orEmpty()).associateByWpId()
                val threadMessages = threadResponse?.messages?.takeIf { it.isNotEmpty() } ?: knownMessages
                if (threadMessages.isEmpty()) return@mapNotNull null
                val peerUsers = fullThread.participants
                    .filter { it != currentWpUserId }
                    .mapNotNull { usersByWpId[it] }
                val resolvedParticipants = fullThread.resolveBetterMessagesParticipants(
                    usersByWpId = usersByWpId,
                    currentWpUserId = currentWpUserId,
                    currentProfileId = profileId,
                    profiles = profiles
                )
                val participantNames = resolvedParticipants
                    .filterNot { it.profileId == profileId }
                    .map { it.name }
                val lastMessage = threadMessages.maxByOrNull {
                    it.created_at.toEpochMillisFromBetterMessagesOrNull() ?: 0L
                }
                val unreadCount = betterMessagesReadStateStore.unreadCount(
                    profileId = profileId,
                    threadId = fullThread.threadId,
                    messages = threadMessages,
                    currentWpUserId = currentWpUserId
                )
                val title = fullThread.title?.takeIf { it.isNotBlank() && it != "${fullThread.participantsCount ?: fullThread.participants.size} participantes" }
                    ?: fullThread.subject?.takeIf { it.isNotBlank() }
                    ?: participantNames.joinToString(", ").ifBlank { "Chat ${fullThread.threadId}" }
                val isKnownPrivateThread = fullThread.threadId in privateThreadIds
                val isGroupConversation = fullThread.isGroupConversation(
                    forceGroup = isKnownBetterMessagesGroupThread(profileId, fullThread.threadId),
                    isKnownPrivateThread = isKnownPrivateThread
                )
                if (isGroupConversation && !isKnownPrivateThread) {
                    markBetterMessagesGroupThread(profileId, fullThread.threadId)
                }
                Conversation(
                    id = betterMessagesConversationId(fullThread.threadId),
                    title = title,
                    avatarUrl = fullThread.image?.takeIf { it.isNotBlank() } ?: peerUsers.firstOrNull()?.avatar,
                    lastMessagePreview = lastMessage?.message?.stripHtml().orEmpty(),
                    unreadCount = unreadCount,
                    updatedAt = lastMessage?.created_at?.toDisplayTime() ?: fullThread.lastTime?.toDisplayTime().orEmpty(),
                    updatedAtMillis = lastMessage?.created_at?.toEpochMillisFromBetterMessagesOrNull()
                        ?: fullThread.lastTime?.toEpochMillisFromBetterMessagesOrNull(),
                    participantIds = resolvedParticipants.map { it.profileId },
                    participantNames = resolvedParticipants.map { it.name },
                    participantAvatarUrls = resolvedParticipants.map { it.avatarUrl },
                    isGroup = isGroupConversation,
                    isMuted = fullThread.isMuted == true,
                    isVisible = true,
                    moderatorIds = resolvedParticipants
                        .filter { it.wpUserId in fullThread.moderators }
                        .map { it.profileId },
                    canMembersInvite = fullThread.meta?.allowInvite == true || fullThread.permissions?.canInvite == true
                )
            }
    }

    private suspend fun trackBetterMessagesVisit(profileId: String, profile: CommunityProfile?) {
        val session = sessionManager.currentSession()
        val displayName = profile?.displayName()?.takeIf { it.isNotBlank() }
            ?: session?.displayName?.takeIf { it.isNotBlank() }
            ?: "Usuario"
        val barrio = profile?.neighborhood?.takeIf { it.isNotBlank() }
            ?: profile?.barrio?.takeIf { it.isNotBlank() }
            ?: ""
        val displayMetrics = appContext.resources.displayMetrics
        val platform = "Android ${Build.VERSION.RELEASE ?: ""}".trim()
        runCatching {
            wordpressClient.trackBetterMessagesVisit(
                profileId = profileId,
                displayName = displayName,
                barrio = barrio,
                visitorId = visitorId(profileId),
                language = Locale.getDefault().toLanguageTag(),
                timezone = TimeZone.getDefault().id,
                screen = "${displayMetrics.widthPixels}x${displayMetrics.heightPixels}",
                platform = platform.ifBlank { "Android" }
            )
        }
    }

    private suspend fun ensureBetterMessagesSession(
        profileId: String,
        forceRefresh: Boolean = false
    ): BetterMessagesSessionContext {
        return betterMessagesSessionMutex.withLock {
            betterMessagesSession
                ?.takeIf { !forceRefresh && it.profileId == profileId }
                ?.let { return@withLock it }

            val profile = runCatching { remote.getProfiles(listOf(profileId)).firstOrNull() }.getOrNull()
            val bridgeSession = betterMessagesRepository.prepareBridgeContext(profileId)
            cacheBetterMessagesProfile(profileId, bridgeSession.currentWpUserId)
            trackBetterMessagesVisit(profileId, profile)
            betterMessagesRepository.refreshRestNonce(profileId)
            BetterMessagesSessionContext(
                profileId = profileId,
                profile = profile,
                currentWpUserId = bridgeSession.currentWpUserId
            ).also { betterMessagesSession = it }
        }
    }

    private suspend fun <T> withBetterMessagesSession(
        profileId: String,
        block: suspend (BetterMessagesSessionContext) -> T
    ): T {
        var context = ensureBetterMessagesSession(profileId)
        return try {
            block(context)
        } catch (error: BetterMessagesHttpException) {
            if (error.statusCode != 401 && error.statusCode != 403) throw error
            context = ensureBetterMessagesSession(profileId, forceRefresh = true)
            block(context)
        }
    }

    private fun inferCurrentWpUserId(
        profile: CommunityProfile?,
        users: List<BmUser>,
        fallbackUserId: Int?
    ): Int? {
        val profileAvatar = profile?.avatar_url?.takeIf { it.isNotBlank() }
            ?: profile?.avatar?.takeIf { it.isNotBlank() }
        profileAvatar?.let { avatar ->
            users.firstOrNull { user -> user.avatar?.normalizedAvatarUrl() == avatar.normalizedAvatarUrl() }?.wpIdOrNull()?.let { userId ->
                cacheBetterMessagesProfile(profile?.id, userId)
                return userId
            }
        }
        val profileName = profile?.displayName()?.normalizeName()
        profileName?.let { expected ->
            users.firstOrNull { user -> user.name?.normalizeName() == expected }?.wpIdOrNull()?.let { userId ->
                cacheBetterMessagesProfile(profile?.id, userId)
                return userId
            }
        }
        cacheBetterMessagesProfile(profile?.id, fallbackUserId)
        return fallbackUserId
    }

    private fun observeBetterMessagesMessages(threadId: Int): Flow<List<Message>> = flow {
        val session = sessionManager.currentSession() ?: run {
            emit(emptyList())
            return@flow
        }
        var thread = loadBetterMessagesThread(session.userId, threadId)
        emit(thread.toDomainMessages(session.userId))
        var lastUpdate = thread.serverTime
            ?: thread.messages.maxOfOrNull { it.updated_at ?: it.created_at }
            ?: 0L

        while (true) {
            delay(MESSAGES_POLL_DELAY_MILLIS)
            val knownThreadIds = (realConversations.value.mapNotNull { it.id.betterMessagesThreadIdOrNull() } + threadId)
                .distinct()
            val now = System.currentTimeMillis()
            val poll = withBetterMessagesSession(session.userId) {
                betterMessagesRepository.checkNewInCurrentSession(
                    lastUpdate = lastUpdate,
                    visibleThreads = listOf(threadId),
                    threadIds = knownThreadIds
                )
            }
            val hasMessageUpdate = poll.messages.any { it.threadId == threadId }
            val hasOtherConversationUpdate = poll.messages.any { it.threadId != threadId }
            lastUpdate = poll.currentTime ?: lastUpdate
            if (hasOtherConversationUpdate) {
                runCatching { loadRealConversations() }
                    .onSuccess { conversations -> setRealConversations(conversations) }
            }
            if (now - realConversationsLastRefreshedAt >= INBOX_REFRESH_INTERVAL_MILLIS) {
                runCatching { loadRealConversations() }
                    .onSuccess { conversations -> setRealConversations(conversations) }
            }
            if (hasMessageUpdate) {
                thread = loadBetterMessagesThread(session.userId, threadId)
                emit(thread.toDomainMessages(session.userId))
                lastUpdate = thread.serverTime ?: lastUpdate
            }
        }
    }

    private suspend fun loadRealMessages(conversationId: String): List<Message> {
        if (conversationId == AppDestinations.FavoriteMessagesConversationId) {
            return loadRealConversations()
                .filter { it.id.betterMessagesThreadIdOrNull() != null }
                .flatMap { loadRealMessages(it.id) }
                .filter { it.isFavorite && !it.isDeleted }
        }
        conversationId.betterMessagesThreadIdOrNull()?.let { return loadBetterMessages(it) }

        val wallId = conversationId.wallIdOrNull() ?: return emptyList()
        val session = sessionManager.currentSession() ?: return emptyList()
        val messages = remote.getCommunityMessages(wallId)
        val profileIds = messages.mapNotNull { it.profile_id }.distinct()
        val profilesById = if (profileIds.isEmpty()) emptyMap() else remote.getProfiles(profileIds).associateBy { it.id }
        return messages
            .sortedBy { it.created_at.orEmpty() }
            .map { message ->
                message.toDomain(
                    conversationId = conversationId,
                    senderName = profilesById[message.profile_id]?.displayName() ?: "Usuario",
                    currentProfileId = session.userId
                )
            }
    }

    private suspend fun loadBetterMessages(threadId: Int): List<Message> {
        val session = sessionManager.currentSession() ?: return emptyList()
        return loadBetterMessagesThread(session.userId, threadId).toDomainMessages(session.userId)
    }

    private suspend fun loadBetterMessagesThread(
        profileId: String,
        threadId: Int,
        knownMessageIds: List<Int> = emptyList()
    ): BmThreadResponse {
        return withBetterMessagesSession(profileId) {
            betterMessagesRepository.loadThreadInCurrentSession(threadId, knownMessageIds)
        }.also { response ->
            response.messages
                .filter { it.threadId == threadId }
                .takeIf { it.isNotEmpty() }
                ?.let { betterMessagesByThreadId[threadId] = it }
        }
    }

    private fun cacheBetterMessagesUpdate(threadId: Int, update: BmThreadResponse?) {
        update?.messages
            ?.filter { it.threadId == threadId }
            ?.takeIf { it.isNotEmpty() }
            ?.let { betterMessagesByThreadId[threadId] = it }
    }

    private suspend fun BmThreadResponse.toDomainMessages(profileId: String): List<Message> {
        val context = ensureBetterMessagesSession(profileId)
        val usersByWpId = users.associateByWpId()
        val currentWpUserId = inferCurrentWpUserId(
            profile = context.profile,
            users = users,
            fallbackUserId = context.currentWpUserId
        )
        val lookup = messages.associateBy { it.messageId }
        val readIdsByThreadId = messages
            .groupBy { it.threadId }
            .mapValues { (threadId, threadMessages) ->
                betterMessagesReadStateStore.readMessageIds(
                    profileId = profileId,
                    threadId = threadId,
                    messages = threadMessages,
                    currentWpUserId = currentWpUserId
                )
            }
        return messages
            .sortedBy { it.created_at.toEpochMillisFromBetterMessagesOrNull() ?: 0L }
            .map { message ->
                message.toDomain(
                    usersByWpId = usersByWpId,
                    currentWpUserId = currentWpUserId,
                    replyLookup = lookup,
                    isRead = currentWpUserId != null && message.senderId == currentWpUserId ||
                        message.messageId in readIdsByThreadId[message.threadId].orEmpty()
                )
            }
    }

    private suspend fun resolvePrivateThreadId(profileId: String, peerProfileId: String): Int? {
        threadIdsByPeerProfileId[peerProfileId]?.let { return it }
        ensureBetterMessagesSession(profileId)
        val threadId = betterMessagesRepository.openOrGetPrivateUrlInCurrentSession(profileId, peerProfileId).threadId?.takeIf { it > 0 }
            ?: betterMessagesRepository.getOrCreatePrivateThread(profileId, peerProfileId)
                .threads
                .firstOrNull { it.threadId > 0 }
                ?.threadId
        if (threadId != null) {
            betterMessagesAbandonedConversationStore.clearAbandoned(profileId, threadId)
            threadIdsByPeerProfileId[peerProfileId] = threadId
        }
        return threadId
    }

    private suspend fun String.toBetterMessagesUserId(): Int {
        removePrefix("wp:")
            .takeIf { startsWith("wp:") }
            ?.toIntOrNull()
            ?.let { return it }
        betterMessagesWpUserIdsByProfileId[this]?.let { return it }
        val wpUserId = betterMessagesRepository.lookupWordPressUserId(this)
            ?: error("No se pudo sincronizar el usuario con Better Messages")
        cacheBetterMessagesProfile(this, wpUserId)
        return wpUserId
    }

    private fun cacheBetterMessagesProfile(profileId: String?, wpUserId: Int?) {
        val cleanProfileId = profileId?.takeIf { it.isNotBlank() && !it.startsWith("wp:") } ?: return
        val cleanWpUserId = wpUserId?.takeIf { it > 0 } ?: return
        betterMessagesWpUserIdsByProfileId[cleanProfileId] = cleanWpUserId
        betterMessagesProfileIdsByWpUserId[cleanWpUserId] = cleanProfileId
    }

    private suspend fun markBetterMessagesGroupThread(profileId: String, threadId: Int) {
        betterMessagesGroupThreadIds += threadId
        betterMessagesConversationTypeStore.markGroup(profileId, threadId)
    }

    private suspend fun isKnownBetterMessagesGroupThread(profileId: String, threadId: Int): Boolean =
        threadId in betterMessagesGroupThreadIds || betterMessagesConversationTypeStore.isGroup(profileId, threadId)

    private fun List<BmUser>.associateByWpId(): Map<Int, BmUser> =
        mapNotNull { user ->
            val id = user.wpIdOrNull()
            id?.let { it to user }
        }.toMap()

    private fun BmUser.wpIdOrNull(): Int? = userId ?: id.toIntOrNull()

    private fun BmThread.toBetterMessagesConversation(
        usersByWpId: Map<Int, BmUser>,
        currentWpUserId: Int?,
        messages: List<BmMessage>,
        resolvedParticipants: List<BetterMessagesParticipant> = emptyList(),
        forceGroup: Boolean = false,
        isKnownPrivateThread: Boolean = false
    ): Conversation {
        val peerUsers = participants
            .filter { it != currentWpUserId }
            .mapNotNull { usersByWpId[it] }
        val participantNames = resolvedParticipants
            .takeIf { it.isNotEmpty() }
            ?.filter { it.wpUserId != currentWpUserId }
            ?.map { it.name }
            ?: peerUsers.mapNotNull { it.name?.takeIf(String::isNotBlank) }
        val lastMessage = messages.maxByOrNull {
            it.created_at.toEpochMillisFromBetterMessagesOrNull() ?: 0L
        }
        val defaultParticipantsTitle = "${participantsCount ?: participants.size} participantes"
        val displayTitle = title
            ?.takeIf { it.isNotBlank() && it != defaultParticipantsTitle }
            ?: subject?.takeIf { it.isNotBlank() }
            ?: participantNames.joinToString(", ").ifBlank { "Chat $threadId" }
        return Conversation(
            id = betterMessagesConversationId(threadId),
            title = displayTitle,
            avatarUrl = image?.takeIf { it.isNotBlank() } ?: peerUsers.firstOrNull()?.avatar,
            lastMessagePreview = lastMessage?.message?.stripHtml().orEmpty(),
            unreadCount = unread ?: 0,
            updatedAt = lastMessage?.created_at?.toDisplayTime() ?: lastTime?.toDisplayTime().orEmpty(),
            updatedAtMillis = lastMessage?.created_at?.toEpochMillisFromBetterMessagesOrNull()
                ?: lastTime?.toEpochMillisFromBetterMessagesOrNull(),
            participantIds = resolvedParticipants
                .takeIf { it.isNotEmpty() }
                ?.map { it.profileId }
                ?: participants.map { "wp:$it" },
            participantNames = resolvedParticipants
                .takeIf { it.isNotEmpty() }
                ?.map { it.name }
                ?: participantNames,
            participantAvatarUrls = resolvedParticipants
                .takeIf { it.isNotEmpty() }
                ?.map { it.avatarUrl }
                ?: participants.map { usersByWpId[it]?.avatar },
            isGroup = isGroupConversation(
                forceGroup = forceGroup,
                isKnownPrivateThread = isKnownPrivateThread
            ),
            isMuted = isMuted == true,
            isVisible = isHidden != 1 && isDeleted != 1,
            moderatorIds = resolvedParticipants
                .takeIf { it.isNotEmpty() }
                ?.filter { it.wpUserId in moderators }
                ?.map { it.profileId }
                ?: moderators.map { "wp:$it" },
            canMembersInvite = meta?.allowInvite == true || permissions?.canInvite == true
        )
    }

    private fun BmThread.isGroupConversation(
        forceGroup: Boolean = false,
        isKnownPrivateThread: Boolean = false
    ): Boolean {
        if (forceGroup || type == "group") return true
        if (isKnownPrivateThread) return false
        return participants.size > 2 || (participantsCount ?: 0) > 2
    }

    private fun BmThread.resolveBetterMessagesParticipants(
        usersByWpId: Map<Int, BmUser>,
        currentWpUserId: Int?,
        currentProfileId: String,
        profiles: List<CommunityProfile>
    ): List<BetterMessagesParticipant> {
        val profilesById = profiles.associateBy { it.id }
        return participants.map { wpUserId ->
            val user = usersByWpId[wpUserId]
            val cachedProfile = betterMessagesProfileIdsByWpUserId[wpUserId]?.let(profilesById::get)
            val profile = when {
                currentWpUserId != null && wpUserId == currentWpUserId -> profilesById[currentProfileId]
                cachedProfile != null -> cachedProfile
                else -> user?.matchingProfile(profiles)
            }
            cacheBetterMessagesProfile(profile?.id, wpUserId)
            BetterMessagesParticipant(
                wpUserId = wpUserId,
                profileId = profile?.id ?: "wp:$wpUserId",
                name = profile?.displayName()?.takeIf { it.isNotBlank() }
                    ?: user?.name?.takeIf { it.isNotBlank() }
                    ?: "Usuario",
                avatarUrl = profile?.avatar_url ?: profile?.avatar ?: user?.avatar
            )
        }.distinctBy { it.wpUserId }
    }

    private fun BmUser.matchingProfile(profiles: List<CommunityProfile>): CommunityProfile? {
        avatar?.takeIf { it.isNotBlank() }?.let { expectedAvatar ->
            expectedAvatar.supabaseProfileIdOrNull()?.let { profileId ->
                profiles.firstOrNull { profile -> profile.id.equals(profileId, ignoreCase = true) }?.let { return it }
            }
            val normalizedExpectedAvatar = expectedAvatar.normalizedAvatarUrl()
            profiles.firstOrNull { profile ->
                profile.avatar_url?.normalizedAvatarUrl() == normalizedExpectedAvatar ||
                    profile.avatar?.normalizedAvatarUrl() == normalizedExpectedAvatar
            }?.let { return it }
        }
        val expectedName = name?.normalizeName()?.takeIf { it.isNotBlank() } ?: return null
        return profiles.firstOrNull { it.displayName().normalizeName() == expectedName }
    }

    private fun CommunityProfile.toUser(): User =
        User(
            id = id,
            email = "${country_code.orEmpty()}${phone_local.orEmpty()}@phone.quata.app",
            displayName = displayName(),
            neighborhood = neighborhood?.takeIf { it.isNotBlank() } ?: barrio.orEmpty(),
            avatarUrl = avatar_url ?: avatar
        )

    private fun CommunityProfile.displayName(): String =
        display_name?.takeIf { it.isNotBlank() }
            ?: nombre?.takeIf { it.isNotBlank() }
            ?: phone_local?.takeIf { it.isNotBlank() }
            ?: "Usuario"

    private fun CommunityProfile.matchesWall(wallNames: Set<String>): Boolean {
        if (wallNames.isEmpty()) return false
        return listOfNotNull(neighborhood, barrio, barrio_normalized)
            .map { it.normalizeName() }
            .any { it in wallNames }
    }

    private fun com.quata.data.supabase.CommunityPrivateChat.hasVisibleMessage(): Boolean =
        !last_message_preview.isNullOrBlank() || !last_message_at.isNullOrBlank()

    private fun visitorId(profileId: String): String {
        val deviceId = runCatching {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()
        return "v-android-${deviceId?.takeIf { it.isNotBlank() } ?: profileId.take(12)}"
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

    private suspend fun Context.copyUriToCache(uriString: String, attachmentName: String?, attachmentMimeType: String?): CachedMedia =
        withContext(Dispatchers.IO) {
            val media = readUriBytes(uriString, attachmentName, attachmentMimeType)
            val uploadDir = File(cacheDir, "quata-bm-upload-${System.currentTimeMillis()}-${Random.nextInt(1_000, 9_999)}")
            uploadDir.mkdirs()
            val file = File(uploadDir, media.fileName.sanitizeUploadFileName())
            file.writeBytes(media.bytes)
            CachedMedia(file = file, mimeType = media.mimeType)
        }

    private suspend fun Context.readUriBytes(uriString: String, attachmentName: String?, attachmentMimeType: String?): MediaPayload =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(uriString)
            val mimeType = attachmentMimeType ?: contentResolver.getType(uri) ?: "application/octet-stream"
            val fileName = attachmentName?.takeIf { it.isNotBlank() }
                ?: displayName(uri).ifBlank { "quata-${System.currentTimeMillis()}.${mimeType.substringAfter('/', "bin")}" }
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("No se pudo leer el archivo seleccionado")
            MediaPayload(
                fileName = fileName,
                mimeType = mimeType,
                extension = fileName.substringAfterLast('.', mimeType.substringAfter('/', "bin")).lowercase(),
                bytes = bytes
            )
        }

    private fun Context.displayName(uri: Uri): String {
        val fromProvider = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
        return fromProvider?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: ""
    }

    private fun String.stripHtml(): String = replace(Regex("<[^>]*>"), "").trim()

    private fun String.normalizeName(): String = trim().lowercase(Locale.ROOT)

    private fun String.normalizedAvatarUrl(): String = trim().substringBefore("?").trimEnd('/')

    private fun String.supabaseProfileIdOrNull(): String? =
        PROFILE_ID_REGEX.find(this)?.value

    private fun String.sanitizeUploadFileName(): String =
        replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .ifBlank { "upload.bin" }

    private data class CachedMedia(val file: File, val mimeType: String) {
        fun delete() {
            runCatching {
                file.parentFile
                    ?.takeIf { it.name.startsWith("quata-bm-upload-") }
                    ?.deleteRecursively()
                    ?: file.delete()
            }
        }
    }

    private data class MediaPayload(
        val fileName: String,
        val mimeType: String,
        val extension: String,
        val bytes: ByteArray
    )

    private data class ConversationPollResult(
        val hasUpdates: Boolean,
        val nextLastUpdate: Long
    )

    private data class BetterMessagesSessionContext(
        val profileId: String,
        val profile: CommunityProfile?,
        val currentWpUserId: Int?
    )

    private data class BetterMessagesParticipant(
        val wpUserId: Int,
        val profileId: String,
        val name: String,
        val avatarUrl: String?
    )

    private companion object {
        const val CONVERSATIONS_POLL_DELAY_MILLIS = 2_000L
        const val MESSAGES_POLL_DELAY_MILLIS = 2_000L
        const val CONVERSATION_DETAILS_REFRESH_INTERVAL_MILLIS = 10_000L
        const val INBOX_REFRESH_INTERVAL_MILLIS = 30_000L
        val PROFILE_ID_REGEX = Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")
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
