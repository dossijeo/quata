package com.quata.feature.chat.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import com.quata.R
import com.quata.bettermessages.BetterMessagesRepository
import com.quata.bettermessages.BmThread
import com.quata.bettermessages.BmUser
import com.quata.core.common.mapFailureToUserFacing
import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.core.navigation.AppDestinations
import com.quata.core.session.SessionManager
import com.quata.data.supabase.CommunityProfile
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
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.TimeZone
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
    private var realConversationsPollJob: Job? = null
    private val _activeConversationId = MutableStateFlow<String?>(null)
    override val activeConversationId: StateFlow<String?> = _activeConversationId
    private val _pendingDeletedConversation = MutableStateFlow<Conversation?>(null)
    override val pendingDeletedConversation: StateFlow<Conversation?> = _pendingDeletedConversation
    private val threadIdsByPeerProfileId = mutableMapOf<String, Int>()

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
            realConversations.value.takeIf { it.isNotEmpty() } ?: loadRealConversations().also { realConversations.value = it }
        }
    }.mapFailureToUserFacing(appContext, R.string.error_load_chats)

    override fun observeConversations(): Flow<List<Conversation>> {
        return if (AppConfig.USE_MOCK_BACKEND) {
            MockData.conversationsFlow
        } else {
            ensureRealConversationsPolling()
            flow {
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
                betterMessagesRepository.sendText(session.userId, threadId, text)
            } else {
                val media = appContext.copyUriToCache(attachmentUri, attachmentName, attachmentMimeType)
                val upload = betterMessagesRepository.uploadFile(session.userId, threadId, media.file, media.mimeType)
                val fileId = upload.id ?: error(upload.error ?: "Better Messages no devolvio id de archivo")
                betterMessagesRepository.sendFiles(session.userId, threadId, listOf(fileId), text)
                media.file.delete()
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
            betterMessagesRepository.sendReply(session.userId, threadId, text, replyMessageId)
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
        }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun setConversationMuted(conversationId: String, muted: Boolean): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.setConversationMuted(conversationId, muted)
            return@runCatching
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        conversationId.betterMessagesThreadIdOrNull()?.let {
            betterMessagesRepository.muteThread(session.userId, it, muted)
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
            error("Anadir participantes requiere mapear perfiles Supabase a usuarios WordPress")
        }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun promoteModerator(conversationId: String, userId: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.promoteModerator(conversationId, userId)
        } else {
            error("Moderadores no disponible en backend real")
        }
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
            betterMessagesRepository.leaveThread(session.userId, it)
        }
        Unit
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
        }
        _pendingDeletedConversation.value = conversation?.copy(isVisible = false)
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun restorePendingDeletedConversation(): Result<Unit> = runCatching {
        val conversation = _pendingDeletedConversation.value ?: return@runCatching
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.restoreConversation(conversation.id)
        } else {
            val session = sessionManager.currentSession() ?: error("No hay sesion activa")
            conversation.id.betterMessagesThreadIdOrNull()?.let {
                betterMessagesRepository.restoreThread(session.userId, it)
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
        } else {
            error("Edicion de mensajes no disponible en Better Messages REST")
        }
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
        val session = sessionManager.currentSession() ?: return emptyList()
        val privateChats = remote.getPrivateChats(session.userId)
        val peerIds = privateChats.mapNotNull { chat ->
            listOfNotNull(chat.user_low_id, chat.user_high_id, chat.requester_profile_id, chat.target_profile_id)
                .distinct()
                .firstOrNull { it != session.userId }
        }.distinct()
        val profilesById = if (peerIds.isEmpty()) emptyMap() else remote.getProfiles(peerIds).associateBy { it.id }
        val privateConversations = privateChats.mapNotNull { chat ->
            runCatching {
                val peerId = listOfNotNull(chat.user_low_id, chat.user_high_id, chat.requester_profile_id, chat.target_profile_id)
                    .distinct()
                    .firstOrNull { it != session.userId } ?: return@runCatching null
                val peer = profilesById[peerId]
                val threadId = resolvePrivateThreadId(session.userId, peerId) ?: return@runCatching null
                val threadResponse = runCatching { betterMessagesRepository.loadThread(session.userId, threadId) }.getOrNull()
                val thread = threadResponse?.threads?.firstOrNull { it.threadId == threadId } ?: BmThread(threadId = threadId)
                val lastMessage = threadResponse?.messages?.maxByOrNull { it.created_at }
                thread.toConversation(
                    title = peer?.displayName().orEmpty(),
                    peerProfileId = peerId,
                    peerName = peer?.displayName() ?: "Usuario",
                    peerAvatarUrl = peer?.avatar_url ?: peer?.avatar,
                    currentProfileId = session.userId
                ).copy(
                    lastMessagePreview = lastMessage?.message?.stripHtml() ?: chat.last_message_preview.orEmpty(),
                    updatedAt = lastMessage?.created_at?.toDisplayTime() ?: chat.last_message_at.orEmpty(),
                    updatedAtMillis = lastMessage?.created_at?.toEpochMillisFromBetterMessages()
                        ?: chat.last_message_at?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
                )
            }.getOrNull()
        }
        val bmConversations = runCatching { loadBetterMessagesInbox(session.userId) }.getOrDefault(emptyList())
        val activeWallId = activeConversationId.value?.wallIdOrNull()
        val wallConversations = if (activeWallId == null) {
            emptyList()
        } else {
            remote.getActiveWalls().filter { it.id == activeWallId }
        }.map { wall ->
            wall.toConversation().copy(
                lastMessagePreview = "",
                updatedAt = wall.chat_last_at.orEmpty(),
                updatedAtMillis = wall.chat_last_at?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
            )
        }
        return (privateConversations + bmConversations + wallConversations)
            .distinctBy { it.id }
            .sortedByDescending { it.updatedAtMillis ?: 0L }
    }

    private fun ensureRealConversationsPolling() {
        if (AppConfig.USE_MOCK_BACKEND || realConversationsPollJob?.isActive == true) return
        realConversationsPollJob = realConversationsScope.launch {
            var lastBetterMessagesUpdate = 0L
            while (true) {
                val session = sessionManager.currentSession()
                if (session == null) {
                    realConversations.value = emptyList()
                    lastBetterMessagesUpdate = 0L
                    delay(CONVERSATIONS_POLL_DELAY_MILLIS)
                    continue
                }

                if (realConversations.value.isEmpty()) {
                    runCatching { loadRealConversations() }
                        .onSuccess { conversations ->
                            realConversations.value = conversations
                            lastBetterMessagesUpdate = System.currentTimeMillis()
                        }
                } else {
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
                                .onSuccess { conversations -> realConversations.value = conversations }
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
        val response = betterMessagesRepository.checkNew(
            profileId = profileId,
            lastUpdate = lastUpdate,
            visibleThreads = visibleThreads,
            threadIds = knownThreadIds
        )
        return ConversationPollResult(
            hasUpdates = response.threads.isNotEmpty() || response.messages.isNotEmpty(),
            nextLastUpdate = response.currentTime ?: System.currentTimeMillis()
        )
    }

    private suspend fun loadBetterMessagesInbox(profileId: String): List<Conversation> {
        val profile = runCatching { remote.getProfiles(listOf(profileId)).firstOrNull() }.getOrNull()
        val bridgeSession = betterMessagesRepository.prepareBridgeContext(profileId)
        trackBetterMessagesVisit(profileId, profile)
        betterMessagesRepository.refreshRestNonce(profileId)
        val inbox = betterMessagesRepository.checkNewInCurrentSession(lastUpdate = 0)
        val currentWpUserId = inferCurrentWpUserId(
            profile = profile,
            users = inbox.users,
            fallbackUserId = bridgeSession.currentWpUserId
        )
        val messagesByThreadId = inbox.messages.groupBy { it.threadId }
        return inbox.threads
            .filter { thread -> thread.threadId > 0 && thread.isHidden != 1 && thread.isDeleted != 1 }
            .filter { thread -> currentWpUserId == null || currentWpUserId in thread.participants }
            .map { thread ->
                val knownMessages = messagesByThreadId[thread.threadId].orEmpty()
                val threadResponse = runCatching {
                    betterMessagesRepository.loadThreadInCurrentSession(
                        threadId = thread.threadId,
                        knownMessageIds = knownMessages.map { it.messageId }
                    )
                }.getOrNull()
                val fullThread = threadResponse?.threads?.firstOrNull { it.threadId == thread.threadId } ?: thread
                val usersByWpId = (inbox.users + threadResponse?.users.orEmpty()).associateByWpId()
                val threadMessages = threadResponse?.messages?.takeIf { it.isNotEmpty() } ?: knownMessages
                val peerUsers = fullThread.participants
                    .filter { it != currentWpUserId }
                    .mapNotNull { usersByWpId[it] }
                val participantNames = peerUsers.mapNotNull { it.name?.takeIf(String::isNotBlank) }
                val lastMessage = threadMessages.maxByOrNull { it.created_at }
                val title = fullThread.title?.takeIf { it.isNotBlank() && it != "${fullThread.participantsCount ?: fullThread.participants.size} participantes" }
                    ?: fullThread.subject?.takeIf { it.isNotBlank() }
                    ?: participantNames.joinToString(", ").ifBlank { "Chat ${fullThread.threadId}" }
                Conversation(
                    id = betterMessagesConversationId(fullThread.threadId),
                    title = title,
                    avatarUrl = fullThread.image?.takeIf { it.isNotBlank() } ?: peerUsers.firstOrNull()?.avatar,
                    lastMessagePreview = lastMessage?.message?.stripHtml().orEmpty(),
                    unreadCount = fullThread.unread ?: 0,
                    updatedAt = lastMessage?.created_at?.toDisplayTime() ?: fullThread.lastTime?.toDisplayTime().orEmpty(),
                    updatedAtMillis = lastMessage?.created_at?.toEpochMillisFromBetterMessages()
                        ?: fullThread.lastTime?.toEpochMillisFromBetterMessages(),
                    participantIds = fullThread.participants.map { "wp:$it" },
                    participantNames = participantNames,
                    isGroup = fullThread.type == "group" || fullThread.participants.size > 2,
                    isMuted = fullThread.isMuted == true,
                    isVisible = true,
                    moderatorIds = fullThread.moderators.map { "wp:$it" },
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

    private fun inferCurrentWpUserId(
        profile: CommunityProfile?,
        users: List<BmUser>,
        fallbackUserId: Int?
    ): Int? {
        val profileAvatar = profile?.avatar_url?.takeIf { it.isNotBlank() }
            ?: profile?.avatar?.takeIf { it.isNotBlank() }
        profileAvatar?.let { avatar ->
            users.firstOrNull { user -> user.avatar == avatar }?.wpIdOrNull()?.let { return it }
        }
        val profileName = profile?.displayName()?.normalizeName()
        profileName?.let { expected ->
            users.firstOrNull { user -> user.name?.normalizeName() == expected }?.wpIdOrNull()?.let { return it }
        }
        return fallbackUserId
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
        val profile = runCatching { remote.getProfiles(listOf(session.userId)).firstOrNull() }.getOrNull()
        val bridgeSession = betterMessagesRepository.prepareBridgeContext(session.userId)
        betterMessagesRepository.refreshRestNonce(session.userId)
        val thread = betterMessagesRepository.loadThreadInCurrentSession(threadId)
        val usersByWpId = thread.users.associateByWpId()
        val currentWpUserId = inferCurrentWpUserId(
            profile = profile,
            users = thread.users,
            fallbackUserId = bridgeSession.currentWpUserId
        )
        val lookup = thread.messages.associateBy { it.messageId }
        return thread.messages
            .sortedBy { it.created_at }
            .map { it.toDomain(usersByWpId, currentWpUserId, lookup) }
    }

    private suspend fun resolvePrivateThreadId(profileId: String, peerProfileId: String): Int? {
        threadIdsByPeerProfileId[peerProfileId]?.let { return it }
        val threadId = betterMessagesRepository.openOrGetPrivateUrl(profileId, peerProfileId).threadId?.takeIf { it > 0 }
        if (threadId != null) threadIdsByPeerProfileId[peerProfileId] = threadId
        return threadId
    }

    private fun List<BmUser>.associateByWpId(): Map<Int, BmUser> =
        mapNotNull { user ->
            val id = user.wpIdOrNull()
            id?.let { it to user }
        }.toMap()

    private fun BmUser.wpIdOrNull(): Int? = userId ?: id.toIntOrNull()

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
            val file = File.createTempFile("quata-bm-", "-${media.fileName}", cacheDir)
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

    private data class CachedMedia(val file: File, val mimeType: String)

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

    private companion object {
        const val CONVERSATIONS_POLL_DELAY_MILLIS = 2_000L
        const val MESSAGES_POLL_DELAY_MILLIS = 2_000L
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
