package com.quata.feature.chat.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.quata.BuildConfig
import com.quata.R
import com.quata.bettermessages.BetterMessagesHttpException
import com.quata.bettermessages.BetterMessagesRepository
import com.quata.bettermessages.BmMessage
import com.quata.bettermessages.BmNewThreadResponse
import com.quata.bettermessages.BmThread
import com.quata.bettermessages.BmThreadResponse
import com.quata.bettermessages.BmUser
import com.quata.core.common.UserFacingException
import com.quata.core.common.mapFailureToUserFacing
import com.quata.core.common.serverMessageOrNull
import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.media.MediaUploadOptimizer
import com.quata.core.model.AuthSession
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.core.navigation.AppDestinations
import com.quata.core.notifications.NotificationFactory
import com.quata.core.session.SessionManager
import com.quata.core.text.stripHtmlTagsAndDecode
import com.quata.data.supabase.CommunityProfile
import com.quata.data.supabase.CommunityWallStats
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.domain.ChatPollingMode
import com.quata.feature.chat.domain.SosRateLimitException
import com.quata.wordpress.QuataWordPressClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
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
    private val sessionManager: SessionManager,
    private val mediaUploadOptimizer: MediaUploadOptimizer
) : ChatRepository {
    private val mockReplyScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val realConversationsScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val realConversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val betterMessagesReadStateStore = BetterMessagesReadStateStore(appContext)
    private val betterMessagesPollingStateStore = BetterMessagesPollingStateStore(appContext)
    private val betterMessagesConversationCacheStore = BetterMessagesConversationCacheStore(appContext)
    private val betterMessagesAbandonedConversationStore = BetterMessagesAbandonedConversationStore(appContext)
    private val betterMessagesConversationTypeStore = BetterMessagesConversationTypeStore(appContext)
    private val notificationFactory = NotificationFactory(appContext)
    private val betterMessagesByThreadId = ConcurrentHashMap<Int, List<BmMessage>>()
    private val betterMessagesThreadsById = ConcurrentHashMap<Int, BmThread>()
    private val betterMessagesUsersByThreadId = ConcurrentHashMap<Int, List<BmUser>>()
    private val betterMessagesThreadResponses = ConcurrentHashMap<Int, MutableStateFlow<BmThreadResponse>>()
    private val betterMessagesRegisteredThreadIds = ConcurrentHashMap.newKeySet<Int>()
    private val favoriteMessagesByProfileId = ConcurrentHashMap<String, MutableStateFlow<List<Message>>>()
    private val favoriteMessagesLoadedProfileIds = ConcurrentHashMap.newKeySet<String>()
    private val pollingWakeSignal = Channel<Unit>(Channel.CONFLATED)
    private var realConversationsPollJob: Job? = null
    private val _activeConversationId = MutableStateFlow<String?>(null)
    override val activeConversationId: StateFlow<String?> = _activeConversationId
    private val pollingMode = MutableStateFlow(ChatPollingMode.MINIMAL)
    private val appForegroundState = MutableStateFlow(false)
    override val isAppForeground: StateFlow<Boolean> = appForegroundState
    private val _pendingDeletedConversation = MutableStateFlow<Conversation?>(null)
    override val pendingDeletedConversation: StateFlow<Conversation?> = _pendingDeletedConversation
    private val _isPollingOnline = MutableStateFlow(true)
    override val isPollingOnline: StateFlow<Boolean> = _isPollingOnline
    private val deviceNetworkAvailable = MutableStateFlow(true)
    private val threadIdsByPeerProfileId = mutableMapOf<String, Int>()
    private val betterMessagesWpUserIdsByProfileId = ConcurrentHashMap<String, Int>()
    private val betterMessagesProfileIdsByWpUserId = ConcurrentHashMap<Int, String>()
    private val betterMessagesGroupThreadIds = ConcurrentHashMap.newKeySet<Int>()
    private val betterMessagesProfilesById = ConcurrentHashMap<String, CommunityProfile>()
    private val betterMessagesProfilesMutex = Mutex()
    private val realConversationsLoadMutex = Mutex()
    private val ensuredWallMemberships = mutableSetOf<String>()
    private val betterMessagesSessionMutex = Mutex()
    private var betterMessagesSession: BetterMessagesSessionContext? = null
    private var betterMessagesInboxLastUpdate: Long = 0L
    private var realConversationsLoadedForProfileId: String? = null
    private var realConversationsLastRefreshedAt: Long = 0L
    private var betterMessagesProfilesLoadedAt: Long = 0L
    private var isBetterMessagesPollingOnline: Boolean? = null
    override fun setDeviceNetworkAvailable(isAvailable: Boolean) {
        deviceNetworkAvailable.value = isAvailable
        if (isAvailable) {
            if (_isPollingOnline.value == false) {
                logPolling("device network available")
                _isPollingOnline.value = true
            }
            wakePolling()
        } else {
            logPolling("device network unavailable")
            isBetterMessagesPollingOnline = false
            _isPollingOnline.value = false
        }
    }

    override fun setActiveConversation(conversationId: String?) {
        _activeConversationId.value = conversationId
        conversationId
            ?.betterMessagesThreadIdOrNull()
            ?.let { registerBetterMessagesThread(it, forceDue = true) }
        wakePolling()
    }

    override fun setAppForeground(isForeground: Boolean) {
        if (appForegroundState.value == isForeground) return
        appForegroundState.value = isForeground
        logPolling("foreground=$isForeground")
        wakePolling()
    }

    override fun setPollingMode(mode: ChatPollingMode) {
        val previous = pollingMode.value
        if (previous == mode) {
            ensureRealConversationsPolling()
            wakePolling()
            return
        }
        pollingMode.value = mode
        logPolling("mode $previous -> $mode")
        ensureRealConversationsPolling()
        wakePolling()
    }

    override fun clearChatNotifications() {
        notificationFactory.clearChatMessages()
        val knownConversations = if (AppConfig.USE_MOCK_BACKEND) {
            MockData.conversations
        } else {
            realConversations.value
        }
        knownConversations.forEach { conversation ->
            notificationFactory.clearChatMessage(conversation.id)
        }
    }

    override fun currentUser(): User? {
        val session = sessionManager.currentSession() ?: return null
        if (AppConfig.USE_MOCK_BACKEND) {
            return MockData.profileById(session.userId)?.toUser() ?: MockData.currentUser
        }
        return User(id = session.userId, email = "", displayName = session.displayName)
    }

    override suspend fun pollForBackgroundNotifications(): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) return@runCatching
        if (appForegroundState.value) return@runCatching
        if (pollingMode.value == ChatPollingMode.AGGRESSIVE || pollingMode.value == ChatPollingMode.MEDIUM) return@runCatching
        val session = sessionManager.currentSession() ?: return@runCatching
        restoreCachedConversationsIfNeeded(session.userId)
        if (currentBetterMessagesInboxLastUpdate(session.userId) <= 0L) {
            try {
                discoverBetterMessagesInboxUpdates(
                    profileId = session.userId,
                    emitNativeNotifications = false,
                    lastUpdateOverride = 0L
                )
                recordPollingSuccess()
            } catch (error: Throwable) {
                recordPollingFailure(error)
                throw error
            }
            return@runCatching
        }
        try {
            discoverBetterMessagesInboxUpdates(session.userId, emitNativeNotifications = true)
            recordPollingSuccess()
        } catch (error: Throwable) {
            recordPollingFailure(error)
            throw error
        }
    }.mapFailureToUserFacing(appContext, R.string.error_load_notifications)

    override suspend fun getConversations(): Result<List<Conversation>> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.conversations
        } else {
            ensureRealConversationsPolling()
            val session = sessionManager.currentSession()
            if (session == null) {
                realConversations.value
            } else {
                restoreCachedConversationsIfNeeded(session.userId)
                realConversations.value.takeIf { it.isNotEmpty() } ?: refreshRealConversations()
            }
        }
    }.mapFailureToUserFacing(appContext, R.string.error_load_chats)

    override fun observeConversations(): Flow<List<Conversation>> {
        return if (AppConfig.USE_MOCK_BACKEND) {
            MockData.conversationsFlow
        } else {
            ensureRealConversationsPolling()
            flow {
                sessionManager.currentSession()?.let { restoreCachedConversationsIfNeeded(it.userId) }
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
            if (conversationId == AppDestinations.FavoriteMessagesConversationId) {
                emitAll(observeFavoriteMessages())
                return@flow
            }
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
            remote.observeProfiles()
                .map { profiles -> profiles.map { it.toUser() } }
                .catch { emit(emptyList()) }
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
                refreshBetterMessagesConversationAfterSuccessfulSend(session.userId, threadId)
            } else {
                val media = appContext.copyUriToCache(attachmentUri, attachmentName, attachmentMimeType)
                try {
                    val upload = betterMessagesRepository.uploadFile(session.userId, threadId, media.file, media.mimeType)
                    val fileId = upload.id ?: error(upload.error ?: "Better Messages no devolvio id de archivo")
                    val response = betterMessagesRepository.sendFiles(session.userId, threadId, listOf(fileId), text)
                    if (!response.result) error("Better Messages no pudo enviar el adjunto")
                    cacheBetterMessagesUpdate(threadId, response.update)
                    refreshBetterMessagesConversationAfterSuccessfulSend(session.userId, threadId)
                } finally {
                    media.delete()
                }
            }
            return@runCatching
        }

        val wallId = conversationId.wallIdOrNull() ?: error("Conversacion no reconocida")
        if (!attachmentUri.isNullOrBlank() && attachmentMimeType?.startsWith("image/") == true) {
            val media = mediaUploadOptimizer.prepareAttachmentUpload(attachmentUri, attachmentName, attachmentMimeType)
            val upload = remote.uploadCommunityChatImage(session.userId, media.bytes, media.extension, media.mimeType)
            val imageUrl = upload.publicUrl ?: error("Supabase no devolvio URL de imagen")
            remote.sendCommunityImageMessage(wallId, session.userId, imageUrl, media.fileName, media.mimeType, text)
            upsertConversationAfterLocalSend(
                conversationId = conversationId,
                preview = text.ifBlank { media.fileName }
            )
        } else {
            remote.sendCommunityMessage(wallId, session.userId, text.ifBlank { attachmentUri.orEmpty() })
            upsertConversationAfterLocalSend(
                conversationId = conversationId,
                preview = text.ifBlank { attachmentName ?: attachmentUri.orEmpty() }
            )
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
            refreshBetterMessagesConversationAfterSuccessfulSend(session.userId, threadId)
        } else {
            sendMessage(conversationId, text).getOrThrow()
        }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun sendSosMessage(contactIds: List<String>, text: String): Result<String> = runCatching {
        if (contactIds.isEmpty()) error(appContext.getString(R.string.error_sos_no_contacts))
        if (text.isBlank()) error(appContext.getString(R.string.error_sos_empty_message))
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val emergencyContactIds = contactIds
            .filterNot { it == session.userId }
            .distinct()
            .take(5)
        if (emergencyContactIds.isEmpty()) error(appContext.getString(R.string.error_sos_no_contacts))
        if (AppConfig.USE_MOCK_BACKEND) {
            return@runCatching MockData.addSosConversation(emergencyContactIds, text, session.userId, session.displayName)
        }
        val conversationId = resolveSosConversation(session.userId, emergencyContactIds)
        sendMessage(conversationId, text).getOrThrow()
        conversationId.betterMessagesThreadIdOrNull()
            ?.let { threadId -> loadBetterMessagesConversation(session.userId, threadId) }
            ?.let(::upsertRealConversation)
        conversationId
    }.mapFailureToUserFacing(appContext, R.string.sos_send_error)

    override suspend fun cachedPrivateConversationId(userId: String): String? {
        val session = sessionManager.currentSession() ?: return null
        if (AppConfig.USE_MOCK_BACKEND) {
            return MockData.conversations.cachedPrivateConversationId(session.userId, userId)
        }
        restoreCachedConversationsIfNeeded(session.userId)
        val conversationId = realConversations.value.cachedPrivateConversationId(session.userId, userId)
        conversationId
            ?.betterMessagesThreadIdOrNull()
            ?.let { threadId ->
                registerBetterMessagesThread(threadId)
                threadIdsByPeerProfileId[userId] = threadId
            }
        return conversationId
    }

    override suspend fun cachedCommunityConversationId(communityName: String): String? {
        val cleanCommunityName = communityName.trim()
        if (cleanCommunityName.isBlank()) return null
        val session = sessionManager.currentSession() ?: return null
        if (AppConfig.USE_MOCK_BACKEND) {
            return MockData.conversations.cachedCommunityConversationId(cleanCommunityName)
        }
        restoreCachedConversationsIfNeeded(session.userId)
        val conversationId = realConversations.value.cachedCommunityConversationId(cleanCommunityName)
        conversationId
            ?.betterMessagesThreadIdOrNull()
            ?.let { threadId ->
                markBetterMessagesGroupThread(session.userId, threadId)
                registerBetterMessagesThread(threadId)
            }
        return conversationId
    }

    override suspend fun openCommunityConversation(
        communityId: String,
        title: String,
        participantIds: List<String>
    ): Result<String> = runCatching {
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val peerIds = participantIds
            .filterNot { it == session.userId }
            .distinct()
        if (peerIds.isEmpty()) error(appContext.getString(R.string.error_group_chat_no_members))
        if (AppConfig.USE_MOCK_BACKEND) {
            return@runCatching MockData.findOrCreateGroupConversation(peerIds, session.userId, session.displayName, title)
        }
        cachedCommunityConversationId(title)?.let { return@runCatching it }
        resolveBetterMessagesCommunityConversation(
            profileId = session.userId,
            communityId = communityId,
            title = title,
            peerProfileIds = peerIds
        )
    }.mapFailureToUserFacing(appContext, R.string.error_load_chats)

    override suspend fun openGroupConversation(participantIds: List<String>, title: String?): Result<String> = runCatching {
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val peerIds = participantIds
            .filterNot { it == session.userId }
            .distinct()
        if (peerIds.isEmpty()) error(appContext.getString(R.string.error_group_chat_no_members))
        if (AppConfig.USE_MOCK_BACKEND) {
            return@runCatching MockData.findOrCreateGroupConversation(peerIds, session.userId, session.displayName, title)
        }
        resolveBetterMessagesGroupConversation(
            profileId = session.userId,
            peerProfileIds = peerIds,
            noPeersMessage = appContext.getString(R.string.error_group_chat_no_members),
            addPeersMessage = appContext.getString(R.string.error_group_chat_add_members)
        )
    }.mapFailureToUserFacing(appContext, R.string.error_load_chats)

    override suspend fun markConversationRead(conversationId: String): Result<Unit> = runCatching {
        notificationFactory.clearChatMessage(conversationId)
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
        persistCurrentConversations()
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
        persistCurrentConversations()
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
            persistCurrentConversations()
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
        removeConversationFromCache(session.userId, conversationId)
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
        removeConversationFromCache(session.userId, conversationId)
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
        removeFavoriteMessageFromCache(session.userId, messageId)
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun toggleFavoriteMessage(messageId: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.toggleFavoriteMessage(messageId)
            return@runCatching
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val (threadId, bmMessageId) = messageId.bmMessagePartsOrNull() ?: error("Mensaje no reconocido")
        val current = loadBetterMessages(threadId).firstOrNull { it.id == messageId }
        val nextFavorite = current?.isFavorite != true
        betterMessagesRepository.favoriteMessage(session.userId, threadId, bmMessageId, favorite = nextFavorite)
        updateBetterMessagesFavoriteState(threadId, bmMessageId, nextFavorite)
        current
            ?.copy(isFavorite = nextFavorite)
            ?.let { updateFavoriteMessageCacheAfterToggle(session.userId, it) }
        Unit
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
                val lastMessage = threadResponse?.messages?.maxByOrNull { it.betterMessagesSortMillis() }
                thread.toConversation(
                    title = peer?.displayName().orEmpty(),
                    peerProfileId = peerId,
                    peerName = peer?.displayName() ?: "Usuario",
                    peerAvatarUrl = peer?.avatar_url ?: peer?.avatar,
                    currentProfileId = session.userId
                ).copy(
                    lastMessagePreview = lastMessage?.message?.stripHtmlTagsAndDecode() ?: chat.last_message_preview.orEmpty().stripHtmlTagsAndDecode(),
                    unreadCount = unreadCount,
                    updatedAt = lastMessage?.betterMessagesDisplayTime() ?: chat.last_message_at.orEmpty(),
                    updatedAtMillis = lastMessage?.betterMessagesTimestampMillisOrNull()
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

    private suspend fun refreshRealConversations(): List<Conversation> =
        realConversationsLoadMutex.withLock {
            loadRealConversations().also { conversations ->
                setRealConversations(conversations)
            }
        }

    private suspend fun loadRealConversation(conversationId: String): Conversation? {
        val session = sessionManager.currentSession() ?: return null
        conversationId.betterMessagesThreadIdOrNull()?.let { threadId ->
            if (betterMessagesAbandonedConversationStore.isAbandoned(session.userId, threadId)) return null
            return loadBetterMessagesConversation(
                profileId = session.userId,
                threadId = threadId,
                allowProfileRemoteRefresh = false
            )
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

    private suspend fun loadBetterMessagesConversation(
        profileId: String,
        threadId: Int,
        preloadedThreadResponse: BmThreadResponse? = null,
        initializeNewThreadAsRead: Boolean = true,
        profileDirectory: List<CommunityProfile>? = null,
        allowProfileRemoteRefresh: Boolean = true
    ): Conversation? {
        if (betterMessagesAbandonedConversationStore.isAbandoned(profileId, threadId)) return null
        val context = ensureBetterMessagesSession(profileId)
        val threadResponse = preloadedThreadResponse
            ?: cachedBetterMessagesThreadResponse(threadId).takeIf { it.hasContentForThread(threadId) }
            ?: betterMessagesConversationCacheStore.cachedThreadResponse(profileId, threadId)
                ?.also { cacheBetterMessagesThreadResponse(threadId, it, persist = false) }
            ?: loadBetterMessagesThread(profileId, threadId)
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
            currentWpUserId = currentWpUserId,
            initializeNewThreadAsRead = initializeNewThreadAsRead
        )
        val effectiveUnreadCount = if (threadResponse.messages.isEmpty()) {
            maxOf(unreadCount, thread.unread ?: 0)
        } else {
            unreadCount
        }
        val profiles = profileDirectory
            ?.let { (it + listOfNotNull(context.profile)).distinctBy { profile -> profile.id } }
            ?: betterMessagesProfileDirectory(
                contextProfile = context.profile,
                allowRemoteRefresh = allowProfileRemoteRefresh
            )
        if (thread.type == "group" || thread.type == BETTER_MESSAGES_WALL_TYPE) {
            markBetterMessagesGroupThread(profileId, threadId)
        }
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
            unreadCount = effectiveUnreadCount
        )
    }

    private fun upsertRealConversation(
        conversation: Conversation,
        emitNativeNotification: Boolean = false
    ) {
        val previous = realConversations.value.firstOrNull { it.id == conversation.id }
        conversation.id.betterMessagesThreadIdOrNull()?.let(::registerBetterMessagesThread)
        realConversations.value = (listOf(conversation) + realConversations.value.filterNot { it.id == conversation.id })
            .distinctBy { it.id }
            .sortedByDescending { it.updatedAtMillis ?: 0L }
        if (emitNativeNotification && shouldEmitNativeNotifications() && shouldNotifyNative(previous, conversation)) {
            notificationFactory.showChatMessage(conversation)
        }
        persistConversation(conversation)
    }

    private suspend fun refreshBetterMessagesConversationAfterSuccessfulSend(profileId: String, threadId: Int) {
        val threadResponse = runCatching { loadBetterMessagesThread(profileId, threadId) }
            .getOrElse { cachedBetterMessagesThreadResponse(threadId) }
        val conversation = loadBetterMessagesConversation(
            profileId = profileId,
            threadId = threadId,
            preloadedThreadResponse = threadResponse,
            initializeNewThreadAsRead = true,
            allowProfileRemoteRefresh = false
        ) ?: return
        upsertRealConversation(conversation)
    }

    private fun upsertConversationAfterLocalSend(conversationId: String, preview: String) {
        val cleanPreview = preview.stripHtmlTagsAndDecode()
        val now = System.currentTimeMillis()
        val current = realConversations.value.firstOrNull { it.id == conversationId } ?: return
        upsertRealConversation(
            current.copy(
                lastMessagePreview = cleanPreview,
                updatedAtMillis = now
            )
        )
    }

    private fun setRealConversations(conversations: List<Conversation>) {
        conversations.forEach { conversation ->
            conversation.id.betterMessagesThreadIdOrNull()?.let(::registerBetterMessagesThread)
        }
        realConversations.value = conversations.withActiveConversationPreserved()
        persistCurrentConversations()
    }

    private suspend fun restoreCachedConversationsIfNeeded(profileId: String) {
        if (realConversationsLoadedForProfileId == profileId && realConversations.value.isNotEmpty()) return
        val cachedConversations = betterMessagesConversationCacheStore.cachedConversations(profileId)
        if (cachedConversations.isEmpty()) return
        cachedConversations.forEach { conversation ->
            conversation.id.betterMessagesThreadIdOrNull()?.let { threadId ->
                registerBetterMessagesThread(threadId)
                if (!cachedBetterMessagesThreadResponse(threadId).hasContentForThread(threadId)) {
                    betterMessagesConversationCacheStore.cachedThreadResponse(profileId, threadId)
                        ?.let { cacheBetterMessagesThreadResponse(threadId, it, persist = false) }
                }
            }
        }
        realConversations.value = cachedConversations.withActiveConversationPreserved()
        realConversationsLoadedForProfileId = profileId
        realConversationsLastRefreshedAt = 0L
    }

    private fun persistCurrentConversations() {
        val profileId = sessionManager.currentSession()?.userId ?: return
        val conversations = realConversations.value
        realConversationsScope.launch {
            betterMessagesConversationCacheStore.replaceConversations(profileId, conversations)
        }
    }

    private fun persistConversation(conversation: Conversation) {
        val profileId = sessionManager.currentSession()?.userId ?: return
        realConversationsScope.launch {
            betterMessagesConversationCacheStore.upsertConversation(profileId, conversation)
        }
    }

    private fun removeConversationFromCache(profileId: String, conversationId: String) {
        favoriteMessagesByProfileId[profileId]?.let { state ->
            state.value = state.value.filterNot { it.conversationId == conversationId }
        }
        realConversationsScope.launch {
            betterMessagesConversationCacheStore.removeConversation(profileId, conversationId)
            betterMessagesConversationCacheStore.removeFavoriteMessagesForConversation(profileId, conversationId)
        }
    }

    private fun logPolling(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(POLLING_TAG, message)
        }
    }

    private fun registerBetterMessagesThread(threadId: Int, forceDue: Boolean = false) {
        if (threadId > 0) {
            betterMessagesRegisteredThreadIds += threadId
        }
        if (forceDue) {
            wakePolling()
        }
    }

    private fun recordPollingSuccess() {
        isBetterMessagesPollingOnline = true
        _isPollingOnline.value = true
    }

    private fun recordPollingFailure(error: Throwable) {
        logPolling("offline: ${error::class.java.simpleName}")
        isBetterMessagesPollingOnline = false
        _isPollingOnline.value = false
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

    private fun shouldNotifyNative(previous: Conversation?, updated: Conversation): Boolean {
        if (updated.isMuted) return false
        if (appForegroundState.value && updated.id == activeConversationId.value) return false
        return updated.unreadCount > (previous?.unreadCount ?: 0)
    }

    private fun shouldEmitNativeNotifications(): Boolean =
        !appForegroundState.value

    private suspend fun hasBetterMessagesMessageCache(profileId: String): Boolean =
        betterMessagesByThreadId.values.any { messages -> messages.isNotEmpty() } ||
            betterMessagesConversationCacheStore.hasCachedThreadMessages(profileId)

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

    private fun List<Conversation>.cachedPrivateConversationId(
        currentUserId: String,
        peerUserId: String
    ): String? {
        val expectedParticipants = setOf(currentUserId, peerUserId)
        return firstOrNull { conversation ->
            conversation.isVisible &&
                !conversation.isGroup &&
                !conversation.isEmergency &&
                conversation.id.betterMessagesThreadIdOrNull() != null &&
                conversation.participantIds.toSet() == expectedParticipants
        }?.id
    }

    private fun List<Conversation>.cachedCommunityConversationId(communityName: String): String? {
        val normalizedCommunityName = communityName.normalizeName()
        return firstOrNull { conversation ->
            conversation.isVisible &&
                conversation.isGroup &&
                !conversation.isEmergency &&
                listOfNotNull(conversation.communityName, conversation.title)
                    .any { it.normalizeName() == normalizedCommunityName }
        }?.id
    }

    private fun ensureRealConversationsPolling() {
        if (AppConfig.USE_MOCK_BACKEND || realConversationsPollJob?.isActive == true) return
        realConversationsPollJob = realConversationsScope.launch {
            logPolling("loop started")
            var lastInboxRefreshAt = 0L
            var lastInboxDiscoveryAt = 0L
            while (true) {
                val session = sessionManager.currentSession()
                if (session == null) {
                    logPolling("no session; sleeping")
                    realConversations.value = emptyList()
                    lastInboxRefreshAt = 0L
                    lastInboxDiscoveryAt = 0L
                    betterMessagesInboxLastUpdate = 0L
                    realConversationsLoadedForProfileId = null
                    realConversationsLastRefreshedAt = 0L
                    betterMessagesRegisteredThreadIds.clear()
                    isBetterMessagesPollingOnline = null
                    _isPollingOnline.value = true
                    waitForNextPoll(CONVERSATIONS_POLL_DELAY_MILLIS)
                    continue
                }

                val now = System.currentTimeMillis()
                val mode = pollingMode.value
                if (realConversationsLoadedForProfileId != null && realConversationsLoadedForProfileId != session.userId) {
                    betterMessagesInboxLastUpdate = 0L
                    betterMessagesRegisteredThreadIds.clear()
                    isBetterMessagesPollingOnline = null
                    _isPollingOnline.value = true
                }
                restoreCachedConversationsIfNeeded(session.userId)
                if (!deviceNetworkAvailable.value) {
                    logPolling("tick skipped: device network unavailable")
                    waitForNextPoll(CONVERSATIONS_POLL_DELAY_MILLIS)
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
                val hasInboxCursor = currentBetterMessagesInboxLastUpdate(session.userId) > 0L
                logPolling(
                    "tick mode=$mode conversations=${realConversations.value.size} " +
                        "hasCursor=$hasInboxCursor activeThread=$activeBetterMessagesThreadId " +
                        "lastDiscoveryAge=${now - lastInboxDiscoveryAt}"
                )

                if (!hasInboxCursor) {
                    logPolling("bootstrap checkNew")
                    runCatching {
                        discoverBetterMessagesInboxUpdates(
                            profileId = session.userId,
                            emitNativeNotifications = false,
                            lastUpdateOverride = 0L
                        )
                    }
                        .onSuccess {
                            recordPollingSuccess()
                            lastInboxDiscoveryAt = System.currentTimeMillis()
                        }
                        .onFailure(::recordPollingFailure)
                } else {
                    if (now - lastInboxDiscoveryAt >= inboxDiscoveryIntervalMillis(mode)) {
                        logPolling("discovery checkNew mode=$mode")
                        runCatching {
                            discoverBetterMessagesInboxUpdates(
                                profileId = session.userId,
                                emitNativeNotifications = shouldEmitNativeNotifications()
                            )
                        }
                            .onSuccess {
                                recordPollingSuccess()
                                lastInboxDiscoveryAt = System.currentTimeMillis()
                            }
                            .onFailure { error ->
                                recordPollingFailure(error)
                                logPolling("checkNew failed: ${error::class.java.simpleName}")
                                val interval = inboxDiscoveryIntervalMillis(mode)
                                val retryDelay = discoveryFailureRetryMillis(mode)
                                lastInboxDiscoveryAt = System.currentTimeMillis() -
                                    (interval - retryDelay).coerceAtLeast(0L)
                            }
                    }
                }
                activeConversationId.value
                    ?.betterMessagesThreadIdOrNull()
                    ?.let { registerBetterMessagesThread(it) }

                waitForNextPoll()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun waitForNextPoll(delayMillis: Long = nextPollingDelayMillis()) {
        select {
            pollingWakeSignal.onReceiveCatching { }
            onTimeout(delayMillis) { }
        }
    }

    private fun wakePolling() {
        pollingWakeSignal.trySend(Unit)
    }

    private suspend fun currentBetterMessagesInboxLastUpdate(profileId: String): Long {
        if (betterMessagesInboxLastUpdate > 0L) return betterMessagesInboxLastUpdate
        return betterMessagesPollingStateStore.lastInboxUpdate(profileId)
            .also { betterMessagesInboxLastUpdate = it }
    }

    private suspend fun updateBetterMessagesInboxLastUpdate(profileId: String, value: Long) {
        if (value <= 0L) return
        if (value > betterMessagesInboxLastUpdate) {
            betterMessagesInboxLastUpdate = value
        }
        betterMessagesPollingStateStore.updateLastInboxUpdate(profileId, value)
    }

    private suspend fun discoverBetterMessagesInboxUpdates(
        profileId: String,
        emitNativeNotifications: Boolean,
        lastUpdateOverride: Long? = null
    ) {
        logPolling("checkNew start native=$emitNativeNotifications")
        val context = ensureBetterMessagesSession(profileId)
        val activeThreadId = activeConversationId.value?.betterMessagesThreadIdOrNull()
        val lastInboxUpdate = lastUpdateOverride ?: currentBetterMessagesInboxLastUpdate(profileId)
        val initialMessageBootstrap = !hasBetterMessagesMessageCache(profileId)
        val response = withBetterMessagesSession(profileId) {
            betterMessagesRepository.checkNewInCurrentSession(
                lastUpdate = lastInboxUpdate,
                visibleThreads = activeThreadId
                    ?.takeIf { appForegroundState.value }
                    ?.let(::listOf)
                    .orEmpty(),
                threadIds = emptyList()
            )
        }
        updateBetterMessagesInboxLastUpdate(
            profileId = profileId,
            value = response.currentTime
            ?: response.messages.maxOfOrNull { it.updated_at ?: it.created_at }
            ?: response.threads.maxOfOrNull { it.lastTime ?: 0L }
            ?: lastInboxUpdate
        )

        val messagesByThreadId = response.messages.groupBy { it.threadId }
        val threadsById = response.threads.associateBy { it.threadId }
        val changedThreadIds = (threadsById.keys + messagesByThreadId.keys)
            .filter { it > 0 }
            .distinct()
            .filter { threadId ->
                hasBetterMessagesCheckNewDelta(
                    threadId = threadId,
                    thread = threadsById[threadId],
                    messages = messagesByThreadId[threadId].orEmpty()
                )
            }
        logPolling("checkNew changedThreads=${changedThreadIds.size} initialBootstrap=$initialMessageBootstrap")
        if (changedThreadIds.isEmpty()) return

        val abandonedThreadIds = betterMessagesAbandonedConversationStore.abandonedThreadIds(profileId)
        val profileDirectory = betterMessagesProfileDirectory(
            contextProfile = context.profile,
            allowRemoteRefresh = false
        )
        changedThreadIds
            .filterNot { it in abandonedThreadIds }
            .forEach { threadId ->
                val wasKnown = realConversations.value.any { it.id.betterMessagesThreadIdOrNull() == threadId }
                val thread = threadsById[threadId]
                if (!cachedBetterMessagesThreadResponse(threadId).hasContentForThread(threadId)) {
                    betterMessagesConversationCacheStore.cachedThreadResponse(profileId, threadId)
                        ?.let { cacheBetterMessagesThreadResponse(threadId, it, persist = false) }
                }
                val threadResponse = cacheBetterMessagesThreadResponse(
                    threadId = threadId,
                    response = BmThreadResponse(
                        threads = listOfNotNull(thread),
                        users = response.users,
                        messages = messagesByThreadId[threadId].orEmpty(),
                        serverTime = response.currentTime
                    )
                )
                    .let {
                        val knownMessageIds = betterMessagesByThreadId[threadId].orEmpty()
                            .map { message -> message.messageId }
                            .distinct()
                        loadBetterMessagesThread(profileId, threadId, knownMessageIds)
                    }
                if (thread?.type == "group" || thread?.type == BETTER_MESSAGES_WALL_TYPE) {
                    markBetterMessagesGroupThread(profileId, threadId)
                }
                loadBetterMessagesConversation(
                    profileId = profileId,
                    threadId = threadId,
                    preloadedThreadResponse = threadResponse,
                    initializeNewThreadAsRead = initialMessageBootstrap ||
                        wasKnown ||
                        (appForegroundState.value && threadId == activeThreadId),
                    profileDirectory = profileDirectory
                )
                    ?.let { conversation ->
                        val displayedConversation = if (initialMessageBootstrap) {
                            conversation.copy(unreadCount = 0)
                        } else {
                            conversation
                        }
                        upsertRealConversation(
                            conversation = displayedConversation,
                            emitNativeNotification = emitNativeNotifications && !initialMessageBootstrap
                        )
                    }
                if (!wasKnown) {
                    registerBetterMessagesThread(threadId)
                }
            }
    }

    private fun hasBetterMessagesCheckNewDelta(
        threadId: Int,
        thread: BmThread?,
        messages: List<BmMessage>
    ): Boolean {
        val cachedThread = betterMessagesThreadsById[threadId]
        val cachedMessages = betterMessagesByThreadId[threadId].orEmpty()
        if (cachedThread == null && cachedMessages.isEmpty()) return true

        val incomingLastTime = thread?.lastTime
        val cachedLastTime = cachedThread?.lastTime ?: 0L
        if (incomingLastTime != null && incomingLastTime > cachedLastTime) return true

        if (thread?.unread != null && thread.unread != cachedThread?.unread) return true
        if (thread?.isMuted != null && thread.isMuted != cachedThread?.isMuted) return true
        if (thread?.isHidden != null && thread.isHidden != cachedThread?.isHidden) return true
        if (thread?.isDeleted != null && thread.isDeleted != cachedThread?.isDeleted) return true

        if (messages.isEmpty()) return false
        val cachedMessagesById = cachedMessages.associateBy { it.messageId }
        return messages.any { message ->
            val cached = cachedMessagesById[message.messageId] ?: return@any true
            (message.updated_at ?: message.created_at) > (cached.updated_at ?: cached.created_at)
        }
    }

    private suspend fun loadBetterMessagesInbox(
        profileId: String,
        privateThreadIds: Set<Int> = emptySet()
    ): List<Conversation> {
        val context = ensureBetterMessagesSession(profileId)
        val inbox = withBetterMessagesSession(profileId) {
            betterMessagesRepository.checkNewInCurrentSession(lastUpdate = 0)
        }
        updateBetterMessagesInboxLastUpdate(profileId, inbox.currentTime ?: betterMessagesInboxLastUpdate)
        val currentWpUserId = inferCurrentWpUserId(
            profile = context.profile,
            users = inbox.users,
            fallbackUserId = context.currentWpUserId
        )
        val messagesByThreadId = inbox.messages.groupBy { it.threadId }
        val abandonedThreadIds = betterMessagesAbandonedConversationStore.abandonedThreadIds(profileId)
        val activeThreadId = activeConversationId.value?.betterMessagesThreadIdOrNull()
        val profiles = betterMessagesProfileDirectory(context.profile)
        return inbox.threads
            .filter { thread -> thread.threadId > 0 && thread.isHidden != 1 && thread.isDeleted != 1 }
            .filterNot { thread -> thread.threadId in abandonedThreadIds }
            .filterNot { thread -> appForegroundState.value && thread.threadId == activeThreadId }
            .filter { thread -> currentWpUserId == null || currentWpUserId in thread.participants }
            .mapNotNull { thread ->
                val knownMessages = messagesByThreadId[thread.threadId].orEmpty()
                val threadResponse = cacheBetterMessagesThreadResponse(
                    threadId = thread.threadId,
                    response = BmThreadResponse(
                        threads = listOf(thread),
                        users = inbox.users,
                        messages = knownMessages,
                        serverTime = inbox.currentTime
                    )
                )
                val fullThread = threadResponse?.threads?.firstOrNull { it.threadId == thread.threadId } ?: thread
                if (fullThread.type == "group" || fullThread.type == BETTER_MESSAGES_WALL_TYPE) {
                    markBetterMessagesGroupThread(profileId, fullThread.threadId)
                }
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
                val lastMessage = threadMessages.maxByOrNull { it.betterMessagesSortMillis() }
                val unreadCount = betterMessagesReadStateStore.unreadCount(
                    profileId = profileId,
                    threadId = fullThread.threadId,
                    messages = threadMessages,
                    currentWpUserId = currentWpUserId
                )
                val title = fullThread.subject?.takeIf { it.isNotBlank() }
                    ?: fullThread.title?.takeIf { it.isNotBlank() && it != "${fullThread.participantsCount ?: fullThread.participants.size} participantes" }
                    ?: participantNames.joinToString(", ").ifBlank { "Chat ${fullThread.threadId}" }
                val isKnownPrivateThread = fullThread.threadId in privateThreadIds
                val isGroupConversation = fullThread.isGroupConversation(
                    forceGroup = isKnownBetterMessagesGroupThread(profileId, fullThread.threadId),
                    isKnownPrivateThread = isKnownPrivateThread
                )
                val isSosConversation = title.isSosThreadTitle() || fullThread.subject?.isSosThreadTitle() == true
                if ((isGroupConversation || isSosConversation) && !isKnownPrivateThread) {
                    markBetterMessagesGroupThread(profileId, fullThread.threadId)
                }
                Conversation(
                    id = betterMessagesConversationId(fullThread.threadId),
                    title = title,
                    avatarUrl = fullThread.image?.takeIf { it.isNotBlank() } ?: peerUsers.firstOrNull()?.avatar,
                    lastMessagePreview = lastMessage?.message?.stripHtmlTagsAndDecode().orEmpty(),
                    unreadCount = unreadCount,
                    updatedAt = lastMessage?.betterMessagesDisplayTime() ?: fullThread.lastTime?.toDisplayTime().orEmpty(),
                    updatedAtMillis = lastMessage?.betterMessagesTimestampMillisOrNull()
                        ?: fullThread.lastTime?.toEpochMillisFromBetterMessagesOrNull(),
                    participantIds = resolvedParticipants.map { it.profileId },
                    participantNames = resolvedParticipants.map { it.name },
                    participantAvatarUrls = resolvedParticipants.map { it.avatarUrl },
                    isGroup = isGroupConversation || isSosConversation,
                    isEmergency = isSosConversation,
                    isMuted = fullThread.isMuted == true,
                    isVisible = true,
                    moderatorIds = resolvedParticipants
                        .filter { it.wpUserId in fullThread.moderators }
                        .map { it.profileId },
                    canMembersInvite = fullThread.meta?.allowInvite == true || fullThread.permissions?.canInvite == true
                )
            }
    }

    private suspend fun betterMessagesProfileDirectory(
        contextProfile: CommunityProfile?,
        allowRemoteRefresh: Boolean = true
    ): List<CommunityProfile> {
        contextProfile?.let { betterMessagesProfilesById[it.id] = it }
        val profileId = contextProfile?.id ?: sessionManager.currentSession()?.userId
        val now = System.currentTimeMillis()
        val memoryProfiles = betterMessagesProfilesById.values
            .plus(listOfNotNull(contextProfile))
            .distinctBy { it.id }
        if (memoryProfiles.isNotEmpty() && now - betterMessagesProfilesLoadedAt <= PROFILE_CACHE_RETENTION_MILLIS) {
            return memoryProfiles
        }
        profileId?.let { id ->
            val cachedProfiles = betterMessagesConversationCacheStore.cachedProfileDirectory(id)
            if (cachedProfiles.isNotEmpty()) {
                cachedProfiles.forEach { betterMessagesProfilesById[it.id] = it }
                contextProfile?.let { betterMessagesProfilesById[it.id] = it }
                val isExpired = betterMessagesConversationCacheStore.isProfileDirectoryExpired(id)
                if (!allowRemoteRefresh || !isExpired) {
                    betterMessagesProfilesLoadedAt = now
                    return (cachedProfiles + listOfNotNull(contextProfile)).distinctBy { it.id }
                }
            }
        }
        if (!allowRemoteRefresh) {
            return memoryProfiles
        }
        return betterMessagesProfilesMutex.withLock {
            val lockedNow = System.currentTimeMillis()
            val lockedMemoryProfiles = betterMessagesProfilesById.values
                .plus(listOfNotNull(contextProfile))
                .distinctBy { it.id }
            if (lockedMemoryProfiles.isNotEmpty() && lockedNow - betterMessagesProfilesLoadedAt <= PROFILE_CACHE_RETENTION_MILLIS) {
                return@withLock lockedMemoryProfiles
            }
            val loadedProfiles = runCatching { remote.getProfiles() }.getOrDefault(emptyList())
            val mergedProfiles = (loadedProfiles + listOfNotNull(contextProfile)).distinctBy { it.id }
            mergedProfiles.forEach { betterMessagesProfilesById[it.id] = it }
            betterMessagesProfilesLoadedAt = lockedNow
            profileId?.let { id ->
                betterMessagesConversationCacheStore.replaceProfileDirectory(id, mergedProfiles)
            }
            mergedProfiles
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
        ensureRealConversationsPolling()
        registerBetterMessagesThread(threadId, forceDue = true)
        if (betterMessagesByThreadId[threadId].isNullOrEmpty()) {
            betterMessagesConversationCacheStore.cachedThreadResponse(session.userId, threadId)
                ?.let { cacheBetterMessagesThreadResponse(threadId, it, persist = false) }
                ?: if (deviceNetworkAvailable.value) {
                    runCatching { loadBetterMessagesThread(session.userId, threadId) }.getOrNull()
                } else {
                    null
                }
        }
        emitAll(
            betterMessagesThreadState(threadId)
                .map { response -> response.toDomainMessages(session.userId) }
        )
    }

    private suspend fun loadRealMessages(conversationId: String): List<Message> {
        if (conversationId == AppDestinations.FavoriteMessagesConversationId) {
            val session = sessionManager.currentSession() ?: return emptyList()
            return loadFavoriteMessages(session.userId)
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
        val cached = cachedBetterMessagesThreadResponse(threadId).takeIf { it.hasContentForThread(threadId) }
            ?: betterMessagesConversationCacheStore.cachedThreadResponse(session.userId, threadId)
                ?.let { cacheBetterMessagesThreadResponse(threadId, it, persist = false) }
        if (cached != null) return cached.toDomainMessages(session.userId)
        if (!deviceNetworkAvailable.value) return emptyList()
        return loadBetterMessagesThread(session.userId, threadId).toDomainMessages(session.userId)
    }

    private fun observeFavoriteMessages(): Flow<List<Message>> = flow {
        val session = sessionManager.currentSession()
        if (session == null) {
            emit(emptyList())
            return@flow
        }
        val state = favoriteMessagesState(session.userId)
        if (session.userId !in favoriteMessagesLoadedProfileIds) {
            state.value = loadFavoriteMessages(session.userId)
            favoriteMessagesLoadedProfileIds += session.userId
        }
        emitAll(state)
    }

    private suspend fun loadFavoriteMessages(profileId: String): List<Message> {
        betterMessagesConversationCacheStore.cachedFavoriteMessages(profileId)?.let { cached ->
            return cached.sortedFavoriteMessages()
        }
        val favorites = loadFavoriteMessagesFromServer(profileId)
        betterMessagesConversationCacheStore.replaceFavoriteMessages(profileId, favorites)
        return favorites
    }

    private suspend fun loadFavoriteMessagesFromServer(profileId: String): List<Message> {
        val startedAt = System.currentTimeMillis()
        val favoritedResponse = withBetterMessagesSession(profileId) {
            betterMessagesRepository.loadFavoritedInCurrentSession()
        }.let { response ->
            response.copy(messages = response.messages.map { message ->
                if (message.favorited == 1) message else message.copy(favorited = 1)
            })
        }
        val visibleThreadIds = favoritedResponse.threads
            .filter { thread -> thread.isHidden != 1 && thread.isDeleted != 1 }
            .map { thread -> thread.threadId }
            .toSet()
        val effectiveThreadIds = if (favoritedResponse.threads.isNotEmpty()) {
            visibleThreadIds
        } else {
            favoritedResponse.messages.map { it.threadId }.toSet()
        }
        val profileDirectory = betterMessagesSession
            ?.profile
            ?.let { betterMessagesProfileDirectory(it) }
        val threadMetadataResponse = favoritedResponse.copy(messages = emptyList())
        favoritedResponse.threads
            .filter { thread -> thread.threadId in effectiveThreadIds }
            .filter { thread -> realConversations.value.none { it.id == betterMessagesConversationId(thread.threadId) } }
            .forEach { thread ->
                loadBetterMessagesConversation(
                    profileId = profileId,
                    threadId = thread.threadId,
                    preloadedThreadResponse = threadMetadataResponse,
                    profileDirectory = profileDirectory
                )?.let(::upsertRealConversation)
            }

        val favorites = favoritedResponse
            .copy(messages = favoritedResponse.messages.filter { it.threadId in effectiveThreadIds })
            .toDomainMessages(profileId)
            .filter { it.isFavorite && !it.isDeleted }
            .distinctBy { it.id }
            .sortedFavoriteMessages()
        logPolling("favorites getFavorited messages=${favorites.size} threads=${effectiveThreadIds.size} duration=${System.currentTimeMillis() - startedAt}ms")
        return favorites
    }

    private fun favoriteMessagesState(profileId: String): MutableStateFlow<List<Message>> =
        favoriteMessagesByProfileId.getOrPut(profileId) { MutableStateFlow(emptyList()) }

    private suspend fun updateFavoriteMessageCacheAfterToggle(profileId: String, message: Message) {
        if (!betterMessagesConversationCacheStore.hasFavoriteMessagesCache(profileId)) return
        favoriteMessagesByProfileId[profileId]?.let { state ->
            state.value = state.value.applyFavoriteMessageChange(message)
        }
        if (message.isFavorite && !message.isDeleted) {
            betterMessagesConversationCacheStore.upsertFavoriteMessage(profileId, message)
        } else {
            betterMessagesConversationCacheStore.removeFavoriteMessage(profileId, message.id)
        }
    }

    private fun removeFavoriteMessageFromCache(profileId: String, messageId: String) {
        favoriteMessagesByProfileId[profileId]?.let { state ->
            state.value = state.value.filterNot { it.id == messageId }
        }
        realConversationsScope.launch {
            betterMessagesConversationCacheStore.removeFavoriteMessage(profileId, messageId)
        }
    }

    private fun updateBetterMessagesFavoriteState(threadId: Int, messageId: Int, favorite: Boolean) {
        val currentMessages = betterMessagesByThreadId[threadId].orEmpty()
        if (currentMessages.none { it.messageId == messageId }) return
        betterMessagesByThreadId[threadId] = currentMessages.map { message ->
            if (message.messageId == messageId) {
                message.copy(favorited = if (favorite) 1 else 0)
            } else {
                message
            }
        }
        val response = cachedBetterMessagesThreadResponse(threadId)
        betterMessagesThreadState(threadId).value = response
        persistThreadResponse(threadId, response)
    }

    private fun List<Message>.applyFavoriteMessageChange(message: Message): List<Message> =
        if (message.isFavorite && !message.isDeleted) {
            (listOf(message) + filterNot { it.id == message.id })
                .distinctBy { it.id }
                .sortedFavoriteMessages()
        } else {
            filterNot { it.id == message.id }
        }

    private fun List<Message>.sortedFavoriteMessages(): List<Message> =
        sortedWith(
            compareByDescending<Message> { it.sentAtMillis ?: 0L }
                .thenByDescending { it.id }
        )

    private suspend fun loadBetterMessagesThread(
        profileId: String,
        threadId: Int,
        knownMessageIds: List<Int> = emptyList()
    ): BmThreadResponse {
        return withBetterMessagesSession(profileId) {
            betterMessagesRepository.loadThreadInCurrentSession(threadId, knownMessageIds)
        }.let { response -> cacheBetterMessagesThreadResponse(threadId, response) }
    }

    private fun cacheBetterMessagesUpdate(threadId: Int, update: BmThreadResponse?) {
        update?.let { cacheBetterMessagesThreadResponse(threadId, it) }
        registerBetterMessagesThread(threadId, forceDue = true)
    }

    private fun cacheBetterMessagesThreadResponse(
        threadId: Int,
        response: BmThreadResponse,
        persist: Boolean = true
    ): BmThreadResponse {
        registerBetterMessagesThread(threadId)
        response.threads
            .firstOrNull { it.threadId == threadId }
            ?.let { betterMessagesThreadsById[threadId] = it }

        val mergedUsers = (betterMessagesUsersByThreadId[threadId].orEmpty() + response.users)
            .distinctBy { user -> user.userId?.toString() ?: user.id }
        if (mergedUsers.isNotEmpty()) {
            betterMessagesUsersByThreadId[threadId] = mergedUsers
        }

        val responseMessages = response.messages.filter { it.threadId == threadId }
        val mergedMessages = (betterMessagesByThreadId[threadId].orEmpty() + responseMessages)
            .groupBy { it.messageId }
            .map { (_, versions) ->
                versions.maxBy { message -> message.updated_at ?: message.created_at }
            }
            .sortedByBetterMessagesTime()
        if (mergedMessages.isNotEmpty()) {
            betterMessagesByThreadId[threadId] = mergedMessages
        }

        val cachedResponse = cachedBetterMessagesThreadResponse(threadId).let { cached ->
            cached.copy(
                serverTime = response.serverTime ?: cached.serverTime
            )
        }
        betterMessagesThreadState(threadId).value = cachedResponse
        if (persist) {
            persistThreadResponse(threadId, cachedResponse)
        }
        return cachedResponse
    }

    private fun cachedBetterMessagesThreadResponse(threadId: Int): BmThreadResponse =
        BmThreadResponse(
            threads = listOfNotNull(betterMessagesThreadsById[threadId]),
            users = betterMessagesUsersByThreadId[threadId].orEmpty(),
            messages = betterMessagesByThreadId[threadId].orEmpty()
        )

    private fun BmThreadResponse.hasContentForThread(threadId: Int): Boolean =
        threads.any { it.threadId == threadId } ||
            messages.any { it.threadId == threadId }

    private fun persistThreadResponse(threadId: Int, response: BmThreadResponse) {
        val profileId = sessionManager.currentSession()?.userId ?: return
        realConversationsScope.launch {
            betterMessagesConversationCacheStore.upsertThreadResponse(profileId, threadId, response)
        }
    }

    private fun betterMessagesThreadState(threadId: Int): MutableStateFlow<BmThreadResponse> =
        betterMessagesThreadResponses.getOrPut(threadId) {
            MutableStateFlow(cachedBetterMessagesThreadResponse(threadId))
        }

    private suspend fun resolveSosConversation(profileId: String, contactProfileIds: List<String>): String {
        val context = ensureBetterMessagesSession(profileId)
        val currentWpUserId = context.currentWpUserId ?: error(appContext.getString(R.string.error_chat_session))
        val peerIds = contactProfileIds
            .filterNot { it == profileId }
            .distinct()
        val peerWpUserIds = peerIds
            .map { peerProfileId -> peerProfileId.toBetterMessagesUserId() }
            .distinct()
        if (peerWpUserIds.isEmpty()) error(appContext.getString(R.string.error_sos_no_contacts))

        val targetWpUserIds = (listOf(currentWpUserId) + peerWpUserIds).distinct()
        findBetterMessagesThreadByParticipants(profileId, targetWpUserIds)?.let { threadId ->
            betterMessagesAbandonedConversationStore.clearAbandoned(profileId, threadId)
            markBetterMessagesGroupThread(profileId, threadId)
            runCatching { betterMessagesRepository.changeSubject(profileId, threadId, SOS_THREAD_TITLE) }
            if (runCatching { ensureBetterMessagesParticipants(profileId, threadId, peerWpUserIds) }.isSuccess) {
                return betterMessagesConversationId(threadId)
            }
        }

        return runCatching {
            val threadId = createBetterMessagesThread(
                profileId = profileId,
                recipientWpUserIds = peerWpUserIds,
                subject = SOS_THREAD_TITLE,
                initialUniqueKey = "quata-sos:${targetWpUserIds.sorted().joinToString("-")}",
                fallbackThreadLookup = { findBetterMessagesThreadByParticipants(profileId, targetWpUserIds) }
            )
            betterMessagesAbandonedConversationStore.clearAbandoned(profileId, threadId)
            markBetterMessagesGroupThread(profileId, threadId)
            ensureBetterMessagesParticipants(profileId, threadId, peerWpUserIds)
            betterMessagesConversationId(threadId)
        }.getOrElse {
            resolveSosFromPrivateBase(
                profileId = profileId,
                peerProfileIds = peerIds,
                peerWpUserIds = peerWpUserIds
            )
        }
    }

    private suspend fun resolveSosFromPrivateBase(
        profileId: String,
        peerProfileIds: List<String>,
        peerWpUserIds: List<Int>
    ): String {
        val firstPeerId = peerProfileIds.firstOrNull() ?: error(appContext.getString(R.string.error_sos_no_contacts))
        val threadId = resolvePrivateThreadId(profileId, firstPeerId)
            ?: error(appContext.getString(R.string.error_chat_thread_missing))
        betterMessagesAbandonedConversationStore.clearAbandoned(profileId, threadId)
        runCatching { betterMessagesRepository.restoreThread(profileId, threadId) }
        runCatching { betterMessagesRepository.changeSubject(profileId, threadId, SOS_THREAD_TITLE) }
        if (peerWpUserIds.size > 1) {
            val added = runCatching { ensureBetterMessagesParticipants(profileId, threadId, peerWpUserIds) }
            if (added.isFailure) error(appContext.getString(R.string.error_sos_add_contacts))
        }
        markBetterMessagesGroupThread(profileId, threadId)
        return betterMessagesConversationId(threadId)
    }

    private suspend fun resolveBetterMessagesCommunityConversation(
        profileId: String,
        communityId: String,
        title: String,
        peerProfileIds: List<String>
    ): String {
        val context = ensureBetterMessagesSession(profileId)
        val currentWpUserId = context.currentWpUserId ?: error(appContext.getString(R.string.error_chat_session))
        val peerIds = peerProfileIds
            .filterNot { it == profileId }
            .distinct()
        val peerWpUserIds = peerIds
            .map { peerProfileId -> peerProfileId.toBetterMessagesUserId() }
            .distinct()
        if (peerWpUserIds.isEmpty()) error(appContext.getString(R.string.error_group_chat_no_members))

        val targetWpUserIds = (listOf(currentWpUserId) + peerWpUserIds).distinct()
        findBetterMessagesWallThread(profileId, title, targetWpUserIds)?.let { threadId ->
            betterMessagesAbandonedConversationStore.clearAbandoned(profileId, threadId)
            markBetterMessagesGroupThread(profileId, threadId)
            ensureBetterMessagesParticipants(profileId, threadId, peerWpUserIds)
            return betterMessagesConversationId(threadId)
        }

        val threadId = createBetterMessagesThread(
            profileId = profileId,
            recipientWpUserIds = peerWpUserIds,
            subject = title,
            type = BETTER_MESSAGES_WALL_TYPE,
            initialMessage = appContext.getString(R.string.neighborhood_chat_initial_message, title),
            initialUniqueKey = "quata-community:$communityId",
            allowUniqueKeyFallbacks = false,
            fallbackThreadLookup = { findBetterMessagesWallThread(profileId, title, targetWpUserIds) }
        )
        betterMessagesAbandonedConversationStore.clearAbandoned(profileId, threadId)
        markBetterMessagesGroupThread(profileId, threadId)
        ensureBetterMessagesParticipants(profileId, threadId, peerWpUserIds)
        return betterMessagesConversationId(threadId)
    }

    private suspend fun createBetterMessagesThread(
        profileId: String,
        recipientWpUserIds: List<Int>,
        subject: String,
        type: String? = null,
        initialMessage: String = "",
        initialUniqueKey: String,
        allowUniqueKeyFallbacks: Boolean = true,
        fallbackThreadLookup: suspend () -> Int?
    ): Int {
        val attempts = if (allowUniqueKeyFallbacks) {
            listOf(
                initialUniqueKey,
                "$initialUniqueKey:${System.currentTimeMillis()}",
                null
            )
        } else {
            listOf(initialUniqueKey)
        }
        attempts.forEach { uniqueKey ->
            runCatching {
                val response = betterMessagesRepository.startNewConversation(
                    profileId = profileId,
                    recipientWpUserIds = recipientWpUserIds,
                    subject = subject,
                    type = type,
                    message = initialMessage,
                    uniqueKey = uniqueKey
                )
                response.usableThreadId(profileId)?.let { return it }
            }
        }
        fallbackThreadLookup()?.let { return it }
        error(appContext.getString(R.string.error_chat_thread_missing))
    }

    private suspend fun BmNewThreadResponse.usableThreadId(profileId: String): Int? {
        val threadResponse = threadResponse()
        val threadId = threadId
            ?: threadResponse.threads.firstOrNull { it.threadId > 0 }?.threadId
            ?: return null
        cacheBetterMessagesUpdate(threadId, threadResponse)
        val thread = threadResponse.threads.firstOrNull { it.threadId == threadId }
            ?: runCatching {
                loadBetterMessagesThread(profileId, threadId)
                    .threads
                    .firstOrNull { it.threadId == threadId }
            }.getOrNull()
        return threadId.takeIf { thread?.isHidden != 1 && thread?.isDeleted != 1 }
    }

    private suspend fun resolveBetterMessagesGroupConversation(
        profileId: String,
        peerProfileIds: List<String>,
        noPeersMessage: String,
        addPeersMessage: String
    ): String {
        val context = ensureBetterMessagesSession(profileId)
        val currentWpUserId = context.currentWpUserId ?: error(appContext.getString(R.string.error_chat_session))
        val peerIds = peerProfileIds
            .filterNot { it == profileId }
            .distinct()
        val peerWpUserIds = peerIds
            .map { peerProfileId -> peerProfileId.toBetterMessagesUserId() }
            .distinct()
        if (peerWpUserIds.isEmpty()) error(noPeersMessage)

        val targetWpUserIds = (listOf(currentWpUserId) + peerWpUserIds).distinct()
        findBetterMessagesThreadByParticipants(profileId, targetWpUserIds)?.let { threadId ->
            betterMessagesAbandonedConversationStore.clearAbandoned(profileId, threadId)
            if (targetWpUserIds.size > 2) markBetterMessagesGroupThread(profileId, threadId)
            return betterMessagesConversationId(threadId)
        }

        val firstPeerId = peerIds.firstOrNull() ?: error(noPeersMessage)
        val threadId = resolvePrivateThreadId(profileId, firstPeerId)
            ?: error(appContext.getString(R.string.error_chat_thread_missing))
        betterMessagesAbandonedConversationStore.clearAbandoned(profileId, threadId)
        val firstPeerWpUserId = peerWpUserIds.firstOrNull()

        val threadParticipants = runCatching {
            loadBetterMessagesThread(profileId, threadId)
                .threads
                .firstOrNull { it.threadId == threadId }
                ?.participants
                .orEmpty()
        }.getOrDefault(listOfNotNull(currentWpUserId, firstPeerWpUserId))
        val missingWpUserIds = peerWpUserIds
            .filterNot { it in threadParticipants }
            .distinct()
        if (missingWpUserIds.isNotEmpty()) {
            val added = betterMessagesRepository.addParticipant(profileId, threadId, missingWpUserIds)
            if (!added) error(addPeersMessage)
        }
        if (targetWpUserIds.size > 2) markBetterMessagesGroupThread(profileId, threadId)
        return betterMessagesConversationId(threadId)
    }

    private suspend fun ensureBetterMessagesParticipants(
        profileId: String,
        threadId: Int,
        expectedPeerWpUserIds: List<Int>
    ) {
        val threadParticipants = runCatching {
            loadBetterMessagesThread(profileId, threadId)
                .threads
                .firstOrNull { it.threadId == threadId }
                ?.participants
                .orEmpty()
        }.getOrDefault(emptyList())
        val missingWpUserIds = expectedPeerWpUserIds
            .filterNot { it in threadParticipants }
            .distinct()
        if (missingWpUserIds.isNotEmpty()) {
            val added = betterMessagesRepository.addParticipant(profileId, threadId, missingWpUserIds)
            if (!added) error(appContext.getString(R.string.error_group_chat_add_members))
        }
    }

    private suspend fun findBetterMessagesThreadBySubjectOrParticipants(
        profileId: String,
        subject: String,
        targetWpUserIds: List<Int>
    ): Int? {
        val normalizedSubject = subject.normalizeName()
        val targetSet = targetWpUserIds.toSet()
        val inbox = withBetterMessagesSession(profileId) {
            betterMessagesRepository.checkNewInCurrentSession(lastUpdate = 0)
        }
        inbox.threads
            .filter { thread -> thread.threadId > 0 && thread.isHidden != 1 && thread.isDeleted != 1 }
            .forEach { thread ->
                val fullThread = runCatching {
                    loadBetterMessagesThread(profileId, thread.threadId)
                        .threads
                        .firstOrNull { it.threadId == thread.threadId }
                }.getOrNull() ?: thread
                val threadSubject = fullThread.subject?.normalizeName()
                    ?: fullThread.title?.normalizeName()
                val participants = fullThread.participants.toSet()
                if (threadSubject == normalizedSubject || participants == targetSet) {
                    return fullThread.threadId
                }
            }
        return null
    }

    private suspend fun findBetterMessagesWallThread(
        profileId: String,
        subject: String,
        requiredWpUserIds: List<Int>
    ): Int? {
        val normalizedSubject = subject.normalizeName()
        val requiredSet = requiredWpUserIds.toSet()
        if (normalizedSubject.isBlank() || requiredSet.size < 2) return null
        val inbox = withBetterMessagesSession(profileId) {
            betterMessagesRepository.checkNewInCurrentSession(lastUpdate = 0)
        }
        val subjectMatches = mutableListOf<Pair<Int, Set<Int>>>()
        inbox.threads
            .filter { thread -> thread.threadId > 0 && thread.isHidden != 1 && thread.isDeleted != 1 }
            .forEach { thread ->
                val fullThread = runCatching {
                    loadBetterMessagesThread(profileId, thread.threadId)
                        .threads
                        .firstOrNull { it.threadId == thread.threadId }
                }.getOrNull() ?: thread
                val threadSubject = fullThread.subject?.normalizeName()
                    ?: fullThread.title?.normalizeName()
                    ?: return@forEach
                if (threadSubject != normalizedSubject) return@forEach
                val isWallLike = fullThread.type == BETTER_MESSAGES_WALL_TYPE ||
                    thread.type == BETTER_MESSAGES_WALL_TYPE ||
                    !fullThread.subject.isNullOrBlank()
                if (!isWallLike) return@forEach
                val participants = fullThread.participants.toSet()
                if (threadSubject == normalizedSubject && participants.containsAll(requiredSet)) {
                    return fullThread.threadId
                }
                subjectMatches += fullThread.threadId to participants
            }
        return subjectMatches
            .maxByOrNull { (_, participants) -> participants.intersect(requiredSet).size }
            ?.first
    }

    private suspend fun findBetterMessagesThreadByParticipants(profileId: String, targetWpUserIds: List<Int>): Int? {
        val targetSet = targetWpUserIds.toSet()
        if (targetSet.size < 2) return null
        val inbox = withBetterMessagesSession(profileId) {
            betterMessagesRepository.checkNewInCurrentSession(lastUpdate = 0)
        }
        inbox.threads
            .filter { thread -> thread.threadId > 0 && thread.isHidden != 1 && thread.isDeleted != 1 }
            .forEach { thread ->
                val participants = runCatching {
                    loadBetterMessagesThread(profileId, thread.threadId)
                        .threads
                        .firstOrNull { it.threadId == thread.threadId }
                        ?.participants
                        ?.toSet()
                }.getOrNull() ?: thread.participants.toSet()
                if (participants == targetSet) return thread.threadId
            }
        return null
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
            .sortedByBetterMessagesTime()
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
        val lastMessage = messages.maxByOrNull { it.betterMessagesSortMillis() }
        val defaultParticipantsTitle = "${participantsCount ?: participants.size} participantes"
        val displayTitle = subject?.takeIf { it.isNotBlank() }
            ?: title?.takeIf { it.isNotBlank() && it != defaultParticipantsTitle }
            ?: participantNames.joinToString(", ").ifBlank { "Chat $threadId" }
        val isSosConversation = displayTitle.isSosThreadTitle() || subject?.isSosThreadTitle() == true
        return Conversation(
            id = betterMessagesConversationId(threadId),
            title = displayTitle,
            avatarUrl = image?.takeIf { it.isNotBlank() } ?: peerUsers.firstOrNull()?.avatar,
            lastMessagePreview = lastMessage?.message?.stripHtmlTagsAndDecode().orEmpty(),
            unreadCount = unread ?: 0,
            updatedAt = lastMessage?.betterMessagesDisplayTime() ?: lastTime?.toDisplayTime().orEmpty(),
            updatedAtMillis = lastMessage?.betterMessagesTimestampMillisOrNull()
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
                forceGroup = forceGroup || isSosConversation,
                isKnownPrivateThread = isKnownPrivateThread
            ),
            isEmergency = isSosConversation,
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
        if (forceGroup || type == "group" || type == BETTER_MESSAGES_WALL_TYPE) return true
        if (isKnownPrivateThread) return false
        return participants.size > 2 || (participantsCount ?: 0) > 2
    }

    private fun String.isSosThreadTitle(): Boolean =
        normalizeName().contains("sos")

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
            val media = mediaUploadOptimizer.prepareAttachmentUpload(uriString, attachmentName, attachmentMimeType)
            val uploadDir = File(cacheDir, "quata-bm-upload-${System.currentTimeMillis()}-${Random.nextInt(1_000, 9_999)}")
            uploadDir.mkdirs()
            val file = File(uploadDir, media.fileName.sanitizeUploadFileName())
            file.writeBytes(media.bytes)
            CachedMedia(file = file, mimeType = media.mimeType)
        }

    private fun String.normalizeName(): String = trim().lowercase(Locale.ROOT)

    private fun String.normalizedAvatarUrl(): String = trim().substringBefore("?").trimEnd('/')

    private fun String.supabaseProfileIdOrNull(): String? =
        PROFILE_ID_REGEX.find(this)?.value

    private fun String.sanitizeUploadFileName(): String =
        replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .ifBlank { "upload.bin" }

    private fun inboxDiscoveryIntervalMillis(mode: ChatPollingMode): Long =
        when (mode) {
            ChatPollingMode.AGGRESSIVE -> 4_000L
            ChatPollingMode.MEDIUM -> 10_000L
            ChatPollingMode.RELAXED -> 15_000L
            ChatPollingMode.MINIMAL -> 30_000L
        }

    private fun discoveryFailureRetryMillis(mode: ChatPollingMode): Long =
        when (mode) {
            ChatPollingMode.AGGRESSIVE -> 4_000L
            ChatPollingMode.MEDIUM -> 10_000L
            ChatPollingMode.RELAXED -> BACKGROUND_POLL_LOOP_DELAY_MILLIS
            ChatPollingMode.MINIMAL -> MINIMAL_BACKGROUND_POLL_LOOP_DELAY_MILLIS
        }

    private fun nextPollingDelayMillis(): Long {
        if (shouldEmitNativeNotifications()) {
            return when (pollingMode.value) {
                ChatPollingMode.RELAXED -> BACKGROUND_POLL_LOOP_DELAY_MILLIS
                ChatPollingMode.MINIMAL -> MINIMAL_BACKGROUND_POLL_LOOP_DELAY_MILLIS
                else -> MAX_POLL_LOOP_DELAY_MILLIS
            }
        }
        return when (pollingMode.value) {
            ChatPollingMode.AGGRESSIVE -> 4_000L
            ChatPollingMode.MEDIUM -> 10_000L
            ChatPollingMode.RELAXED -> 15_000L
            ChatPollingMode.MINIMAL -> 30_000L
        }
    }

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
        const val CONVERSATIONS_POLL_DELAY_MILLIS = 5_000L
        const val MESSAGES_POLL_DELAY_MILLIS = 4_000L
        const val MAX_POLL_LOOP_DELAY_MILLIS = 20_000L
        const val BACKGROUND_POLL_LOOP_DELAY_MILLIS = 15_000L
        const val MINIMAL_BACKGROUND_POLL_LOOP_DELAY_MILLIS = 30_000L
        const val PROFILE_CACHE_RETENTION_MILLIS = 24L * 60L * 60L * 1000L
        const val POLLING_TAG = "QuataChatPolling"
        const val SOS_THREAD_TITLE = "\uD83D\uDEA8 SOS"
        const val BETTER_MESSAGES_WALL_TYPE = "wall"
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
