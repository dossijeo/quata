package com.quata.feature.chat.presentation.chat

import com.quata.core.common.AppDispatchers
import com.quata.core.model.Conversation
import com.quata.core.model.MessageDeliveryState
import com.quata.core.model.Message
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.feature.chat.domain.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(
    private val conversationId: String,
    private val repository: ChatRepository,
    private val isFavoritesConversation: Boolean = false,
    private val text: (ChatText) -> String = { "Chat error" },
    private val composerDraftStore: ChatComposerDraftStore? = null,
    dispatchers: AppDispatchers = AppDispatchers()
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)
    private val composerDraftScope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val _uiState = MutableStateFlow(ChatUiState(isViewportRestoreReady = composerDraftStore == null))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private var backendMessages: List<Message> = emptyList()
    private var localEchoMessages: List<Message> = emptyList()
    private var optimisticEditedMessages: Map<String, Message> = emptyMap()
    private var retryDraft: OutgoingDraft? = null
    private var participantCandidateSearchJob: Job? = null
    private var participantCandidatePageJob: Job? = null
    private var forwardCandidateSearchJob: Job? = null
    private var forwardCandidatePageJob: Job? = null
    private var isConversationVisible = false
    private var messageObservationJob: Job? = null
    private var composerDraftWriteJob: Job? = null
    private var viewportWriteJob: Job? = null
    private var composerAttachmentStageJob: Job? = null
    private var composerAttachmentSelection = 0L
    private var composerRestoreHistoryJob: Job? = null
    private var composerRevision = 0L
    private val composerDraftLease = CompletableDeferred<ChatComposerDraftLease?>()
    private var pendingComposerRestore: PendingComposerRestore? = null
    private var lastQueuedViewport: ChatConversationViewport? = null
    private var messageObservationFailure: String? = null
    private var historyLoadFailure: String? = null
    private var activeSelectedMessageMutation: SelectedMessageMutation? = null
    private var pendingSelectedMessageMutationRetry: SelectedMessageMutation? = null
    private var activeEditMutation: EditMessageMutation? = null
    private var pendingEditMutationRetry: EditMessageMutation? = null

    init {
        _uiState.value = _uiState.value.copy(currentUser = repository.currentUser())
        composerDraftScope.launch {
            val lease = runCatching {
                val store = composerDraftStore ?: return@runCatching null
                repository.currentActorId()?.let { store.open(it) }
            }.getOrNull()
            composerDraftLease.complete(lease)
            val viewport = runCatching {
                val store = composerDraftStore ?: return@runCatching null
                lease?.let { store.readViewport(it, conversationId) }
            }.getOrNull()
            lastQueuedViewport = viewport
            _uiState.value = _uiState.value.copy(
                restoredViewport = viewport,
                isViewportRestoreReady = true,
            )
        }
        restoreComposerDraft()
        scope.launch {
            if (isConversationVisible && repository.isAppForeground.value) {
                repository.markConversationRead(conversationId)
            }
        }
        scope.launch {
            repository.isAppForeground.collect { isForeground ->
                if (isForeground && isConversationVisible) {
                    repository.markConversationRead(conversationId)
                }
            }
        }
        scope.launch {
            repository.syncStatus.collect { status -> _uiState.value = _uiState.value.copy(syncStatus = status) }
        }
        scope.launch {
            repository.typingProfileIds.collect { profileIds ->
                _uiState.value = _uiState.value.copy(typingProfileIds = profileIds)
            }
        }
        scope.launch {
            repository.observeConversations()
                .catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = text(ChatText.LoadConversations)
                    )
                }
                .collect { conversations ->
                    _uiState.value = _uiState.value.copy(
                        conversation = conversations.firstOrNull { it.id == conversationId },
                        currentUser = repository.currentUser(),
                    )
                }
        }
        observeMessages()
        scope.launch {
            repository.observeParticipantCandidates()
                .catch {
                    _uiState.value = _uiState.value.copy(error = text(ChatText.LoadCandidates))
                }
                .collect { candidates ->
                    _uiState.value = _uiState.value.copy(participantCandidates = candidates)
                }
        }
    }

    private fun observeMessages() {
        messageObservationJob?.cancel()
        messageObservationJob = scope.launch {
            repository.observeMessages(conversationId)
                .catch {
                    messageObservationFailure = text(ChatText.LoadMessages)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        messageLoadFailure = currentMessageLoadFailure(),
                    )
                }
                .collect { messages ->
                    backendMessages = if (isFavoritesConversation) {
                        messages
                    } else {
                        messages.filter { it.conversationId == conversationId }
                    }
                    localEchoMessages = localEchoMessages.filterNot { local ->
                        messages.any { remote -> remote.matchesLocalEcho(local) }
                    }
                    optimisticEditedMessages = optimisticEditedMessages.filter { (messageId, optimistic) ->
                        optimistic.isPending ||
                            messages.none { remote ->
                                remote.id == messageId &&
                                    remote.text == optimistic.text &&
                                    remote.isEdited
                            }
                    }
                    publishMessages(isLoading = false)
                    messageObservationFailure = null
                    _uiState.value = _uiState.value.copy(
                        currentUser = repository.currentUser(),
                        hasReceivedMessageSnapshot = true,
                        messageLoadFailure = currentMessageLoadFailure(),
                    )
                    tryRestoreComposerDraft()
                    if (isConversationVisible && repository.isAppForeground.value) {
                        repository.markConversationRead(conversationId)
                    }
                }
        }
    }

    fun onEvent(event: ChatUiEvent) {
        when (event) {
            is ChatUiEvent.MessageChanged -> {
                _uiState.value = _uiState.value.copy(messageText = event.value)
                repository.setTyping(conversationId, event.value.isNotBlank())
                persistCurrentComposerDraft(event.value)
            }
            is ChatUiEvent.AttachmentSelected -> stageComposerAttachment(event)
            is ChatUiEvent.ParticipantSearchChanged -> onParticipantCandidateQueryChanged(event.value)
            is ChatUiEvent.ParticipantSelectionToggled -> toggleParticipant(event.userId)
            is ChatUiEvent.MessageSelected -> _uiState.value = _uiState.value.copy(selectedMessageId = event.messageId)
            is ChatUiEvent.ForwardProfileToggled -> toggleForwardProfile(event.profileId)
            is ChatUiEvent.ConversationMutedChanged -> setMuted(event.muted)
            is ChatUiEvent.MemberInvitesChanged -> setMemberInvitesEnabled(event.enabled)
            ChatUiEvent.OpenAddParticipants -> openAddParticipantsPicker()
            ChatUiEvent.CloseAddParticipants -> closeAddParticipantsPicker()
            ChatUiEvent.AddSelectedParticipants -> addParticipants()
            ChatUiEvent.StartReply -> startReply()
            ChatUiEvent.ClearReply -> {
                _uiState.value = _uiState.value.copy(replyToMessage = null)
                persistPlainComposerDraft(_uiState.value.messageText)
            }
            ChatUiEvent.StartEdit -> startEdit()
            ChatUiEvent.CancelEdit -> {
                _uiState.value = _uiState.value.copy(editingMessage = null, messageText = "")
                pendingEditMutationRetry = null
                persistPlainComposerDraft("")
            }
            ChatUiEvent.ToggleFavoriteSelected -> toggleFavoriteSelected()
            ChatUiEvent.DeleteSelectedMessage -> deleteSelectedMessage()
            ChatUiEvent.ReportSelectedMessage -> reportSelectedMessage()
            ChatUiEvent.RetryMessageMutation -> retryMessageMutation()
            ChatUiEvent.ClearNotice -> _uiState.value = _uiState.value.copy(notice = null)
            ChatUiEvent.ClearError -> {
                pendingSelectedMessageMutationRetry = null
                _uiState.value = _uiState.value.copy(error = null, messageMutationRetry = null)
            }
            is ChatUiEvent.ShowNotice -> _uiState.value = _uiState.value.copy(notice = event.message, error = null)
            is ChatUiEvent.ShowError -> _uiState.value = _uiState.value.copy(error = event.message)
            ChatUiEvent.OpenForwardDialog -> openForwardPicker()
            ChatUiEvent.CloseForwardDialog -> closeForwardPicker()
            ChatUiEvent.SendForward -> sendForward()
            is ChatUiEvent.PromoteModerator -> promoteModerator(event.userId)
            is ChatUiEvent.DemoteModerator -> demoteModerator(event.userId)
            is ChatUiEvent.RemoveParticipant -> removeParticipant(event.userId)
            is ChatUiEvent.BlockParticipant -> blockParticipant(event.userId)
            ChatUiEvent.LeaveConversation -> leaveConversation()
            ChatUiEvent.HideConversation -> hideConversation()
            ChatUiEvent.DeleteConversation -> deleteConversation()
            ChatUiEvent.Send -> send()
            ChatUiEvent.ClearAttachment -> clearComposerAttachment()
        }
    }

    fun setConversationVisible(visible: Boolean) {
        if (isConversationVisible == visible) return
        isConversationVisible = visible
        repository.setConversationVisible(conversationId, visible)
        if (!visible) repository.setTyping(conversationId, false)
        if (visible && repository.isAppForeground.value) {
            scope.launch { repository.markConversationRead(conversationId) }
        }
    }

    fun cleanupEmptyConversationIfNeeded() {
        if (!isFavoritesConversation && backendMessages.isEmpty() && localEchoMessages.isEmpty()) {
            repository.cleanupEmptyConversation(conversationId)
        }
    }

    fun loadOlderMessages(): Boolean {
        if (_uiState.value.isLoadingOlderMessages || !_uiState.value.hasMoreHistory) return false
        _uiState.value = _uiState.value.copy(isLoadingOlderMessages = true)
        scope.launch {
            repository.loadOlderMessages(conversationId)
                .onSuccess { hasMore ->
                    historyLoadFailure = null
                    _uiState.value = _uiState.value.copy(
                        isLoadingOlderMessages = false,
                        hasMoreHistory = hasMore,
                        messageLoadFailure = currentMessageLoadFailure(),
                    )
                }
                .onFailure {
                    historyLoadFailure = text(ChatText.LoadMessages)
                    _uiState.value = _uiState.value.copy(
                        isLoadingOlderMessages = false,
                        messageLoadFailure = currentMessageLoadFailure(),
                    )
                }
        }
        return true
    }

    /** Reattaches the authenticated message flow only after a user explicitly requests a retry. */
    fun retryMessageLoading() = retryMessageLoading(retryHistory = true)

    fun retryFocusedMessageLoading() = retryMessageLoading(retryHistory = false)

    private fun retryMessageLoading(retryHistory: Boolean) {
        val retryObservation = messageObservationFailure != null
        val retryOlderPage = historyLoadFailure != null
        messageObservationFailure = null
        historyLoadFailure = null
        _uiState.value = _uiState.value.copy(messageLoadFailure = null, error = null)
        if (retryObservation || !retryOlderPage) observeMessages()
        if (retryOlderPage && retryHistory) loadOlderMessages()
    }

    private fun currentMessageLoadFailure(): String? = messageObservationFailure ?: historyLoadFailure

    fun retryPendingMessage(clientMessageId: String) {
        scope.launch {
            repository.retryPendingMessage(clientMessageId)
                .onFailure { error -> _uiState.value = _uiState.value.copy(error = error.message) }
        }
    }

    fun consumeRestoredViewport(preserveUntilUserScroll: Boolean = false) {
        if (_uiState.value.restoredViewport != null) {
            _uiState.value = _uiState.value.copy(
                restoredViewport = null,
                isViewportFallbackProtected =
                    _uiState.value.isViewportFallbackProtected || preserveUntilUserScroll,
            )
        }
    }

    fun discardRestoredViewport() {
        _uiState.value = _uiState.value.copy(
            restoredViewport = null,
            isViewportRestoreReady = true,
            isViewportFallbackProtected = false,
        )
        queueViewportWrite(null)
    }

    fun allowViewportPersistenceAfterUserScroll() {
        if (_uiState.value.isViewportFallbackProtected) {
            _uiState.value = _uiState.value.copy(isViewportFallbackProtected = false)
        }
    }

    fun persistViewport(viewport: ChatConversationViewport) {
        if (
            !uiState.value.isViewportRestoreReady ||
            uiState.value.isViewportFallbackProtected ||
            viewport == lastQueuedViewport
        ) return
        queueViewportWrite(viewport)
    }

    private fun send() {
        val stateAtSend = _uiState.value
        val text = stateAtSend.messageText
        val attachmentCacheKey = stateAtSend.attachmentCacheKey
        val attachmentUri = stateAtSend.attachmentUri
        val attachmentName = stateAtSend.attachmentName
        val attachmentMimeType = stateAtSend.attachmentMimeType
        if (text.isBlank() && attachmentUri.isNullOrBlank()) return

        val currentUserId = stateAtSend.currentUser?.id
        if (currentUserId != null && currentUserId in stateAtSend.conversation?.blockedUserIds.orEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Has sido bloqueado de esta conversacion")
            return
        }

        val editingMessage = stateAtSend.editingMessage
        val editMutation = if (editingMessage != null) {
            val actorId = currentUserId ?: return
            if (activeEditMutation != null) return
            pendingEditMutationRetry
                ?.takeIf { it.matches(editingMessage.id, editingMessage.conversationId, text, actorId) }
                ?: EditMessageMutation(
                    messageId = editingMessage.id,
                    conversationId = editingMessage.conversationId,
                    text = text,
                    actorId = actorId,
                    clientMutationId = newMessageMutationId(),
                )
        } else {
            null
        }
        if (editMutation != null) activeEditMutation = editMutation

        val sendRevision = composerRevision
        attachmentCacheKey?.let { composerDraftStore?.retainAttachment(it) }
        scope.launch {
            try {
                val replyToMessage = stateAtSend.replyToMessage
                val draft = retryDraft
                    ?.takeIf { it.matches(text, attachmentCacheKey, attachmentUri, attachmentName, attachmentMimeType, replyToMessage) }
                    ?: OutgoingDraft(
                        text = text,
                        attachmentCacheKey = attachmentCacheKey,
                        attachmentUri = attachmentUri,
                        attachmentName = attachmentName,
                        attachmentMimeType = attachmentMimeType,
                        replyToMessage = replyToMessage,
                        clientMessageId = newClientMessageId()
                    )
                retryDraft = null
                val optimisticMessage = if (editingMessage == null) createOptimisticMessage(draft) else null
                val optimisticEditedMessage = editingMessage?.copy(
                    text = text,
                    isEdited = true,
                    isPending = true
                )
                optimisticMessage?.let { message ->
                    localEchoMessages = localEchoMessages + message
                    _uiState.value = _uiState.value.copy(
                        messageText = "",
                        attachmentCacheKey = null,
                        attachmentUri = null,
                        attachmentName = null,
                        attachmentMimeType = null,
                        replyToMessage = null,
                        selectedMessageId = null
                    )
                    publishMessages(isLoading = false)
                }
                optimisticEditedMessage?.let { message ->
                    optimisticEditedMessages = optimisticEditedMessages + (message.id to message)
                    _uiState.value = _uiState.value.copy(
                        messageText = "",
                        editingMessage = null,
                        selectedMessageId = null
                    )
                    publishMessages(isLoading = false)
                }

                val sendOperation: suspend () -> Result<Unit> = {
                    when {
                        editMutation != null -> repository.editMessage(
                            messageId = editMutation.messageId,
                            text = editMutation.text,
                            conversationId = editMutation.conversationId,
                            clientMutationId = editMutation.clientMutationId,
                            expectedActorId = editMutation.actorId,
                        )
                        replyToMessage != null -> repository.sendReply(
                            conversationId = conversationId,
                            text = text,
                            replyTo = replyToMessage,
                            attachmentUri = attachmentUri,
                            attachmentName = attachmentName,
                            attachmentMimeType = attachmentMimeType,
                            clientMessageId = draft.clientMessageId
                        )
                        else -> repository.sendMessage(
                            conversationId = conversationId,
                            text = text,
                            attachmentUri = attachmentUri,
                            attachmentName = attachmentName,
                            attachmentMimeType = attachmentMimeType,
                            clientMessageId = draft.clientMessageId
                        )
                    }
                }
                val result = try {
                    if (attachmentCacheKey != null) {
                        val store = composerDraftStore
                        val lease = composerDraftLease.await()
                        if (store != null && lease != null) {
                            store.withAttachmentCustody(lease, sendOperation)
                        } else {
                            sendOperation()
                        }
                    } else {
                        sendOperation()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Result.failure(error)
                }
                result
                    .onSuccess {
                        repository.setTyping(conversationId, false)
                        if (optimisticMessage != null && composerRevision == sendRevision) clearComposerDraft()
                        optimisticMessage?.let { message ->
                            if (repository.isMessagePending(message.clientMessageId.orEmpty())) {
                                markLocalEchoPending(message)
                            } else {
                                markLocalEchoSent(message)
                            }
                        }
                        if (editingMessage != null) {
                            pendingEditMutationRetry = null
                            optimisticEditedMessages = optimisticEditedMessages.mapValues { (messageId, message) ->
                                if (messageId == editingMessage.id) message.copy(isPending = false) else message
                            }
                            publishMessages(isLoading = false)
                            _uiState.value = _uiState.value.copy(
                                messageText = "",
                                editingMessage = null,
                                selectedMessageId = null
                            )
                            if (composerRevision == sendRevision) clearComposerDraft()
                        }
                    }
                    .onFailure { error ->
                        optimisticMessage?.let { failedMessage ->
                            val alreadyConfirmed = backendMessages.any { remote -> remote.matchesLocalEcho(failedMessage) }
                            localEchoMessages = localEchoMessages.filterNot { it.id == failedMessage.id }
                            if (!alreadyConfirmed) {
                                restoreDraftIfComposerIsEmpty(draft)
                            }
                            publishMessages(isLoading = false)
                        }
                        if (editingMessage != null) {
                            pendingEditMutationRetry = editMutation
                            optimisticEditedMessages = optimisticEditedMessages - editingMessage.id
                            restoreEditDraftIfComposerIsEmpty(editingMessage, text)
                            publishMessages(isLoading = false)
                        }
                        _uiState.value = _uiState.value.copy(error = text(ChatText.Send))
                    }
            } finally {
                if (activeEditMutation === editMutation) activeEditMutation = null
                if (attachmentCacheKey != null) {
                    withContext(NonCancellable) {
                        val store = composerDraftStore
                        val lease = composerDraftLease.await()
                        if (store != null && lease != null) {
                            store.releaseAttachment(lease, conversationId, attachmentCacheKey)
                        }
                    }
                }
            }
        }
    }

    private fun publishMessages(isLoading: Boolean = _uiState.value.isLoading) {
        val editedBackendMessages = backendMessages.map { message ->
            optimisticEditedMessages[message.id] ?: message
        }
        val visibleMessages = (editedBackendMessages + localEchoMessages)
            .filter { isFavoritesConversation || it.conversationId == conversationId }
            .distinctBy(Message::composeKey)
            .withIndex()
            .sortedWith(
                compareBy<IndexedValue<Message>> { it.value.visibleSortMillis() }
                    .thenBy { it.index }
            )
            .map { it.value }
        _uiState.value = _uiState.value.copy(messages = visibleMessages, isLoading = isLoading)
    }

    private fun Message.visibleSortMillis(): Long =
        if (isLocalEcho) Long.MAX_VALUE else sentAtMillis ?: Long.MAX_VALUE

    private fun createOptimisticMessage(draft: OutgoingDraft): Message {
        val currentUser = _uiState.value.currentUser
        val now = currentEpochMillis()
        return Message(
            id = "local:${draft.clientMessageId}",
            conversationId = conversationId,
            senderId = currentUser?.id.orEmpty(),
            senderName = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: text(ChatText.You),
            text = draft.text,
            sentAt = now.toString(),
            sentAtMillis = now,
            isMine = true,
            isRead = false,
            replyToMessageId = draft.replyToMessage?.id,
            replyToSenderName = draft.replyToMessage?.senderName,
            replyToText = draft.replyToMessage?.text,
            attachmentUri = draft.attachmentUri,
            attachmentName = draft.attachmentName,
            attachmentMimeType = draft.attachmentMimeType,
            clientMessageId = draft.clientMessageId,
            isPending = true,
            isLocalEcho = true,
            deliveryState = MessageDeliveryState.Pending
        )
    }

    private fun markLocalEchoSent(message: Message) {
        localEchoMessages = localEchoMessages.map { local ->
            if (local.id == message.id) {
                local.copy(isPending = false, deliveryState = MessageDeliveryState.Sent)
            } else {
                local
            }
        }.filterNot { local ->
            backendMessages.any { remote -> remote.matchesLocalEcho(local) }
        }
        publishMessages(isLoading = false)
    }

    private fun markLocalEchoPending(message: Message) {
        localEchoMessages = localEchoMessages.map { local ->
            if (local.id == message.id) {
                local.copy(isPending = true, deliveryState = MessageDeliveryState.Pending)
            } else {
                local
            }
        }
        publishMessages(isLoading = false)
    }

    private fun restoreDraftIfComposerIsEmpty(draft: OutgoingDraft) {
        val state = _uiState.value
        if (state.messageText.isNotBlank() || state.attachmentUri != null) return
        val restorableDraft = if (draft.attachmentCacheKey == null) {
            draft.copy(
                attachmentUri = null,
                attachmentName = null,
                attachmentMimeType = null,
            )
        } else {
            draft
        }
        retryDraft = restorableDraft
        _uiState.value = state.copy(
            messageText = restorableDraft.text,
            attachmentCacheKey = restorableDraft.attachmentCacheKey,
            attachmentUri = restorableDraft.attachmentUri,
            attachmentName = restorableDraft.attachmentName,
            attachmentMimeType = restorableDraft.attachmentMimeType,
            replyToMessage = restorableDraft.replyToMessage
        )
        persistCurrentComposerDraft(restorableDraft.text)
    }

    private fun restoreEditDraftIfComposerIsEmpty(message: Message, text: String) {
        val state = _uiState.value
        if (state.messageText.isNotBlank() || state.attachmentUri != null) return
        _uiState.value = state.copy(
            messageText = text,
            editingMessage = message,
            selectedMessageId = message.id
        )
        persistCurrentComposerDraft(text)
    }

    private fun restoreComposerDraft() {
        val store = composerDraftStore ?: return
        val revisionAtStart = composerRevision
        scope.launch {
            val lease = composerDraftLease.await() ?: return@launch
            val restored = store.readRecord(lease, conversationId) ?: return@launch
            pendingComposerRestore = PendingComposerRestore(restored, revisionAtStart)
            tryRestoreComposerDraft()
        }
    }

    private fun tryRestoreComposerDraft() {
        val pending = pendingComposerRestore ?: return
        val state = _uiState.value
        if (
            composerRevision != pending.revision ||
            state.messageText.isNotEmpty() ||
            state.attachmentUri != null ||
            state.editingMessage != null ||
            state.replyToMessage != null
        ) {
            pendingComposerRestore = null
            return
        }
        if (pending.draft.mode == ChatComposerDraftMode.Plain) {
            pendingComposerRestore = null
            _uiState.value = state.withRestoredComposerDraft(pending.draft)
            return
        }
        if (!state.hasReceivedMessageSnapshot) return

        val target = state.messages.firstOrNull { it.id == pending.draft.targetMessageId }
        if (target != null) {
            val valid = !target.isLocalEcho && !target.isDeleted &&
                (pending.draft.mode != ChatComposerDraftMode.Edit || target.isMine)
            if (!valid) {
                discardPendingComposerRestore()
                return
            }
            pendingComposerRestore = null
            _uiState.value = when (pending.draft.mode) {
                ChatComposerDraftMode.Plain -> state
                ChatComposerDraftMode.Reply -> state.copy(
                    messageText = pending.draft.text,
                    attachmentCacheKey = pending.draft.attachment?.cacheKey,
                    attachmentUri = pending.draft.attachment?.reference,
                    attachmentName = pending.draft.attachment?.name,
                    attachmentMimeType = pending.draft.attachment?.mimeType,
                    replyToMessage = target,
                )
                ChatComposerDraftMode.Edit -> state.copy(
                    messageText = pending.draft.text,
                    attachmentCacheKey = pending.draft.attachment?.cacheKey,
                    attachmentUri = pending.draft.attachment?.reference,
                    attachmentName = pending.draft.attachment?.name,
                    attachmentMimeType = pending.draft.attachment?.mimeType,
                    editingMessage = target,
                    selectedMessageId = null,
                )
            }
            return
        }

        if (!state.hasMoreHistory) {
            discardPendingComposerRestore()
            return
        }
        if (composerRestoreHistoryJob?.isActive == true) return
        composerRestoreHistoryJob = scope.launch {
            _uiState.value = _uiState.value.copy(isLoadingOlderMessages = true)
            var shouldRefreshSnapshot = false
            try {
                repository.loadOlderMessages(conversationId)
                    .onSuccess { hasMore ->
                        _uiState.value = _uiState.value.copy(hasMoreHistory = hasMore)
                        shouldRefreshSnapshot = true
                    }
            } finally {
                _uiState.value = _uiState.value.copy(isLoadingOlderMessages = false)
                composerRestoreHistoryJob = null
            }
            // Repository paging updates its cache before returning. Reattaching forces a fresh
            // authoritative snapshot before either resolving the target or declaring it absent.
            if (shouldRefreshSnapshot) observeMessages()
        }
    }

    private fun discardPendingComposerRestore() {
        pendingComposerRestore = null
        composerRevision += 1
        queueComposerDraftWrite(ChatComposerDraftRecord(""))
    }

    private fun stageComposerAttachment(event: ChatUiEvent.AttachmentSelected) {
        val store = composerDraftStore
        if (store == null) {
            _uiState.value = _uiState.value.copy(
                attachmentCacheKey = null,
                attachmentUri = event.uri,
                attachmentName = event.name,
                attachmentMimeType = event.mimeType,
            )
            return
        }
        composerRevision += 1
        pendingComposerRestore = null
        composerRestoreHistoryJob?.cancel()
        composerRestoreHistoryJob = null
        val selection = ++composerAttachmentSelection
        val previousStage = composerAttachmentStageJob
        composerAttachmentStageJob = composerDraftScope.launch {
            previousStage?.join()
            val lease = composerDraftLease.await() ?: return@launch
            val staged = store.stageAttachment(
                lease = lease,
                uniqueId = newClientMessageId(),
                file = PlatformFile(event.uri, event.name, event.mimeType),
            )
            when (staged) {
                is PlatformResult.Success -> {
                    if (selection != composerAttachmentSelection) {
                        store.discardStagedAttachment(lease, staged.value)
                        return@launch
                    }
                    _uiState.value = _uiState.value.copy(
                        attachmentCacheKey = staged.value.cacheKey,
                        attachmentUri = staged.value.reference,
                        attachmentName = staged.value.name,
                        attachmentMimeType = staged.value.mimeType,
                        error = null,
                    )
                    persistCurrentComposerDraft(_uiState.value.messageText)
                }
                is PlatformResult.Failure, PlatformResult.Cancelled, PlatformResult.Unsupported -> {
                    if (selection == composerAttachmentSelection) {
                        _uiState.value = _uiState.value.copy(error = text(ChatText.PreserveAttachment))
                    }
                }
            }
        }
    }

    private fun clearComposerAttachment() {
        composerAttachmentSelection += 1
        _uiState.value = _uiState.value.copy(
            attachmentCacheKey = null,
            attachmentUri = null,
            attachmentName = null,
            attachmentMimeType = null,
        )
        persistCurrentComposerDraft(_uiState.value.messageText)
    }

    private fun persistCurrentComposerDraft(value: String) {
        val state = _uiState.value
        val attachment = state.composerDraftAttachment()
        val draft = when {
            state.editingMessage != null -> ChatComposerDraftRecord(
                text = value,
                mode = ChatComposerDraftMode.Edit,
                targetMessageId = state.editingMessage.id,
                attachment = attachment,
            )
            state.replyToMessage != null -> ChatComposerDraftRecord(
                text = value,
                mode = ChatComposerDraftMode.Reply,
                targetMessageId = state.replyToMessage.id,
                attachment = attachment,
            )
            else -> ChatComposerDraftRecord(value, attachment = attachment)
        }
        persistComposerDraft(draft)
    }

    private fun persistPlainComposerDraft(value: String) {
        persistComposerDraft(ChatComposerDraftRecord(value, attachment = _uiState.value.composerDraftAttachment()))
    }

    private fun persistComposerDraft(draft: ChatComposerDraftRecord) {
        composerRevision += 1
        pendingComposerRestore = null
        composerRestoreHistoryJob?.cancel()
        composerRestoreHistoryJob = null
        queueComposerDraftWrite(draft)
    }

    private fun clearComposerDraft() {
        composerRevision += 1
        pendingComposerRestore = null
        composerRestoreHistoryJob?.cancel()
        composerRestoreHistoryJob = null
        queueComposerDraftWrite(ChatComposerDraftRecord(""))
    }

    private fun queueViewportWrite(viewport: ChatConversationViewport?) {
        val store = composerDraftStore ?: return
        lastQueuedViewport = viewport
        val previousWrite = viewportWriteJob
        viewportWriteJob = composerDraftScope.launch {
            previousWrite?.join()
            val lease = composerDraftLease.await() ?: return@launch
            if (viewport == null) store.clearViewport(lease, conversationId)
            else store.writeViewport(lease, conversationId, viewport)
        }
    }

    private fun clearConversationLocalState() {
        clearComposerDraft()
        queueViewportWrite(null)
    }

    private fun queueComposerDraftWrite(draft: ChatComposerDraftRecord) {
        val store = composerDraftStore ?: return
        val previousWrite = composerDraftWriteJob
        composerDraftWriteJob = composerDraftScope.launch {
            previousWrite?.join()
            val lease = composerDraftLease.await() ?: return@launch
            if (draft.isEmpty) store.clear(lease, conversationId)
            else store.writeRecord(lease, conversationId, draft)
        }
    }

    private fun Message.matchesLocalEcho(local: Message): Boolean {
        if (!local.isLocalEcho || !isMine) return false
        if (!clientMessageId.isNullOrBlank() && clientMessageId == local.clientMessageId) return true
        val remoteText = text.normalizedEchoText()
        val localText = local.text.normalizedEchoText()
        val sameLink = local.text.echoUrls()
            .takeIf { it.isNotEmpty() }
            ?.let { localUrls -> text.echoUrls().any { it in localUrls } }
            ?: false
        val sameText = remoteText == localText ||
            sameLink ||
            localText.canMatchEnrichedRemoteText() &&
            (remoteText.contains(localText) || localText.contains(remoteText))
        val sameAttachment = local.attachmentName.isNullOrBlank() ||
            attachmentName == local.attachmentName ||
            attachmentMimeType == local.attachmentMimeType
        val remoteTime = sentAtMillis
        val localTime = local.sentAtMillis
        val closeInTime = remoteTime == null || localTime == null ||
            remoteTime in (localTime - LOCAL_ECHO_MATCH_PAST_TOLERANCE_MILLIS)..(localTime + LOCAL_ECHO_MATCH_FUTURE_TOLERANCE_MILLIS)
        return sameText && sameAttachment && closeInTime
    }

    private fun String.normalizedEchoText(): String =
        stripMarkup()
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun String.canMatchEnrichedRemoteText(): Boolean =
        length >= LOCAL_ECHO_ENRICHED_TEXT_MIN_LENGTH || ECHO_URL_REGEX.containsMatchIn(this)

    private fun String.echoUrls(): Set<String> =
        ECHO_URL_REGEX.findAll(this)
            .map { match -> match.value.trimEnd('.', ',', ';', ':', ')', ']', '>', '"', '\'') }
            .toSet()

    private fun toggleParticipant(userId: String) {
        val current = _uiState.value.selectedParticipantIds
        _uiState.value = _uiState.value.copy(
            selectedParticipantIds = if (userId in current) current - userId else current + userId
        )
    }

    private fun setMuted(muted: Boolean) = scope.launch {
        val previousConversation = _uiState.value.conversation
        _uiState.value = _uiState.value.copy(
            conversation = previousConversation?.copy(isMuted = muted),
            isConversationActionInProgress = true
        )
        repository.setConversationMuted(conversationId, muted)
            .onSuccess {
                _uiState.value = _uiState.value.copy(isConversationActionInProgress = false)
            }
            .onFailure {
                _uiState.value = _uiState.value.copy(
                    conversation = previousConversation,
                    isConversationActionInProgress = false,
                    error = text(ChatText.Update)
                )
            }
    }

    private fun setMemberInvitesEnabled(enabled: Boolean) = scope.launch {
        _uiState.value = _uiState.value.copy(isConversationActionInProgress = true)
        repository.setMemberInvitesEnabled(conversationId, enabled)
            .onSuccess { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false) }
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = text(ChatText.Update)) }
    }

    fun onParticipantCandidateQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(
            participantSearch = query,
            participantCandidateQuery = query,
            participantConversationCandidates = emptyList(),
            participantCandidateHasMore = true,
            participantCandidateNextOffset = 0,
            participantCandidateError = null
        )
        participantCandidateSearchJob?.cancel()
        participantCandidateSearchJob = scope.launch {
            delay(250L)
            loadParticipantConversationCandidates(reset = true)
        }
    }

    fun loadMoreParticipantCandidates() {
        val state = _uiState.value
        if (!state.isAddParticipantsOpen) return
        if (state.isParticipantCandidateInitialLoading || state.isParticipantCandidatePageLoading || !state.participantCandidateHasMore) return
        loadParticipantConversationCandidates(reset = false)
    }

    fun addConversationCandidateParticipant(profileId: String) {
        if (_uiState.value.addingCandidateProfileId != null) return
        if (profileId in _uiState.value.conversation?.participantIds.orEmpty()) return
        _uiState.value = _uiState.value.copy(addingCandidateProfileId = profileId, participantCandidateError = null)
        scope.launch {
            repository.addParticipants(conversationId, listOf(profileId))
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        addingCandidateProfileId = null,
                        participantConversationCandidates = _uiState.value.participantConversationCandidates.filterNot { it.profileId == profileId }
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        addingCandidateProfileId = null,
                        participantCandidateError = text(ChatText.AddParticipant)
                    )
                }
        }
    }

    private fun openAddParticipantsPicker() {
        _uiState.value = _uiState.value.copy(
            isAddParticipantsOpen = true,
            participantSearch = "",
            participantCandidateQuery = "",
            participantConversationCandidates = emptyList(),
            participantCandidateHasMore = true,
            participantCandidateNextOffset = 0,
            participantCandidateError = null,
            addingCandidateProfileId = null
        )
        loadParticipantConversationCandidates(reset = true)
    }

    private fun closeAddParticipantsPicker() {
        participantCandidateSearchJob?.cancel()
        participantCandidatePageJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isAddParticipantsOpen = false,
            participantSearch = "",
            selectedParticipantIds = emptyList(),
            participantCandidateQuery = "",
            participantConversationCandidates = emptyList(),
            participantCandidateHasMore = true,
            participantCandidateNextOffset = 0,
            participantCandidateError = null,
            addingCandidateProfileId = null
        )
    }

    fun onForwardCandidateQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(
            forwardCandidateQuery = query,
            forwardConversationCandidates = emptyList(),
            forwardCandidateHasMore = true,
            forwardCandidateNextOffset = 0,
            forwardCandidateError = null
        )
        forwardCandidateSearchJob?.cancel()
        forwardCandidateSearchJob = scope.launch {
            delay(260L)
            loadForwardConversationCandidates(reset = true)
        }
    }

    fun loadMoreForwardConversationCandidates() {
        if (!_uiState.value.isForwardDialogOpen) return
        if (_uiState.value.isForwardCandidateInitialLoading || _uiState.value.isForwardCandidatePageLoading || !_uiState.value.forwardCandidateHasMore) return
        loadForwardConversationCandidates(reset = false)
    }

    private fun openForwardPicker() {
        selectedMessage()?.takeIf { !it.isLocalEcho && !it.isDeleted } ?: return
        _uiState.value = _uiState.value.copy(
            isForwardDialogOpen = true,
            selectedForwardProfileIds = emptyList(),
            forwardCandidateQuery = "",
            forwardConversationCandidates = emptyList(),
            forwardCandidateHasMore = true,
            forwardCandidateNextOffset = 0,
            forwardCandidateActorNeighborhood = "",
            forwardCandidateError = null
        )
        loadForwardConversationCandidates(reset = true)
    }

    private fun closeForwardPicker() {
        forwardCandidateSearchJob?.cancel()
        forwardCandidatePageJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isForwardDialogOpen = false,
            selectedForwardProfileIds = emptyList(),
            forwardCandidateQuery = "",
            forwardConversationCandidates = emptyList(),
            forwardCandidateHasMore = true,
            forwardCandidateNextOffset = 0,
            forwardCandidateError = null
        )
    }

    private fun loadForwardConversationCandidates(reset: Boolean) {
        forwardCandidatePageJob?.cancel()
        forwardCandidatePageJob = scope.launch {
            val state = _uiState.value
            val offset = if (reset) 0 else state.forwardCandidateNextOffset
            _uiState.value = state.copy(
                isForwardCandidateInitialLoading = reset,
                isForwardCandidatePageLoading = !reset,
                forwardCandidateError = null
            )
            repository.searchConversationCandidates(
                query = state.forwardCandidateQuery,
                limit = 30,
                offset = offset
            ).onSuccess { page ->
                val currentItems = if (reset) emptyList() else _uiState.value.forwardConversationCandidates
                val filteredPageItems = page.candidates
                    .filterNot { it.existingConversationId == conversationId }
                    .filterNot { it.profileId == _uiState.value.currentUser?.id }
                val updatedItems = (currentItems + filteredPageItems).distinctBy { it.profileId }
                _uiState.value = _uiState.value.copy(
                    isForwardCandidateInitialLoading = false,
                    isForwardCandidatePageLoading = false,
                    forwardConversationCandidates = updatedItems,
                    forwardCandidateHasMore = page.hasMore,
                    forwardCandidateNextOffset = page.nextOffset,
                    forwardCandidateActorNeighborhood = page.actorNeighborhood,
                    forwardCandidateError = null
                )
                if (filteredPageItems.isEmpty() && page.hasMore && _uiState.value.isForwardDialogOpen) {
                    loadForwardConversationCandidates(reset = false)
                }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isForwardCandidateInitialLoading = false,
                    isForwardCandidatePageLoading = false,
                    forwardCandidateError = text(ChatText.LoadCandidates)
                )
            }
        }
    }

    private fun loadParticipantConversationCandidates(reset: Boolean) {
        participantCandidatePageJob?.cancel()
        participantCandidatePageJob = scope.launch {
            val state = _uiState.value
            val offset = if (reset) 0 else state.participantCandidateNextOffset
            _uiState.value = state.copy(
                isParticipantCandidateInitialLoading = reset,
                isParticipantCandidatePageLoading = !reset,
                participantCandidateError = null
            )
            repository.searchConversationCandidates(
                query = state.participantCandidateQuery,
                limit = 30,
                offset = offset
            ).onSuccess { page ->
                val excludedIds = _uiState.value.conversation?.participantIds.orEmpty().toSet()
                val currentItems = if (reset) emptyList() else _uiState.value.participantConversationCandidates
                val filteredPageItems = page.candidates.filterNot { it.profileId in excludedIds }
                val updatedItems = (currentItems + filteredPageItems).distinctBy { it.profileId }
                _uiState.value = _uiState.value.copy(
                    isParticipantCandidateInitialLoading = false,
                    isParticipantCandidatePageLoading = false,
                    participantConversationCandidates = updatedItems,
                    participantCandidateHasMore = page.hasMore,
                    participantCandidateNextOffset = page.nextOffset,
                    participantCandidateActorNeighborhood = page.actorNeighborhood,
                    participantCandidateError = null
                )
                if (filteredPageItems.isEmpty() && page.hasMore && _uiState.value.isAddParticipantsOpen) {
                    loadParticipantConversationCandidates(reset = false)
                }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isParticipantCandidateInitialLoading = false,
                    isParticipantCandidatePageLoading = false,
                    participantCandidateError = text(ChatText.LoadCandidates)
                )
            }
        }
    }

    private fun addParticipants() = scope.launch {
        val selectedIds = _uiState.value.selectedParticipantIds
        val selectedCandidates = _uiState.value.participantConversationCandidates
            .filter { it.profileId in selectedIds }
        _uiState.value = _uiState.value.copy(
            isConversationActionInProgress = true,
            isAddParticipantsOpen = false,
            participantSearch = "",
            selectedParticipantIds = emptyList()
        )
        repository.addParticipants(conversationId, selectedIds)
            .onSuccess {
                _uiState.value = _uiState.value.copy(
                    conversation = _uiState.value.conversation
                        ?.withAddedParticipants(selectedIds, selectedCandidates),
                    participantConversationCandidates = _uiState.value.participantConversationCandidates
                        .filterNot { it.profileId in selectedIds },
                    isConversationActionInProgress = false
                )
            }
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = text(ChatText.AddParticipants)) }
    }

    private fun Conversation.withAddedParticipants(
        participantIdsToAdd: List<String>,
        selectedCandidates: List<com.quata.feature.chat.domain.ChatConversationCandidate>
    ): Conversation {
        if (participantIdsToAdd.isEmpty()) return this
        val candidatesById = selectedCandidates.associateBy { it.profileId }
        val nameById = participantIds.zip(participantNames).toMap().toMutableMap()
        val avatarById = participantIds.zip(participantAvatarUrls).toMap().toMutableMap()
        participantIdsToAdd.forEach { id ->
            val candidate = candidatesById[id]
            if (candidate != null) {
                nameById[id] = candidate.displayName
                avatarById[id] = candidate.avatarUrl
            }
        }
        val updatedIds = (participantIds + participantIdsToAdd).distinct()
        return copy(
            participantIds = updatedIds,
            participantNames = updatedIds.map { nameById[it] ?: it },
            participantAvatarUrls = updatedIds.map { avatarById[it] }
        )
    }

    private fun hideConversation() = scope.launch {
        _uiState.value = _uiState.value.copy(isConversationActionInProgress = true)
        repository.hideConversation(conversationId)
            .onSuccess { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, shouldCloseConversation = true) }
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = text(ChatText.DeleteConversation)) }
    }

    private fun deleteConversation() = scope.launch {
        _uiState.value = _uiState.value.copy(isConversationActionInProgress = true)
        repository.deleteConversation(conversationId)
            .onSuccess {
                clearConversationLocalState()
                _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, shouldCloseConversation = true)
            }
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = text(ChatText.DeleteConversation)) }
    }

    private fun leaveConversation() = scope.launch {
        _uiState.value = _uiState.value.copy(isConversationActionInProgress = true)
        repository.leaveConversation(conversationId)
            .onSuccess {
                clearConversationLocalState()
                _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, shouldCloseConversation = true)
            }
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = text(ChatText.LeaveConversation)) }
    }

    private fun promoteModerator(userId: String) = scope.launch {
        _uiState.value = _uiState.value.copy(isConversationActionInProgress = true, error = null)
        repository.promoteModerator(conversationId, userId)
            .onSuccess { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false) }
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = text(ChatText.PromoteParticipant)) }
    }

    private fun demoteModerator(userId: String) = scope.launch {
        _uiState.value = _uiState.value.copy(isConversationActionInProgress = true, error = null)
        repository.demoteModerator(conversationId, userId)
            .onSuccess { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false) }
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = text(ChatText.DemoteParticipant)) }
    }

    private fun removeParticipant(userId: String) = scope.launch {
        _uiState.value = _uiState.value.copy(isConversationActionInProgress = true, error = null)
        repository.removeParticipant(conversationId, userId)
            .onSuccess { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false) }
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = text(ChatText.RemoveParticipant)) }
    }

    private fun blockParticipant(userId: String) = scope.launch {
        _uiState.value = _uiState.value.copy(isConversationActionInProgress = true, error = null)
        repository.blockParticipant(conversationId, userId)
            .onSuccess { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false) }
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = text(ChatText.BlockParticipant)) }
    }

    private fun selectedMessage() = _uiState.value.messages.firstOrNull { it.id == _uiState.value.selectedMessageId }

    private fun startReply() {
        selectedMessage()?.takeIf { !it.isLocalEcho && !it.isDeleted }?.let { message ->
            _uiState.value = _uiState.value.copy(replyToMessage = message, selectedMessageId = null)
            persistCurrentComposerDraft(_uiState.value.messageText)
        }
    }

    private fun startEdit() {
        selectedMessage()?.takeIf { it.isMine && !it.isDeleted && !it.isLocalEcho }?.let { message ->
            _uiState.value = _uiState.value.copy(
                editingMessage = message,
                messageText = message.text,
                selectedMessageId = null
            )
            persistCurrentComposerDraft(message.text)
        }
    }

    private fun toggleFavoriteSelected() {
        val message = selectedMessage()?.takeIf { !it.isLocalEcho && !it.isDeleted } ?: return
        scope.launch {
            repository.toggleFavoriteMessage(message.id)
                .onSuccess { _uiState.value = _uiState.value.copy(selectedMessageId = null) }
                .onFailure { _uiState.value = _uiState.value.copy(error = text(ChatText.UpdateFavorite)) }
        }
    }

    private fun deleteSelectedMessage() {
        val message = selectedMessage()?.takeIf { it.isMine && !it.isDeleted && !it.isLocalEcho } ?: return
        val actorId = _uiState.value.currentUser?.id ?: return
        runSelectedMessageMutation(
            SelectedMessageMutation.Delete(
                messageId = message.id,
                conversationId = message.conversationId,
                actorId = actorId,
                clientMutationId = newMessageMutationId(),
            ),
        )
    }

    private fun reportSelectedMessage() {
        val message = selectedMessage()?.takeIf { !it.isMine && !it.isDeleted && !it.isLocalEcho } ?: return
        val actorId = _uiState.value.currentUser?.id ?: return
        runSelectedMessageMutation(
            SelectedMessageMutation.Report(
                messageId = message.id,
                conversationId = message.conversationId,
                actorId = actorId,
            ),
        )
    }

    private fun retryMessageMutation() {
        val mutation = pendingSelectedMessageMutationRetry ?: return
        runSelectedMessageMutation(mutation)
    }

    private fun runSelectedMessageMutation(mutation: SelectedMessageMutation) {
        if (activeSelectedMessageMutation != null) return
        if (_uiState.value.currentUser?.id != mutation.actorId) {
            pendingSelectedMessageMutationRetry = null
            _uiState.value = _uiState.value.copy(messageMutationRetry = null, error = null)
            return
        }
        val loadedConversationId = _uiState.value.messages
            .firstOrNull { it.id == mutation.messageId }
            ?.conversationId
        if (loadedConversationId != null && loadedConversationId != mutation.conversationId) {
            pendingSelectedMessageMutationRetry = null
            _uiState.value = _uiState.value.copy(messageMutationRetry = null, error = null)
            return
        }
        activeSelectedMessageMutation = mutation
        pendingSelectedMessageMutationRetry = null
        _uiState.value = _uiState.value.copy(
            isMessageMutationInProgress = true,
            messageMutationRetry = null,
            error = null,
        )
        scope.launch {
            try {
                val result = try {
                    when (mutation) {
                        is SelectedMessageMutation.Delete -> repository.deleteMessage(
                            messageId = mutation.messageId,
                            conversationId = mutation.conversationId,
                            clientMutationId = mutation.clientMutationId,
                            expectedActorId = mutation.actorId,
                        )
                        is SelectedMessageMutation.Report -> repository.reportMessage(
                            messageId = mutation.messageId,
                            expectedActorId = mutation.actorId,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Result.failure(error)
                }
                val actorStillMatches = runCatching { repository.currentActorId() }.getOrNull() == mutation.actorId
                if (!actorStillMatches) {
                    pendingSelectedMessageMutationRetry = null
                    _uiState.value = _uiState.value.copy(
                        isMessageMutationInProgress = false,
                        messageMutationRetry = null,
                        error = null,
                    )
                    return@launch
                }
                result
                    .onSuccess {
                        val state = _uiState.value
                        _uiState.value = state.copy(
                            selectedMessageId = state.selectedMessageId.takeUnless { it == mutation.messageId },
                            isMessageMutationInProgress = false,
                            messageMutationRetry = null,
                            notice = if (mutation is SelectedMessageMutation.Report) text(ChatText.ReportSent) else state.notice,
                            error = null,
                        )
                    }
                    .onFailure {
                        pendingSelectedMessageMutationRetry = mutation
                        _uiState.value = _uiState.value.copy(
                            isMessageMutationInProgress = false,
                            messageMutationRetry = mutation.toRetryState(),
                            error = text(
                                if (mutation is SelectedMessageMutation.Delete) {
                                    ChatText.DeleteMessage
                                } else {
                                    ChatText.ReportMessage
                                },
                            ),
                        )
                    }
            } finally {
                if (activeSelectedMessageMutation === mutation) {
                    activeSelectedMessageMutation = null
                }
            }
        }
    }

    private fun toggleForwardProfile(profileId: String) {
        val current = _uiState.value.selectedForwardProfileIds
        _uiState.value = _uiState.value.copy(
            selectedForwardProfileIds = if (profileId in current) current - profileId else current + profileId
        )
    }

    private fun sendForward() {
        val message = selectedMessage()?.takeIf { !it.isLocalEcho && !it.isDeleted } ?: return
        val selectedProfileIds = _uiState.value.selectedForwardProfileIds.distinct()
        if (selectedProfileIds.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            isConversationActionInProgress = true,
            error = null
        )
        scope.launch {
            val openedConversationIds = mutableListOf<String>()
            var openFailureCount = 0
            selectedProfileIds.forEach { profileId ->
                repository.openPrivateConversation(profileId).fold(
                    onSuccess = { openedConversationIds += it },
                    onFailure = { openFailureCount += 1 },
                )
            }
            val conversationIds = openedConversationIds
                .filterNot { it == conversationId }
                .distinct()
            if (conversationIds.isEmpty()) {
                _uiState.value = if (openFailureCount == 0) {
                    _uiState.value.copy(
                        isConversationActionInProgress = false,
                        isForwardDialogOpen = false,
                        selectedForwardProfileIds = emptyList(),
                        selectedMessageId = null,
                    )
                } else {
                    _uiState.value.copy(
                        isConversationActionInProgress = false,
                        error = text(ChatText.Forward),
                    )
                }
                return@launch
            }
            repository.forwardMessage(message, conversationIds).onSuccess { result ->
                if (result.isComplete && openFailureCount == 0) {
                    _uiState.value = _uiState.value.copy(
                        selectedMessageId = null,
                        isConversationActionInProgress = false,
                        isForwardDialogOpen = false,
                        selectedForwardProfileIds = emptyList(),
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isConversationActionInProgress = false,
                        error = text(ChatText.Forward),
                    )
                }
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isConversationActionInProgress = false,
                    error = text(ChatText.Forward),
                )
            }
        }
    }

    companion object {
        private const val LOCAL_ECHO_MATCH_PAST_TOLERANCE_MILLIS = 2L * 60L * 1000L
        private const val LOCAL_ECHO_MATCH_FUTURE_TOLERANCE_MILLIS = 10L * 60L * 1000L
        private const val LOCAL_ECHO_ENRICHED_TEXT_MIN_LENGTH = 12
        private val ECHO_URL_REGEX = Regex("""https?://\S+|www\.\S+""", RegexOption.IGNORE_CASE)

    }

    fun close() {
        messageObservationJob?.cancel()
        cleanupEmptyConversationIfNeeded()
        repository.setConversationVisible(conversationId, false)
        scope.coroutineContext.cancel()
        val pendingAttachmentStage = composerAttachmentStageJob
        val pendingViewportWrite = viewportWriteJob
        if (composerDraftWriteJob == null && pendingAttachmentStage == null && pendingViewportWrite == null) {
            composerDraftScope.coroutineContext.cancel()
        } else {
            val closeJob = composerDraftScope.launch {
                pendingAttachmentStage?.join()
                while (true) {
                    val pendingDraftWrite = composerDraftWriteJob ?: break
                    pendingDraftWrite.join()
                    if (pendingDraftWrite === composerDraftWriteJob) break
                }
                while (true) {
                    val pendingWrite = viewportWriteJob ?: break
                    pendingWrite.join()
                    if (pendingWrite === viewportWriteJob) break
                }
            }
            closeJob.invokeOnCompletion { composerDraftScope.coroutineContext.cancel() }
        }
    }
}

private fun ChatUiState.composerDraftAttachment(): ChatComposerDraftAttachment? {
    val cacheKey = attachmentCacheKey ?: return null
    val reference = attachmentUri ?: return null
    return ChatComposerDraftAttachment(
        cacheKey = cacheKey,
        reference = reference,
        name = attachmentName,
        mimeType = attachmentMimeType,
    )
}

private fun ChatUiState.withRestoredComposerDraft(draft: ChatComposerDraftRecord): ChatUiState = copy(
    messageText = draft.text,
    attachmentCacheKey = draft.attachment?.cacheKey,
    attachmentUri = draft.attachment?.reference,
    attachmentName = draft.attachment?.name,
    attachmentMimeType = draft.attachment?.mimeType,
)

private data class OutgoingDraft(
    val text: String,
    val attachmentCacheKey: String?,
    val attachmentUri: String?,
    val attachmentName: String?,
    val attachmentMimeType: String?,
    val replyToMessage: Message?,
    val clientMessageId: String
) {
    fun matches(
        text: String,
        attachmentCacheKey: String?,
        attachmentUri: String?,
        attachmentName: String?,
        attachmentMimeType: String?,
        replyToMessage: Message?
    ): Boolean =
        this.text == text &&
            this.attachmentCacheKey == attachmentCacheKey &&
            this.attachmentUri == attachmentUri &&
            this.attachmentName == attachmentName &&
            this.attachmentMimeType == attachmentMimeType &&
            this.replyToMessage?.id == replyToMessage?.id
}

private sealed interface SelectedMessageMutation {
    val messageId: String
    val conversationId: String
    val actorId: String

    data class Delete(
        override val messageId: String,
        override val conversationId: String,
        override val actorId: String,
        val clientMutationId: String,
    ) : SelectedMessageMutation

    data class Report(
        override val messageId: String,
        override val conversationId: String,
        override val actorId: String,
    ) : SelectedMessageMutation
}

private fun SelectedMessageMutation.toRetryState() = ChatMessageMutationRetry(
    kind = if (this is SelectedMessageMutation.Delete) ChatMessageMutationKind.Delete else ChatMessageMutationKind.Report,
    messageId = messageId,
)

private data class EditMessageMutation(
    val messageId: String,
    val conversationId: String,
    val text: String,
    val actorId: String,
    val clientMutationId: String,
) {
    fun matches(messageId: String, conversationId: String, text: String, actorId: String): Boolean =
        this.messageId == messageId && this.conversationId == conversationId && this.text == text && this.actorId == actorId
}

private fun newMessageMutationId(): String = "chat-mutation-${newClientMessageId()}"

private data class PendingComposerRestore(
    val draft: ChatComposerDraftRecord,
    val revision: Long,
)
