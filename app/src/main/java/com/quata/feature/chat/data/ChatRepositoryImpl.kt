package com.quata.feature.chat.data

import android.content.Context
import android.util.Log
import com.quata.R
import com.quata.core.common.mapFailureToUserFacing
import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.media.MediaUploadOptimizer
import com.quata.core.model.AuthSession
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.MessageDeliveryState
import com.quata.core.model.User
import com.quata.core.navigation.AppDestinations
import com.quata.core.notifications.NotificationFactory
import com.quata.core.session.SessionManager
import com.quata.core.session.AuthState
import com.quata.data.supabase.CommunityProfile
import com.quata.data.supabase.RealtimeRawEvent
import com.quata.data.supabase.RealtimeStatus
import com.quata.data.supabase.SupabaseRealtimeClient
import com.quata.feature.chat.domain.ChatConversationCandidate
import com.quata.feature.chat.domain.ChatConversationCandidatePage
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.domain.ChatSyncStatus
import com.quata.feature.chat.domain.isExactPrivateConversation
import com.quata.feature.chat.domain.normalizeContactPhoneKey
import com.quata.feature.chat.domain.prepareContactDiscoveryBatches
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.text.Normalizer
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import kotlin.random.Random

class ChatRepositoryImpl(
    private val appContext: Context,
    private val remote: ChatRemoteDataSource,
    private val supabaseRealtimeClient: SupabaseRealtimeClient,
    private val sessionManager: SessionManager,
    private val mediaUploadOptimizer: MediaUploadOptimizer,
    private val messageStateAckManager: ChatMessageStateAckManager,
    private val typingIndicatorManager: ChatTypingIndicatorManager
) : ChatRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheStore = SupabaseChatCacheStore(appContext)
    private val attachmentFileCache = ChatAttachmentFileCache(appContext)
    private val outboxAttachmentStore = ChatOutboxAttachmentStore(appContext)
    private val notificationFactory = NotificationFactory(appContext)
    private val refreshMutex = Mutex()
    private val messageStates = ConcurrentHashMap<String, MutableStateFlow<List<Message>>>()
    private val favoriteMessages = MutableStateFlow<List<Message>>(emptyList())
    private val profilesById = ConcurrentHashMap<String, CommunityProfile>()
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val _activeConversationId = MutableStateFlow<String?>(null)
    private val _pendingDeletedConversation = MutableStateFlow<Conversation?>(null)
    private val _isRealtimeOnline = MutableStateFlow(true)
    private val _syncStatus = MutableStateFlow(ChatSyncStatus.Refreshing)
    private val appForegroundState = MutableStateFlow(false)
    private val deviceNetworkAvailable = MutableStateFlow(true)
    private var realtimeProfileId: String? = null
    private var realtimeAccessToken: String? = null
    private var realtimeReadyProfileId: String? = null
    @Volatile
    private var isRealtimeConnecting = false
    private var reconnectJob: Job? = null
    private var reconnectAttempt: Int = 0
    private var realtimeTokenRefreshJob: Job? = null
    private var lastFullRefreshAtMillis: Long = 0L
    private var lastFavoritesRefreshAtMillis: Long = 0L
    private val lastThreadRefreshAtMillis = ConcurrentHashMap<String, Long>()
    @Volatile
    private var cacheProfileId: String? = null

    override val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()
    override val isAppForeground: StateFlow<Boolean> = appForegroundState.asStateFlow()
    override val pendingDeletedConversation: StateFlow<Conversation?> = _pendingDeletedConversation.asStateFlow()
    override val isRealtimeOnline: StateFlow<Boolean> = _isRealtimeOnline.asStateFlow()
    override val typingProfileIds: StateFlow<Set<String>> = typingIndicatorManager.typingProfileIds
    override val syncStatus: StateFlow<ChatSyncStatus> = _syncStatus.asStateFlow()

    init {
        if (AppConfig.USE_MOCK_BACKEND) {
            _conversations.value = MockData.conversations
            _isRealtimeOnline.value = true
        } else {
            scope.launch {
                sessionManager.authState
                    .map { state -> (state as? AuthState.LoggedIn)?.userId }
                    .distinctUntilChanged()
                    .collect { profileId -> switchChatProfile(profileId) }
            }
        }
    }

    override fun setDeviceNetworkAvailable(isAvailable: Boolean) {
        typingIndicatorManager.setDeviceNetworkAvailable(isAvailable)
        if (AppConfig.USE_MOCK_BACKEND) {
            deviceNetworkAvailable.value = isAvailable
            _isRealtimeOnline.value = isAvailable
            return
        }
        if (deviceNetworkAvailable.value == isAvailable) return
        deviceNetworkAvailable.value = isAvailable
        if (!isAvailable) {
            _syncStatus.value = ChatSyncStatus.Offline
            _isRealtimeOnline.value = false
            isRealtimeConnecting = false
            realtimeProfileId = null
            realtimeAccessToken = null
            realtimeReadyProfileId = null
            reconnectJob?.cancel()
            reconnectJob = null
            realtimeTokenRefreshJob?.cancel()
            realtimeTokenRefreshJob = null
            supabaseRealtimeClient.disconnect()
            return
        }
        _isRealtimeOnline.value = false
        _syncStatus.value = ChatSyncStatus.Refreshing
        sessionManager.currentSession()?.let { session ->
            scope.launch {
                if (appForegroundState.value) {
                    refreshAndConnectRealtime(session.userId)
                } else {
                    refreshAll(session.userId)
                }
                flushPendingMessages()
            }
        }
    }

    override fun currentUser(): User? =
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.currentUser
        } else {
            sessionManager.currentSession()?.let { session ->
                User(
            id = session.userId,
            email = session.email,
            displayName = session.displayName
                )
            }
        }

    override fun setActiveConversation(conversationId: String?) {
        _activeConversationId.value = conversationId
        if (AppConfig.USE_MOCK_BACKEND) {
            if (conversationId != null) {
                MockData.markConversationRead(conversationId)
                messagesState(conversationId).value = MockData.messages.filter { it.conversationId == conversationId }
                _conversations.value = MockData.conversations
            }
            return
        }
        if (conversationId != null) {
            notificationFactory.clearChatMessage(conversationId)
            scope.launch {
                restoreMessagesFromCache(conversationId)
                if (deviceNetworkAvailable.value) refreshMessagesInBackground(conversationId)
            }
        }
    }

    override fun setConversationVisible(conversationId: String, visible: Boolean) {
        typingIndicatorManager.setVisibleConversation(conversationId, visible)
        if (visible) {
            setActiveConversation(conversationId)
        } else if (_activeConversationId.value == conversationId) {
            _activeConversationId.value = null
        }
    }

    private suspend fun switchChatProfile(profileId: String?) {
        if (cacheProfileId == profileId) return
        stopRealtime()
        cacheProfileId = profileId
        _activeConversationId.value = null
        _pendingDeletedConversation.value = null
        _conversations.value = emptyList()
        messageStates.clear()
        favoriteMessages.value = emptyList()
        profilesById.clear()
        lastThreadRefreshAtMillis.clear()
        lastFullRefreshAtMillis = 0L
        lastFavoritesRefreshAtMillis = 0L
        notificationFactory.clearChatMessages()
        if (profileId == null) return
        restoreCache(profileId)
        if (appForegroundState.value && deviceNetworkAvailable.value) {
            refreshAndConnectRealtime(profileId)
            flushPendingMessages()
        }
    }

    override fun cleanupEmptyConversation(conversationId: String) {
        if (conversationId == AppDestinations.FavoriteMessagesConversationId) return
        val threadId = conversationId.supabaseThreadIdOrNull() ?: return
        val session = sessionManager.currentSession() ?: return
        val conversation = _conversations.value.firstOrNull { it.id == conversationId }
        if (conversation?.isGroup == true || conversation?.isEmergency == true) return
        if (messageStates[conversationId]?.value.orEmpty().isNotEmpty()) return

        scope.launch {
            runCatching {
                if (AppConfig.USE_MOCK_BACKEND) {
                    removeConversation(session.userId, conversationId)
                    return@runCatching
                }
                val result = remote.cleanupEmptyPrivateThread(session.userId, threadId)
                if (result.obj.boolean("deleted") == true) {
                    messageStates.remove(conversationId)
                    removeConversation(session.userId, conversationId)
                }
            }.onFailure { error ->
                Log.w("ChatRepository", "No se pudo limpiar chat privado vacio $conversationId", error)
            }
        }
    }

    override fun setAppForeground(isForeground: Boolean) {
        if (appForegroundState.value == isForeground) return
        appForegroundState.value = isForeground
        typingIndicatorManager.setAppForeground(isForeground)
        if (isForeground) {
            sessionManager.currentSession()?.let { session ->
                scope.launch {
                    refreshAndConnectRealtime(session.userId)
                    _activeConversationId.value?.let { conversationId ->
                        refreshMessages(conversationId, force = true)
                    }
                }
            }
        } else {
            stopRealtime()
        }
    }

    override fun setTyping(conversationId: String, isTyping: Boolean) {
        typingIndicatorManager.setTyping(conversationId, isTyping)
    }

    override fun clearChatNotifications() {
        notificationFactory.clearChatMessages()
    }

    override suspend fun getConversations(): Result<List<Conversation>> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            _conversations.value = MockData.conversations
            return@runCatching MockData.conversations
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        _conversations.value.takeIf { it.isNotEmpty() }?.let { conversations ->
            if (deviceNetworkAvailable.value) scope.launch { refreshAndConnectRealtime(session.userId) }
            return@runCatching conversations
        }
        val restored = restoreCache(session.userId)
        if (deviceNetworkAvailable.value) {
            if (restored) {
                scope.launch { refreshAndConnectRealtime(session.userId) }
            } else {
                refreshAndConnectRealtime(session.userId)
            }
        }
        _conversations.value
    }.mapFailureToUserFacing(appContext, R.string.error_load_chats)

    override fun observeConversations(): Flow<List<Conversation>> =
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.conversationsFlow.onStart { _conversations.value = MockData.conversations }
        } else {
        _conversations.onStart {
            sessionManager.currentSession()?.let { session ->
                val restored = restoreCache(session.userId)
                if (deviceNetworkAvailable.value) {
                    if (restored || _conversations.value.isNotEmpty()) {
                        scope.launch { refreshAndConnectRealtime(session.userId) }
                    } else {
                        refreshAndConnectRealtime(session.userId)
                    }
                }
            }
        }
        }

    override fun observeMessages(conversationId: String): Flow<List<Message>> =
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.messagesFlow.map { messages ->
                if (conversationId == AppDestinations.FavoriteMessagesConversationId) {
                    messages.filter { it.isFavorite && !it.isDeleted }
                        .sortedByDescending { it.sentAtMillis ?: 0L }
                } else {
                    messages.filter { it.conversationId == conversationId }
                }
            }
        } else {
        messagesState(conversationId).onStart {
            val restored = restoreMessagesFromCache(conversationId)
            if (deviceNetworkAvailable.value) {
                if (restored) {
                    refreshMessagesInBackground(conversationId)
                } else {
                    refreshMessages(conversationId)
                }
            }
        }
        }

    override suspend fun loadOlderMessages(conversationId: String, limit: Int): Result<Boolean> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND || conversationId == AppDestinations.FavoriteMessagesConversationId) return@runCatching false
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val knownIds = messagesState(conversationId).value.mapNotNull { it.id.toLongOrNull() }
        val payload = remote.getChatThread(session.userId, conversationId.requireThreadId(), limit, knownIds)
        val parsed = parseChatPayload(payload, session.userId)
        parsed.profiles.forEach { profilesById[it.id] = it }
        val older = parsed.messages
        if (older.isNotEmpty()) {
            val merged = reconcileChatMessages(older, messagesState(conversationId).value)
            messagesState(conversationId).value = attachmentFileCache.resolveCached(session.userId, merged)
            cacheStore.replaceMessages(session.userId, conversationId, merged)
        }
        older.size >= limit
    }.mapFailureToUserFacing(appContext, R.string.error_load_chats)

    override fun observeParticipantCandidates(): Flow<List<User>> {
        if (AppConfig.USE_MOCK_BACKEND) {
            return MockData.socialFlow.map { MockData.registeredUsers }
                .onStart { emit(MockData.registeredUsers) }
        }
        val state = MutableStateFlow<List<User>>(profilesById.values.map { it.toUser() })
        scope.launch {
            runCatching {
                val session = sessionManager.currentSession()
                val cached = session?.let { cacheStore.cachedProfileDirectory(it.userId) }.orEmpty()
                if (cached.isNotEmpty()) state.value = cached.map { it.toUser() }
                val profiles = remote.getProfiles(limit = 1000)
                profiles.forEach { profilesById[it.id] = it }
                session?.let { cacheStore.replaceProfileDirectory(it.userId, profiles) }
                state.value = profiles.map { it.toUser() }
            }
        }
        return state
    }

    override suspend fun searchConversationCandidates(query: String, limit: Int, offset: Int): Result<ChatConversationCandidatePage> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            val cleanQuery = query.trim()
            val currentId = MockData.currentUser.id
            val existingConversations = MockData.conversations
            val existingPrivatePeers = existingConversations
                .filter { !it.isGroup && !it.isEmergency }
                .flatMap { conversation -> conversation.participantIds.filterNot { it == currentId } }
                .toSet()
            val candidates = MockData.mockAuthProfiles
                .filterNot { it.id == currentId }
                .filter { profile ->
                    cleanQuery.isBlank() ||
                        listOf(profile.displayName, profile.neighborhood, profile.email, profile.phone, profile.countryCode)
                            .any { it.contains(cleanQuery, ignoreCase = true) }
                }
                .drop(offset.coerceAtLeast(0))
                .take(limit.coerceIn(1, 50))
                .map { profile ->
                    val user = profile.toUser()
                    val isContact = profile.id in existingPrivatePeers
                    ChatConversationCandidate(
                        profileId = profile.id,
                        displayName = user.displayName,
                        neighborhood = user.neighborhood,
                        phone = "",
                        avatarUrl = user.avatarUrl,
                        sectionKey = if (isContact) "contacts" else "other",
                        neighborhoodGroup = user.neighborhood,
                        existingConversationId = existingConversations.firstOrNull {
                            it.isExactPrivateConversation(currentId, profile.id)
                        }?.id
                    )
                }
            return@runCatching ChatConversationCandidatePage(
                candidates = candidates,
                hasMore = candidates.size >= limit,
                nextOffset = offset + candidates.size,
                actorNeighborhood = MockData.currentUser.neighborhood
            )
        }

        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val payload = remote.searchChatConversationCandidates(
            profileId = session.userId,
            query = query.trim(),
            limit = limit,
            offset = offset
        )
        val root = payload.obj
        val candidates = root.array("items").mapNotNull { it.objOrNull?.toConversationCandidate() }
        candidates.forEach { candidate ->
            profilesById[candidate.profileId] = CommunityProfile(
                id = candidate.profileId,
                display_name = candidate.displayName,
                neighborhood = candidate.neighborhood,
                phone_local = null,
                avatar_url = candidate.avatarUrl
            )
        }
        ChatConversationCandidatePage(
            candidates = candidates,
            hasMore = root.boolean("has_more") == true,
            nextOffset = root.int("next_offset") ?: (offset + candidates.size),
            actorNeighborhood = root.string("actor_neighborhood").orEmpty()
        )
    }.mapFailureToUserFacing(appContext, R.string.error_load_chats)

    override suspend fun matchRegisteredContactPhones(phoneCandidates: Collection<String>): Result<Set<String>> = runCatching {
        val candidateBatches = prepareContactDiscoveryBatches(phoneCandidates)
        if (candidateBatches.isEmpty()) return@runCatching emptySet()
        val normalizedCandidates = candidateBatches.flatten()

        if (AppConfig.USE_MOCK_BACKEND) {
            val registeredKeys = MockData.mockAuthProfiles
                .flatMap { profile ->
                    listOf(
                        profile.phone,
                        "${profile.countryCode}${profile.phone}"
                    )
                }
                .map(::normalizeContactPhoneKey)
                .filter { it.length in 6..20 }
                .toSet()
            return@runCatching normalizedCandidates.filterTo(mutableSetOf()) { it in registeredKeys }
        }

        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        buildSet {
            candidateBatches.forEach { batch ->
                val root = remote.matchRegisteredChatContacts(session.userId, batch).obj
                root.array("matched_phones").forEach { value ->
                    value.jsonPrimitive.contentOrNull?.let(::add)
                }
            }
        }
    }.mapFailureToUserFacing(appContext, R.string.error_load_chats)

    override suspend fun openPrivateConversation(peerProfileId: String): Result<String> =
        openGroupConversation(listOf(peerProfileId), title = null)

    override suspend fun sendMessage(
        conversationId: String,
        text: String,
        attachmentUri: String?,
        attachmentName: String?,
        attachmentMimeType: String?,
        clientMessageId: String?
    ): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            val user = MockData.currentUser
            MockData.addMessage(
                conversationId = conversationId,
                text = text.ifBlank { attachmentName.orEmpty() },
                senderId = user.id,
                senderName = user.displayName,
                attachmentUri = attachmentUri,
                attachmentName = attachmentName,
                attachmentMimeType = attachmentMimeType
            )
            _conversations.value = MockData.conversations
            messagesState(conversationId).value = MockData.messages.filter { it.conversationId == conversationId }
            return@runCatching
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val id = clientMessageId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val stagedUri = outboxAttachmentStore.stage(id, attachmentUri)
        val pending = PendingOutgoingMessage(
            profileId = session.userId,
            clientMessageId = id,
            conversationId = conversationId,
            text = text,
            attachmentUri = stagedUri,
            attachmentName = attachmentName,
            attachmentMimeType = attachmentMimeType
        )
        persistPendingOutgoing(session, pending)
        if (deviceNetworkAvailable.value) attemptOutgoing(pending) else ChatOutboxWorkScheduler.scheduleOneTime(appContext)
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun sendReply(
        conversationId: String,
        text: String,
        replyTo: Message,
        attachmentUri: String?,
        attachmentName: String?,
        attachmentMimeType: String?,
        clientMessageId: String?
    ): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            val user = MockData.currentUser
            MockData.addMessage(
                conversationId = conversationId,
                text = text.ifBlank { attachmentName.orEmpty() },
                senderId = user.id,
                senderName = user.displayName,
                replyTo = replyTo,
                attachmentUri = attachmentUri,
                attachmentName = attachmentName,
                attachmentMimeType = attachmentMimeType
            )
            _conversations.value = MockData.conversations
            messagesState(conversationId).value = MockData.messages.filter { it.conversationId == conversationId }
            return@runCatching
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val id = clientMessageId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val stagedUri = outboxAttachmentStore.stage(id, attachmentUri)
        val pending = PendingOutgoingMessage(
            profileId = session.userId,
            clientMessageId = id,
            conversationId = conversationId,
            text = text,
            attachmentUri = stagedUri,
            attachmentName = attachmentName,
            attachmentMimeType = attachmentMimeType,
            replyToMessageId = replyTo.id,
            replyToSenderName = replyTo.senderName,
            replyToText = replyTo.text
        )
        persistPendingOutgoing(session, pending)
        if (deviceNetworkAvailable.value) attemptOutgoing(pending) else ChatOutboxWorkScheduler.scheduleOneTime(appContext)
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun flushPendingMessages(): Boolean {
        if (AppConfig.USE_MOCK_BACKEND) return true
        val session = sessionManager.currentSession() ?: return true
        if (!deviceNetworkAvailable.value) return false
        val pending = cacheStore.pendingOutgoing(session.userId)
        var allSent = true
        pending.forEach { outgoing ->
            if (outgoing.attempts >= MAX_OUTBOX_ATTEMPTS) {
                markOutgoingFailed(outgoing)
            } else if (!attemptOutgoing(outgoing)) {
                allSent = false
            }
        }
        return allSent
    }

    override suspend fun retryPendingMessage(clientMessageId: String): Result<Unit> = runCatching {
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val outgoing = cacheStore.pendingOutgoing(session.userId).firstOrNull { it.clientMessageId == clientMessageId }
            ?: error("Mensaje pendiente no encontrado")
        cacheStore.resetOutgoingAttempts(session.userId, clientMessageId)
        val state = messagesState(outgoing.conversationId)
        state.value = state.value.map { message ->
            if (message.clientMessageId == clientMessageId) message.copy(isPending = true, deliveryState = MessageDeliveryState.Pending) else message
        }
        cacheStore.replaceMessages(session.userId, outgoing.conversationId, state.value)
        if (deviceNetworkAvailable.value) attemptOutgoing(outgoing.copy(attempts = 0, lastError = null)) else ChatOutboxWorkScheduler.scheduleOneTime(appContext)
        Unit
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    private suspend fun markOutgoingFailed(outgoing: PendingOutgoingMessage) {
        val state = messagesState(outgoing.conversationId)
        state.value = state.value.map { message ->
            if (message.clientMessageId == outgoing.clientMessageId) message.copy(isPending = false, deliveryState = MessageDeliveryState.Failed) else message
        }
        cacheStore.replaceMessages(outgoing.profileId, outgoing.conversationId, state.value)
    }

    private suspend fun persistPendingOutgoing(session: AuthSession, outgoing: PendingOutgoingMessage) {
        cacheStore.enqueueOutgoing(outgoing)
        val message = Message(
            id = "local:${outgoing.clientMessageId}",
            conversationId = outgoing.conversationId,
            senderId = session.userId,
            senderName = session.displayName.ifBlank { appContext.getString(R.string.common_you) },
            text = outgoing.text,
            sentAt = Instant.ofEpochMilli(outgoing.createdAtMillis).toString(),
            sentAtMillis = outgoing.createdAtMillis,
            isMine = true,
            isRead = false,
            replyToMessageId = outgoing.replyToMessageId,
            replyToSenderName = outgoing.replyToSenderName,
            replyToText = outgoing.replyToText,
            attachmentUri = outgoing.attachmentUri,
            attachmentName = outgoing.attachmentName,
            attachmentMimeType = outgoing.attachmentMimeType,
            clientMessageId = outgoing.clientMessageId,
            isPending = true,
            isLocalEcho = true,
            deliveryState = MessageDeliveryState.Pending
        )
        val state = messagesState(outgoing.conversationId)
        state.value = (state.value.filterNot { it.clientMessageId == outgoing.clientMessageId } + message)
            .sortedBy { it.sentAtMillis ?: Long.MAX_VALUE }
        cacheStore.upsertMessage(session.userId, message)
    }

    private suspend fun attemptOutgoing(outgoing: PendingOutgoingMessage): Boolean {
        val session = sessionManager.currentSession()?.takeIf { it.userId == outgoing.profileId } ?: return false
        return runCatching {
            val threadId = outgoing.conversationId.requireThreadId()
            val remoteFileId = outgoing.remoteFileId ?: outgoing.attachmentUri?.takeIf { it.isNotBlank() }?.let { uri ->
                uploadAttachment(session.userId, threadId, uri, outgoing.attachmentName, outgoing.attachmentMimeType).also { fileId ->
                    cacheStore.markOutgoingUploaded(session.userId, outgoing.clientMessageId, fileId)
                }
            }
            val payload = remote.sendChatMessage(
                profileId = session.userId,
                threadId = threadId,
                message = outgoing.text,
                fileIds = listOfNotNull(remoteFileId),
                replyToMessageId = outgoing.replyToMessageId?.toLongOrNull(),
                clientMessageId = outgoing.clientMessageId
            )
            // Reconcile the confirmed server row while the optimistic row is
            // still present. This avoids an observable empty/removal frame and
            // persists the client's stable visual identity on the confirmed row.
            mergeChatPayload(payload, session.userId, outgoing)
            cacheStore.removeOutgoing(session.userId, outgoing.clientMessageId)
            outboxAttachmentStore.remove(outgoing.attachmentUri)
            refreshThread(outgoing.conversationId, force = true)
            refreshAll(session.userId, force = true)
        }.onFailure { error ->
            cacheStore.markOutgoingAttempt(outgoing.profileId, outgoing.clientMessageId, error.message)
            ChatOutboxWorkScheduler.scheduleOneTime(appContext)
            Log.w(TAG, "Could not send queued message ${outgoing.clientMessageId}", error)
        }.isSuccess
    }

    override suspend fun sendSosMessage(
        contactIds: List<String>,
        text: String,
        lat: Double?,
        lng: Double?,
        accuracy: Double?
    ): Result<String> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            val user = MockData.currentUser
            val conversationId = MockData.addSosConversation(contactIds, text, user.id, user.displayName)
            _conversations.value = MockData.conversations
            messagesState(conversationId).value = MockData.messages.filter { it.conversationId == conversationId }
            return@runCatching conversationId
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val payload = remote.sendChatSos(session.userId, contactIds, text, lat, lng, accuracy)
        val threadId = payload.obj.long("thread_id") ?: payload.obj.obj("thread")?.long("thread_id") ?: error("No se pudo abrir SOS")
        val conversationId = supabaseChatConversationId(threadId)
        mergeChatPayload(payload, session.userId)
        refreshThread(conversationId, force = true)
        conversationId
    }.mapFailureToUserFacing(appContext, R.string.sos_send_error)

    override suspend fun cachedPrivateConversationId(userId: String): String? {
        if (AppConfig.USE_MOCK_BACKEND) {
            val user = MockData.currentUser
            return runCatching { MockData.findOrCreatePrivateConversation(userId, user.id, user.displayName) }.getOrNull()
        }
        val session = sessionManager.currentSession() ?: return null
        val existing = _conversations.value.firstOrNull { conversation ->
            conversation.isExactPrivateConversation(session.userId, userId)
        }?.id
        if (existing != null) return existing
        val payload = remote.getOrCreatePrivateThread(session.userId, userId)
        mergeChatPayload(payload, session.userId)
        val threadId = payload.obj.long("thread_id") ?: payload.obj.obj("thread")?.long("thread_id") ?: return null
        refreshAll(session.userId, force = true)
        return supabaseChatConversationId(threadId)
    }

    override suspend fun cachedCommunityConversationId(communityName: String): String? {
        val key = communityName.normalizeName()
        if (AppConfig.USE_MOCK_BACKEND) {
            return MockData.conversations.firstOrNull { conversation ->
                conversation.communityName?.normalizeName() == key || conversation.title.normalizeName() == key
            }?.id
        }
        return _conversations.value.firstOrNull { conversation ->
            conversation.communityName?.normalizeName() == key || conversation.title.normalizeName() == key
        }?.id
    }

    override suspend fun openCommunityConversation(communityId: String, title: String, participantIds: List<String>): Result<String> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            val user = MockData.currentUser
            val conversationId = MockData.findOrCreateNeighborhoodConversation(title, user.id, user.displayName)
            _conversations.value = MockData.conversations
            return@runCatching conversationId
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val payload = remote.openCommunityChatThread(session.userId, communityId, title)
        mergeChatPayload(payload, session.userId)
        val threadId = payload.obj.long("thread_id") ?: payload.obj.obj("thread")?.long("thread_id") ?: error("No se pudo abrir el chat")
        val conversationId = supabaseChatConversationId(threadId)
        refreshThread(conversationId, force = true)
        refreshAll(session.userId, force = true)
        conversationId
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun openGroupConversation(participantIds: List<String>, title: String?): Result<String> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            val user = MockData.currentUser
            val cleanParticipants = participantIds.distinct().filterNot { it == user.id }
            val conversationId = if (cleanParticipants.size == 1 && title.isNullOrBlank()) {
                MockData.findOrCreatePrivateConversation(cleanParticipants.first(), user.id, user.displayName)
            } else {
                MockData.findOrCreateGroupConversation(cleanParticipants, user.id, user.displayName, title)
            }
            _conversations.value = MockData.conversations
            return@runCatching conversationId
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val cleanParticipants = participantIds.distinct().filterNot { it == session.userId }
        val payload = if (cleanParticipants.size == 1 && title.isNullOrBlank()) {
            remote.getOrCreatePrivateThread(session.userId, cleanParticipants.first())
        } else {
            remote.startChatThread(
                profileId = session.userId,
                participantIds = cleanParticipants,
                subject = title,
                type = "group",
                uniqueKey = "quata-group:${(cleanParticipants + session.userId).sorted().joinToString(":")}:${title.orEmpty().normalizeName()}"
            )
        }
        mergeChatPayload(payload, session.userId)
        val threadId = payload.obj.long("thread_id") ?: payload.obj.obj("thread")?.long("thread_id") ?: error("No se pudo abrir el chat")
        val conversationId = supabaseChatConversationId(threadId)
        refreshThread(conversationId, force = true)
        refreshAll(session.userId, force = true)
        conversationId
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun markConversationRead(conversationId: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.markConversationRead(conversationId)
            _conversations.value = MockData.conversations
            messagesState(conversationId).value = MockData.messages.filter { it.conversationId == conversationId }
            return@runCatching
        }
        val session = sessionManager.currentSession() ?: return@runCatching
        if (conversationId == AppDestinations.FavoriteMessagesConversationId) return@runCatching
        val incomingMessageIds = messageStates[conversationId]
            ?.value
            .orEmpty()
            .mapNotNull { message ->
                message.id.toLongOrNull()?.takeIf { !message.isMine && !message.isDeleted }
            }
        if (incomingMessageIds.isNotEmpty()) {
            messageStateAckManager.markMessages(
                messageIds = incomingMessageIds,
                status = ChatMessageStateAckStatus.Read,
                source = "chat_open"
            )
        } else {
            val unreadCount = _conversations.value.firstOrNull { it.id == conversationId }?.unreadCount ?: 0
            if (unreadCount > 0) {
                remote.markChatThreadRead(session.userId, conversationId.requireThreadId())
            }
        }
        updateConversation(conversationId) { it.copy(unreadCount = 0) }
        notificationFactory.clearChatMessage(conversationId)
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun setConversationMuted(conversationId: String, muted: Boolean): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.setConversationMuted(conversationId, muted)
            _conversations.value = MockData.conversations
            return@runCatching
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        remote.setChatMuted(session.userId, conversationId.requireThreadId(), muted)
        updateConversation(conversationId) { it.copy(isMuted = muted) }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun setMemberInvitesEnabled(conversationId: String, enabled: Boolean): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.setMemberInvitesEnabled(conversationId, enabled)
            _conversations.value = MockData.conversations
            return@runCatching
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        remote.setChatMemberInvitesEnabled(session.userId, conversationId.requireThreadId(), enabled)
        updateConversation(conversationId) { it.copy(canMembersInvite = enabled) }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun addParticipants(conversationId: String, participantIds: List<String>): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            val user = MockData.currentUser
            MockData.addParticipants(conversationId, participantIds, user.id, user.displayName)
            _conversations.value = MockData.conversations
            return@runCatching
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        remote.addChatParticipants(session.userId, conversationId.requireThreadId(), participantIds)
        refreshThread(conversationId, force = true)
        refreshAll(session.userId, force = true)
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun promoteModerator(conversationId: String, userId: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.promoteModerator(conversationId, userId)
            _conversations.value = MockData.conversations
            return@runCatching
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        remote.promoteChatModerator(session.userId, conversationId.requireThreadId(), userId)
        refreshThread(conversationId, force = true)
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun demoteModerator(conversationId: String, userId: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.promoteModerator(conversationId, userId)
            _conversations.value = MockData.conversations
            return@runCatching
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        remote.demoteChatModerator(session.userId, conversationId.requireThreadId(), userId)
        refreshThread(conversationId, force = true)
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun removeParticipant(conversationId: String, userId: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.removeParticipant(conversationId, userId)
            _conversations.value = MockData.conversations
            return@runCatching
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        remote.removeChatParticipant(session.userId, conversationId.requireThreadId(), userId)
        refreshThread(conversationId, force = true)
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun blockParticipant(conversationId: String, userId: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.blockParticipant(conversationId, userId)
            _conversations.value = MockData.conversations
            return@runCatching
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        remote.blockChatParticipant(session.userId, conversationId.requireThreadId(), userId)
        updateConversation(conversationId) { it.copy(blockedUserIds = (it.blockedUserIds + userId).distinct()) }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun reportMessage(messageId: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) return@runCatching
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        remote.reportChatMessage(session.userId, messageId)
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun leaveConversation(conversationId: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.leaveConversation(conversationId, MockData.currentUser.id)
            _conversations.value = MockData.conversations
            return@runCatching
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        remote.leaveChatThread(session.userId, conversationId.requireThreadId())
        removeConversation(session.userId, conversationId)
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun hideConversation(conversationId: String): Result<Unit> = deleteConversation(conversationId)

    override suspend fun deleteConversation(conversationId: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            val conversation = MockData.conversations.firstOrNull { it.id == conversationId }
            if (conversation != null) _pendingDeletedConversation.value = conversation
            MockData.hideConversation(conversationId)
            _conversations.value = MockData.conversations
            return@runCatching
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val conversation = _conversations.value.firstOrNull { it.id == conversationId }
        remote.deleteChatThread(session.userId, conversationId.requireThreadId())
        if (conversation != null) _pendingDeletedConversation.value = conversation
        removeConversation(session.userId, conversationId)
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun restorePendingDeletedConversation(): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            val conversation = _pendingDeletedConversation.value ?: return@runCatching
            MockData.restoreConversation(conversation.id)
            _pendingDeletedConversation.value = null
            _conversations.value = MockData.conversations
            return@runCatching
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val conversation = _pendingDeletedConversation.value ?: return@runCatching
        remote.restoreChatThread(session.userId, conversation.id.requireThreadId())
        _pendingDeletedConversation.value = null
        refreshAll(session.userId, force = true)
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun finalizePendingDeletedConversation(): Result<Unit> = runCatching {
        _pendingDeletedConversation.value = null
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun editMessage(messageId: String, text: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.editMessage(messageId, text)
            refreshMockMessageStates()
            return@runCatching
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val threadId = messageThreadId(messageId)
        remote.editChatMessage(session.userId, threadId, messageId.toLongOrNull() ?: error("Mensaje no valido"), text)
        refreshLoadedThreads()
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun deleteMessage(messageId: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.deleteMessage(messageId)
            refreshMockMessageStates()
            return@runCatching
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val threadId = messageThreadId(messageId)
        remote.deleteChatMessages(session.userId, threadId, listOf(messageId.toLongOrNull() ?: error("Mensaje no valido")))
        refreshLoadedThreads()
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun toggleFavoriteMessage(messageId: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            MockData.toggleFavoriteMessage(messageId)
            refreshMockMessageStates()
            favoriteMessages.value = MockData.messages.filter { it.isFavorite && !it.isDeleted }
                .sortedByDescending { it.sentAtMillis ?: 0L }
            return@runCatching
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val message = loadedMessages().firstOrNull { it.id == messageId } ?: error("Mensaje no cargado")
        val nextFavorite = !message.isFavorite
        val threadId = message.conversationId.requireThreadId()
        val messageSnapshots = messageStates.values.associateWith { it.value }
        val favoriteSnapshot = favoriteMessages.value
        try {
            applyFavoriteLocally(session.userId, message, nextFavorite)
            remote.setChatFavorite(session.userId, threadId, messageId.toLongOrNull() ?: error("Mensaje no valido"), nextFavorite)
            refreshFavorites(session.userId, force = true)
            refreshLoadedThreads()
        } catch (error: Throwable) {
            messageSnapshots.forEach { (state, messages) -> state.value = messages }
            favoriteMessages.value = favoriteSnapshot
            runCatching { cacheStore.replaceFavoriteMessages(session.userId, favoriteSnapshot) }
            throw error
        }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    override suspend fun forwardMessage(message: Message, conversationIds: List<String>): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            val user = MockData.currentUser
            MockData.forwardMessage(message, conversationIds, user.id, user.displayName)
            _conversations.value = MockData.conversations
            refreshMockMessageStates()
            return@runCatching
        }
        val session = sessionManager.currentSession() ?: error("No hay sesion activa")
        val targets = conversationIds.mapNotNull { it.supabaseThreadIdOrNull() }
        if (targets.isEmpty()) return@runCatching
        remote.forwardChatMessage(session.userId, message.id.toLongOrNull() ?: error("Mensaje no valido"), targets)
        scope.launch {
            refreshAll(session.userId, force = true)
            refreshLoadedThreads()
        }
    }.mapFailureToUserFacing(appContext, R.string.error_backend_generic)

    private fun refreshMockMessageStates() {
        _conversations.value = MockData.conversations
        messageStates.forEach { (conversationId, state) ->
            state.value = if (conversationId == AppDestinations.FavoriteMessagesConversationId) {
                MockData.messages.filter { it.isFavorite && !it.isDeleted }
                    .sortedByDescending { it.sentAtMillis ?: 0L }
            } else {
                MockData.messages.filter { it.conversationId == conversationId }
            }
        }
    }

    private suspend fun restoreCache(profileId: String): Boolean {
        val cachedConversations = cacheStore.cachedConversations(profileId)
        if (cachedConversations.isNotEmpty()) _conversations.value = cachedConversations
        if (cachedConversations.isNotEmpty()) _syncStatus.value = ChatSyncStatus.CacheAvailable
        cacheStore.cachedFavoriteMessages(profileId).takeIf { it.isNotEmpty() }?.let { favoriteMessages.value = it }
        cacheStore.cachedProfileDirectory(profileId).forEach { profilesById[it.id] = it }
        return cachedConversations.isNotEmpty()
    }

    private suspend fun refreshAll(profileId: String, force: Boolean = false) {
        if (AppConfig.USE_MOCK_BACKEND) return
        refreshMutex.withLock {
            if (!deviceNetworkAvailable.value) return@withLock
            _syncStatus.value = ChatSyncStatus.Refreshing
            val now = System.currentTimeMillis()
            if (!force && now - lastFullRefreshAtMillis < FULL_REFRESH_MIN_INTERVAL_MILLIS) return@withLock
            lastFullRefreshAtMillis = now
            runCatching {
                val previous = _conversations.value.associateBy { it.id }
                val payload = remote.getChatInbox(profileId)
                if (sessionManager.currentSession()?.userId != profileId) return@runCatching
                val parsed = parseChatPayload(payload, profileId)
                parsed.profiles.forEach { profilesById[it.id] = it }
                val conversations = parsed.conversations
                    .sortedByDescending { it.updatedAtMillis ?: 0L }
                _conversations.value = conversations
                cacheStore.replaceConversations(profileId, conversations)
                parsed.messagesByConversation.forEach { (conversationId, messages) ->
                    val cachedMessages = cacheStore.cachedMessages(profileId, conversationId)
                    val mergedMessages = reconcileChatMessages(messages, cachedMessages)
                    val displayMessages = attachmentFileCache.resolveCached(profileId, mergedMessages)
                    val state = messagesState(conversationId)
                    if (state.value.isEmpty()) {
                        state.value = displayMessages
                    }
                    cacheStore.replaceMessages(profileId, conversationId, mergedMessages)
                    ackIncomingMessages(messages, ChatMessageStateAckStatus.Delivered, "inbox_refresh")
                }
                emitNotifications(previous, conversations)
                refreshFavorites(profileId, force = force)
                _isRealtimeOnline.value = true
                _syncStatus.value = ChatSyncStatus.Online
            }.onFailure {
                _isRealtimeOnline.value = false
                _syncStatus.value = if (deviceNetworkAvailable.value) ChatSyncStatus.Error else ChatSyncStatus.Offline
            }
        }
    }

    private suspend fun restoreMessagesFromCache(conversationId: String): Boolean {
        if (AppConfig.USE_MOCK_BACKEND) return false
        val session = sessionManager.currentSession() ?: return false
        val state = messagesState(conversationId)
        if (conversationId == AppDestinations.FavoriteMessagesConversationId) {
            val cached = cacheStore.cachedFavoriteMessages(session.userId)
            if (cached.isNotEmpty()) favoriteMessages.value = attachmentFileCache.resolveCached(session.userId, cached)
            state.value = favoriteMessages.value
            return cached.isNotEmpty()
        }
        val cached = cacheStore.cachedMessages(session.userId, conversationId)
        if (cached.isNotEmpty()) state.value = attachmentFileCache.resolveCached(session.userId, cached)
        return cached.isNotEmpty()
    }

    private fun refreshMessagesInBackground(conversationId: String) {
        scope.launch {
            refreshMessages(conversationId)
        }
    }

    private suspend fun refreshMessages(conversationId: String, force: Boolean = false) {
        if (AppConfig.USE_MOCK_BACKEND) return
        val session = sessionManager.currentSession() ?: return
        if (conversationId == AppDestinations.FavoriteMessagesConversationId) {
            refreshFavorites(session.userId, force = true)
        } else {
            refreshThread(conversationId, force = force)
        }
    }

    private suspend fun refreshThread(conversationId: String, force: Boolean = false) {
        if (AppConfig.USE_MOCK_BACKEND) return
        val session = sessionManager.currentSession() ?: return
        val threadId = conversationId.supabaseThreadIdOrNull() ?: return
        val now = System.currentTimeMillis()
        val previous = lastThreadRefreshAtMillis[conversationId] ?: 0L
        if (!force && now - previous < THREAD_REFRESH_MIN_INTERVAL_MILLIS) return
        lastThreadRefreshAtMillis[conversationId] = now
        runCatching {
            val payload = remote.getChatThread(session.userId, threadId)
            if (sessionManager.currentSession()?.userId != session.userId) return@runCatching
            val parsed = parseChatPayload(payload, session.userId)
            parsed.profiles.forEach { profilesById[it.id] = it }
            val conversation = parsed.conversations.firstOrNull()
            if (conversation != null) {
                upsertConversation(session.userId, conversation)
            }
            val incomingMessages = parsed.messages
            val cachedMessages = cacheStore.cachedMessages(session.userId, conversationId)
            val messages = reconcileChatMessages(incomingMessages, cachedMessages)
            val displayMessages = attachmentFileCache.resolveCached(session.userId, messages)
            messagesState(conversationId).value = displayMessages
            cacheStore.replaceMessages(session.userId, conversationId, messages)
            scope.launch {
                val prefetched = attachmentFileCache.prefetchAndResolve(session.userId, messages)
                if (sessionManager.currentSession()?.userId == session.userId) messagesState(conversationId).value = prefetched
            }
            ackIncomingMessages(displayMessages, ChatMessageStateAckStatus.Delivered, "thread_refresh")
            _isRealtimeOnline.value = true
            _syncStatus.value = ChatSyncStatus.Online
        }.onFailure {
            _isRealtimeOnline.value = false
            _syncStatus.value = if (deviceNetworkAvailable.value) ChatSyncStatus.Error else ChatSyncStatus.Offline
        }
    }

    private suspend fun refreshFavorites(profileId: String, force: Boolean = false) {
        if (AppConfig.USE_MOCK_BACKEND) return
        val now = System.currentTimeMillis()
        if (!force && now - lastFavoritesRefreshAtMillis < FAVORITES_REFRESH_MIN_INTERVAL_MILLIS) return
        lastFavoritesRefreshAtMillis = now
        runCatching {
            val payload = remote.getChatFavorites(profileId)
            val parsed = parseChatPayload(payload, profileId)
            parsed.profiles.forEach { profilesById[it.id] = it }
            val cachedMessages = cacheStore.cachedFavoriteMessages(profileId)
            val messages = reconcileChatMessages(
                incoming = parsed.messages,
                existing = cachedMessages,
                retainUnmatchedExisting = false
            )
                .sortedByDescending { it.sentAtMillis ?: 0L }
            val displayMessages = attachmentFileCache.resolveCached(profileId, messages)
            favoriteMessages.value = displayMessages
            messagesState(AppDestinations.FavoriteMessagesConversationId).value = displayMessages
            cacheStore.replaceFavoriteMessages(profileId, messages)
            scope.launch {
                val prefetched = attachmentFileCache.prefetchAndResolve(profileId, messages)
                if (sessionManager.currentSession()?.userId == profileId) favoriteMessages.value = prefetched
            }
        }.onFailure { error ->
            Log.w(TAG, "Could not refresh favorite chat messages", error)
        }
    }

    private suspend fun refreshLoadedThreads() {
        messageStates.keys
            .filter { it != AppDestinations.FavoriteMessagesConversationId }
            .forEach { refreshThread(it, force = true) }
    }

    private fun connectRealtime(session: AuthSession) {
        if (AppConfig.USE_MOCK_BACKEND) return
        if (!appForegroundState.value || !deviceNetworkAvailable.value || !session.isSupabaseAuthenticated()) return
        val accessToken = session.bearerToken
        if (realtimeProfileId == session.userId &&
            realtimeAccessToken == accessToken &&
            (isRealtimeConnecting || _isRealtimeOnline.value)
        ) {
            scheduleRealtimeTokenRefresh(session)
            return
        }
        reconnectJob?.cancel()
        reconnectJob = null
        realtimeTokenRefreshJob?.cancel()
        realtimeTokenRefreshJob = null
        isRealtimeConnecting = true
        _isRealtimeOnline.value = false
        realtimeProfileId = session.userId
        realtimeAccessToken = accessToken
        realtimeReadyProfileId = null
        scheduleRealtimeTokenRefresh(session)
        supabaseRealtimeClient.connect(
            accessToken = accessToken,
            presenceKey = session.userId,
            tables = RealtimeTables,
            onEvent = ::handleRealtimeEvent,
            onStatus = onStatus@ { status ->
                when (status) {
                    RealtimeStatus.Connected, RealtimeStatus.Subscribed -> Unit
                    RealtimeStatus.PostgresReady -> {
                        isRealtimeConnecting = false
                        if (realtimeReadyProfileId == session.userId) return@onStatus
                        realtimeReadyProfileId = session.userId
                        reconnectAttempt = 0
                        _isRealtimeOnline.value = true
                        _syncStatus.value = ChatSyncStatus.Online
                        scope.launch { refreshAll(session.userId) }
                    }
                    RealtimeStatus.Closed, RealtimeStatus.Error -> {
                        isRealtimeConnecting = false
                        _isRealtimeOnline.value = false
                        _syncStatus.value = if (deviceNetworkAvailable.value) ChatSyncStatus.Error else ChatSyncStatus.Offline
                        realtimeAccessToken = null
                        realtimeReadyProfileId = null
                        realtimeTokenRefreshJob?.cancel()
                        realtimeTokenRefreshJob = null
                        scheduleReconnect()
                    }
                }
            },
            onFailure = {
                isRealtimeConnecting = false
                realtimeTokenRefreshJob?.cancel()
                realtimeTokenRefreshJob = null
                scheduleReconnect()
            }
        )
    }

    private fun scheduleReconnect() {
        if (AppConfig.USE_MOCK_BACKEND) return
        if (!appForegroundState.value) return
        if (!deviceNetworkAvailable.value) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            val baseDelay = (2_000L shl reconnectAttempt.coerceAtMost(5)).coerceAtMost(60_000L)
            reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(6)
            delay(baseDelay + Random.nextLong(0L, 1_000L))
            val session = sessionManager.currentSession() ?: return@launch
            realtimeProfileId = null
            refreshAndConnectRealtime(session.userId)
        }
    }

    private fun scheduleRealtimeTokenRefresh(session: AuthSession) {
        if (AppConfig.USE_MOCK_BACKEND) return
        val expiresAt = session.expiresAt ?: return
        if (realtimeTokenRefreshJob?.isActive == true) return
        val nowEpochSeconds = System.currentTimeMillis() / 1000L
        val refreshAtSeconds = expiresAt - REALTIME_REFRESH_LEEWAY_SECONDS
        val delayMillis = (refreshAtSeconds - nowEpochSeconds).coerceAtLeast(0L) * 1000L
        realtimeTokenRefreshJob = scope.launch {
            delay(delayMillis)
            if (!appForegroundState.value) return@launch
            if (!deviceNetworkAvailable.value) return@launch
            val current = sessionManager.currentSession()?.takeIf { it.userId == session.userId }
                ?: run {
                    supabaseRealtimeClient.disconnect()
                    return@launch
                }
            val refreshed = remote.ensureFreshSession(force = false)
                ?: sessionManager.currentSession()?.takeIf { it.userId == current.userId }
            if (refreshed == null) {
                supabaseRealtimeClient.disconnect()
                return@launch
            }
            if (refreshed.bearerToken == current.bearerToken && refreshed.shouldRefresh()) {
                delay(REALTIME_REFRESH_RETRY_MILLIS)
                realtimeTokenRefreshJob = null
                scheduleRealtimeTokenRefresh(refreshed)
                return@launch
            }
            realtimeTokenRefreshJob = null
            connectRealtime(refreshed)
        }
    }

    private suspend fun refreshAndConnectRealtime(profileId: String) {
        if (AppConfig.USE_MOCK_BACKEND) return
        if (!deviceNetworkAvailable.value) return
        remote.ensureFreshSession()
        refreshAll(profileId)
        val freshSession = sessionManager.currentSession()?.takeIf { it.userId == profileId } ?: return
        if (appForegroundState.value) {
            connectRealtime(freshSession)
        }
    }

    private fun stopRealtime() {
        reconnectJob?.cancel()
        reconnectJob = null
        realtimeTokenRefreshJob?.cancel()
        realtimeTokenRefreshJob = null
        isRealtimeConnecting = false
        realtimeProfileId = null
        realtimeAccessToken = null
        realtimeReadyProfileId = null
        reconnectAttempt = 0
        _isRealtimeOnline.value = false
        supabaseRealtimeClient.disconnect()
    }

    private fun handleRealtimeEvent(event: RealtimeRawEvent) {
        if (AppConfig.USE_MOCK_BACKEND) return
        val session = sessionManager.currentSession() ?: return
        scope.launch {
            val active = _activeConversationId.value
            val threadId = event.record?.long("thread_id") ?: event.oldRecord?.long("thread_id")
            val conversationId = threadId?.let(::supabaseChatConversationId)
            val table = event.table.orEmpty()
            Log.d(TAG, "Realtime event table=$table thread=$threadId active=$active")
            when (table) {
                "chat_message_favorites" -> refreshFavorites(session.userId)
                "chat_messages", "chat_attachments", "chat_message_reads", "chat_message_states" -> {
                    conversationId?.let { refreshThread(it) }
                    if (active != null &&
                        active != AppDestinations.FavoriteMessagesConversationId &&
                        active != conversationId
                    ) {
                        refreshThread(active)
                    }
                    refreshAll(session.userId)
                }
                "chat_threads", "chat_participants" -> {
                    conversationId?.let { refreshThread(it) }
                    refreshAll(session.userId)
                }
                else -> refreshAll(session.userId)
            }
        }
    }

    private suspend fun uploadAttachment(
        profileId: String,
        threadId: Long,
        uri: String,
        attachmentName: String?,
        attachmentMimeType: String?
    ): Long {
        val media = mediaUploadOptimizer.prepareAttachmentUpload(uri, attachmentName, attachmentMimeType)
        val upload = remote.uploadChatAttachment(profileId, media.bytes, media.extension, media.mimeType, media.fileName)
        val fileUrl = upload.publicUrl ?: error("No se pudo subir el adjunto")
        val registered = remote.registerChatAttachment(
            profileId = profileId,
            threadId = threadId,
            fileUrl = fileUrl,
            storagePath = upload.key,
            mimeType = media.mimeType,
            name = media.fileName,
            sizeBytes = media.bytes.size.toLong(),
            extension = media.extension
        )
        return registered.obj.long("id")
            ?: registered.obj.obj("file")?.long("id")
            ?: error("No se pudo registrar el adjunto")
    }

    private suspend fun upsertConversation(profileId: String, conversation: Conversation) {
        _conversations.value = (listOf(conversation) + _conversations.value.filterNot { it.id == conversation.id })
            .distinctBy { it.id }
            .sortedByDescending { it.updatedAtMillis ?: 0L }
        cacheStore.upsertConversation(profileId, conversation)
    }

    private suspend fun removeConversation(profileId: String, conversationId: String) {
        _conversations.value = _conversations.value.filterNot { it.id == conversationId }
        cacheStore.removeConversation(profileId, conversationId)
        notificationFactory.clearChatMessage(conversationId)
    }

    private fun updateConversation(conversationId: String, transform: (Conversation) -> Conversation) {
        _conversations.value = _conversations.value.map { conversation ->
            if (conversation.id == conversationId) transform(conversation) else conversation
        }
    }

    private fun emitNotifications(previous: Map<String, Conversation>, updated: List<Conversation>) {
        // Native background notifications are handled by Firebase push. Realtime updates
        // only refresh local state here, otherwise they can overwrite the localized FCM payload.
    }

    private fun messagesState(conversationId: String): MutableStateFlow<List<Message>> =
        if (conversationId == AppDestinations.FavoriteMessagesConversationId) {
            favoriteMessages
        } else {
            messageStates.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
        }

    private fun loadedMessages(): List<Message> =
        messageStates.values.flatMap { it.value } + favoriteMessages.value

    private suspend fun applyFavoriteLocally(profileId: String, message: Message, favorite: Boolean) {
        messageStates.values.forEach { state ->
            state.value = state.value.map { loaded ->
                if (loaded.id == message.id) loaded.copy(isFavorite = favorite) else loaded
            }
        }
        val updatedMessage = message.copy(isFavorite = favorite)
        val updatedFavorites = if (favorite) {
            (listOf(updatedMessage) + favoriteMessages.value.filterNot { it.id == message.id })
                .sortedByDescending { it.sentAtMillis ?: 0L }
        } else {
            favoriteMessages.value.filterNot { it.id == message.id }
        }
        favoriteMessages.value = updatedFavorites
        cacheStore.replaceFavoriteMessages(profileId, updatedFavorites)
    }

    private fun messageThreadId(messageId: String): Long =
        loadedMessages()
            .firstOrNull { it.id == messageId }
            ?.conversationId
            ?.requireThreadId()
            ?: error("Mensaje no cargado")

    private suspend fun mergeChatPayload(
        payload: JsonElement,
        expectedProfileId: String,
        confirmedOutgoing: PendingOutgoingMessage? = null
    ) {
        val session = sessionManager.currentSession()?.takeIf { it.userId == expectedProfileId } ?: return
        val parsed = parseChatPayload(payload, session.userId)
        parsed.profiles.forEach { profilesById[it.id] = it }
        parsed.conversations
            .forEach {
                if (sessionManager.currentSession()?.userId != expectedProfileId) return
                upsertConversation(session.userId, it)
            }
        parsed.messagesByConversation.forEach { (conversationId, parsedIncomingMessages) ->
            if (sessionManager.currentSession()?.userId != expectedProfileId) return
            val incomingMessages = parsedIncomingMessages.withConfirmedOutgoingIdentity(
                confirmedOutgoing?.takeIf { it.conversationId == conversationId }
            )
            val cachedMessages = cacheStore.cachedMessages(session.userId, conversationId)
            val merged = reconcileChatMessages(incomingMessages, cachedMessages)
            messagesState(conversationId).value = attachmentFileCache.resolveCached(session.userId, merged)
            cacheStore.replaceMessages(session.userId, conversationId, merged)
        }
    }

    private fun List<Message>.withConfirmedOutgoingIdentity(
        outgoing: PendingOutgoingMessage?
    ): List<Message> {
        outgoing ?: return this
        if (any { it.clientMessageId == outgoing.clientMessageId }) return this

        val eligibleIndices = indices.filter { index ->
            val message = this[index]
            message.isMine && message.clientMessageId.isNullOrBlank()
        }
        val exactIndices = eligibleIndices.filter { index ->
            val message = this[index]
            message.text == outgoing.text &&
                message.replyToMessageId == outgoing.replyToMessageId &&
                (outgoing.attachmentName.isNullOrBlank() || message.attachmentName == outgoing.attachmentName)
        }
        val confirmedIndex = when {
            exactIndices.size == 1 -> exactIndices.single()
            eligibleIndices.size == 1 -> eligibleIndices.single()
            else -> return this // Ambiguous payload: never assign one client identity to two rows.
        }
        return mapIndexed { index, message ->
            if (index == confirmedIndex) {
                message.copy(
                    clientMessageId = outgoing.clientMessageId,
                    isPending = false,
                    isLocalEcho = false,
                    deliveryState = MessageDeliveryState.Sent
                )
            } else {
                message
            }
        }
    }

    private fun parseChatPayload(payload: JsonElement, currentProfileId: String): ParsedChatPayload {
        val envelope = parseChatRpcPayloadEnvelope(payload)
        val profiles = envelope.profileRecords().map { it.toCommunityProfile() }
        val conversations = envelope.toChatRpcConversations(currentProfileId)
        val messages = envelope.toChatRpcMessages(currentProfileId)
        val messagesByConversation = reconcileChatMessages(
            incoming = messages
        )
            .groupBy { it.conversationId }
        return ParsedChatPayload(
            conversations = conversations,
            messages = messages,
            profiles = profiles,
            messagesByConversation = messagesByConversation
        )
    }

    private fun ackIncomingMessages(
        messages: List<Message>,
        status: ChatMessageStateAckStatus,
        source: String
    ) {
        val messageIds = messages.mapNotNull { message ->
            message.id.toLongOrNull()?.takeIf { !message.isMine && !message.isDeleted }
        }
        if (messageIds.isEmpty()) return
        scope.launch {
            messageStateAckManager.markMessages(messageIds, status, source)
        }
    }

    private fun ChatRpcProfile.toCommunityProfile(): CommunityProfile = CommunityProfile(
            id = id,
            display_name = displayName ?: name,
            nombre = name,
            avatar_url = avatarUrl,
            neighborhood = neighborhood,
            phone_local = phoneLocal,
            country_code = countryCode,
        )

    private fun JsonObject.toConversationCandidate(): ChatConversationCandidate? {
        val profileId = string("profile_id") ?: string("id") ?: return null
        val existingThreadId = long("existing_thread_id")
        return ChatConversationCandidate(
            profileId = profileId,
            displayName = string("display_name") ?: string("name") ?: "Usuario",
            neighborhood = string("neighborhood").orEmpty(),
            phone = "",
            avatarUrl = string("avatar_url"),
            sectionKey = string("section_key") ?: "other",
            neighborhoodGroup = string("neighborhood_group").orEmpty(),
            existingConversationId = existingThreadId?.let(::supabaseChatConversationId)
        )
    }

    private fun CommunityProfile.toUser(): User =
        User(
            id = id,
            email = "${country_code.orEmpty()}${phone_local.orEmpty()}@phone.quata.app",
            displayName = displayName(),
            neighborhood = neighborhood ?: barrio.orEmpty(),
            avatarUrl = avatar_url ?: avatar,
            isAdmin = is_admin == true,
            isOfficial = is_official == true
        )

    private fun CommunityProfile.displayName(): String =
        display_name?.takeIf { it.isNotBlank() }
            ?: nombre?.takeIf { it.isNotBlank() }
            ?: phone_local?.takeIf { it.isNotBlank() }
            ?: "Usuario"

    private val JsonElement.obj: JsonObject
        get() = jsonObject

    private val JsonElement.objOrNull: JsonObject?
        get() = this as? JsonObject

    private fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject

    private fun JsonObject.array(key: String): List<JsonElement> =
        (get(key) as? JsonArray)?.toList().orEmpty()

    private fun JsonObject.string(key: String): String? =
        (get(key) as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(key: String): Long? =
        (get(key) as? JsonPrimitive)?.longOrNull

    private fun JsonObject.int(key: String): Int? =
        (get(key) as? JsonPrimitive)?.intOrNull

    private fun JsonObject.boolean(key: String): Boolean? =
        (get(key) as? JsonPrimitive)?.booleanOrNull

    private fun JsonElement.stringOrNull(): String? =
        (this as? JsonPrimitive)?.contentOrNull

    private fun String.requireThreadId(): Long =
        supabaseThreadIdOrNull() ?: error("Conversacion no valida")

    private fun String.normalizeName(): String {
        val normalized = Normalizer.normalize(trim(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
        return normalized.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    private data class ParsedChatPayload(
        val conversations: List<Conversation>,
        val messages: List<Message>,
        val profiles: List<CommunityProfile>,
        val messagesByConversation: Map<String, List<Message>>
    )

    private companion object {
        const val TAG = "QuataChat"
        const val FULL_REFRESH_MIN_INTERVAL_MILLIS = 8_000L
        const val FAVORITES_REFRESH_MIN_INTERVAL_MILLIS = 8_000L
        const val THREAD_REFRESH_MIN_INTERVAL_MILLIS = 1_200L
        const val REALTIME_REFRESH_LEEWAY_SECONDS = 115L
        const val REALTIME_REFRESH_RETRY_MILLIS = 60_000L
        const val MAX_OUTBOX_ATTEMPTS = 5
        val RealtimeTables = listOf(
            "chat_threads",
            "chat_participants",
            "chat_messages",
            "chat_attachments",
            "chat_message_favorites",
            "chat_message_reads",
            "chat_message_states"
        )
    }
}
