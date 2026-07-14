package com.quata.feature.chat.presentation.chat

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quata.R
import com.quata.core.model.MessageDeliveryState
import com.quata.core.navigation.AppDestinations
import com.quata.core.model.Message
import com.quata.core.text.stripHtmlTagsAndDecode
import com.quata.feature.chat.domain.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class ChatViewModel(
    private val conversationId: String,
    private val repository: ChatRepository,
    private val resolveString: (Int) -> String = { "Chat error" }
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private val isFavoritesConversation = conversationId == AppDestinations.FavoriteMessagesConversationId
    private var backendMessages: List<Message> = emptyList()
    private var localEchoMessages: List<Message> = emptyList()
    private var optimisticEditedMessages: Map<String, Message> = emptyMap()
    private var retryDraft: OutgoingDraft? = null
    private var participantCandidateSearchJob: Job? = null
    private var participantCandidatePageJob: Job? = null
    private var forwardCandidateSearchJob: Job? = null
    private var forwardCandidatePageJob: Job? = null
    private var isConversationVisible = false

    init {
        _uiState.value = _uiState.value.copy(currentUser = repository.currentUser())
        viewModelScope.launch {
            if (isConversationVisible && repository.isAppForeground.value) {
                repository.markConversationRead(conversationId)
            }
        }
        viewModelScope.launch {
            repository.isAppForeground.collect { isForeground ->
                if (isForeground && isConversationVisible) {
                    repository.markConversationRead(conversationId)
                }
            }
        }
        viewModelScope.launch {
            repository.syncStatus.collect { status -> _uiState.value = _uiState.value.copy(syncStatus = status) }
        }
        viewModelScope.launch {
            repository.observeConversations()
                .catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = errorText(R.string.chat_error_load_conversations)
                    )
                }
                .collect { conversations ->
                    _uiState.value = _uiState.value.copy(
                        conversation = conversations.firstOrNull { it.id == conversationId }
                    )
                }
        }
        viewModelScope.launch {
            repository.observeMessages(conversationId)
                .catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = errorText(R.string.chat_error_load_messages)
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
                    if (isConversationVisible && repository.isAppForeground.value) {
                        repository.markConversationRead(conversationId)
                    }
                }
        }
        viewModelScope.launch {
            repository.observeParticipantCandidates()
                .catch { emit(emptyList()) }
                .collect { candidates ->
                    _uiState.value = _uiState.value.copy(participantCandidates = candidates)
                }
        }
    }

    fun onEvent(event: ChatUiEvent) {
        when (event) {
            is ChatUiEvent.MessageChanged -> _uiState.value = _uiState.value.copy(messageText = event.value)
            is ChatUiEvent.AttachmentSelected -> _uiState.value = _uiState.value.copy(
                attachmentUri = event.uri,
                attachmentName = event.name,
                attachmentMimeType = event.mimeType
            )
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
            ChatUiEvent.ClearReply -> _uiState.value = _uiState.value.copy(replyToMessage = null)
            ChatUiEvent.StartEdit -> startEdit()
            ChatUiEvent.CancelEdit -> _uiState.value = _uiState.value.copy(editingMessage = null, messageText = "")
            ChatUiEvent.ToggleFavoriteSelected -> toggleFavoriteSelected()
            ChatUiEvent.DeleteSelectedMessage -> deleteSelectedMessage()
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
            ChatUiEvent.ClearAttachment -> _uiState.value = _uiState.value.copy(
                attachmentUri = null,
                attachmentName = null,
                attachmentMimeType = null
            )
        }
    }

    fun setConversationVisible(visible: Boolean) {
        if (isConversationVisible == visible) return
        isConversationVisible = visible
        repository.setConversationVisible(conversationId, visible)
        if (visible && repository.isAppForeground.value) {
            viewModelScope.launch { repository.markConversationRead(conversationId) }
        }
    }

    fun cleanupEmptyConversationIfNeeded() {
        if (!isFavoritesConversation && backendMessages.isEmpty() && localEchoMessages.isEmpty()) {
            repository.cleanupEmptyConversation(conversationId)
        }
    }

    fun loadOlderMessages() {
        if (isFavoritesConversation || _uiState.value.isLoadingOlderMessages || !_uiState.value.hasMoreHistory) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingOlderMessages = true)
            repository.loadOlderMessages(conversationId)
                .onSuccess { hasMore -> _uiState.value = _uiState.value.copy(isLoadingOlderMessages = false, hasMoreHistory = hasMore) }
                .onFailure { error -> _uiState.value = _uiState.value.copy(isLoadingOlderMessages = false, error = error.message) }
        }
    }

    fun retryPendingMessage(clientMessageId: String) {
        viewModelScope.launch {
            repository.retryPendingMessage(clientMessageId)
                .onFailure { error -> _uiState.value = _uiState.value.copy(error = error.message) }
        }
    }

    private fun send() = viewModelScope.launch {
        val text = _uiState.value.messageText
        val attachmentUri = _uiState.value.attachmentUri
        val attachmentName = _uiState.value.attachmentName
        val attachmentMimeType = _uiState.value.attachmentMimeType
        if (text.isBlank() && attachmentUri.isNullOrBlank()) return@launch

        val currentUserId = _uiState.value.currentUser?.id
        if (currentUserId != null && currentUserId in _uiState.value.conversation?.blockedUserIds.orEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Has sido bloqueado de esta conversacion")
            return@launch
        }

        val editingMessage = _uiState.value.editingMessage
        val replyToMessage = _uiState.value.replyToMessage
        val draft = retryDraft
            ?.takeIf { it.matches(text, attachmentUri, attachmentName, attachmentMimeType, replyToMessage) }
            ?: OutgoingDraft(
                text = text,
                attachmentUri = attachmentUri,
                attachmentName = attachmentName,
                attachmentMimeType = attachmentMimeType,
                replyToMessage = replyToMessage,
                clientMessageId = UUID.randomUUID().toString()
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

        val result = when {
            editingMessage != null -> repository.editMessage(editingMessage.id, text)
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
        result
            .onSuccess {
                optimisticMessage?.let(::markLocalEchoSent)
                if (editingMessage != null) {
                    optimisticEditedMessages = optimisticEditedMessages.mapValues { (messageId, message) ->
                        if (messageId == editingMessage.id) message.copy(isPending = false) else message
                    }
                    publishMessages(isLoading = false)
                    _uiState.value = _uiState.value.copy(
                        messageText = "",
                        editingMessage = null,
                        selectedMessageId = null
                    )
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
                    optimisticEditedMessages = optimisticEditedMessages - editingMessage.id
                    restoreEditDraftIfComposerIsEmpty(editingMessage, text)
                    publishMessages(isLoading = false)
                }
                _uiState.value = _uiState.value.copy(error = errorText(R.string.chat_error_send))
            }
    }

    private fun publishMessages(isLoading: Boolean = _uiState.value.isLoading) {
        val editedBackendMessages = backendMessages.map { message ->
            optimisticEditedMessages[message.id] ?: message
        }
        val visibleMessages = (editedBackendMessages + localEchoMessages)
            .filter { isFavoritesConversation || it.conversationId == conversationId }
            .distinctBy { it.id }
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
        val now = System.currentTimeMillis()
        return Message(
            id = "local:${draft.clientMessageId}",
            conversationId = conversationId,
            senderId = currentUser?.id.orEmpty(),
            senderName = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: resolveString(R.string.common_you),
            text = draft.text,
            sentAt = Instant.ofEpochMilli(now).toString(),
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

    private fun restoreDraftIfComposerIsEmpty(draft: OutgoingDraft) {
        val state = _uiState.value
        if (state.messageText.isNotBlank() || state.attachmentUri != null) return
        retryDraft = draft
        _uiState.value = state.copy(
            messageText = draft.text,
            attachmentUri = draft.attachmentUri,
            attachmentName = draft.attachmentName,
            attachmentMimeType = draft.attachmentMimeType,
            replyToMessage = draft.replyToMessage
        )
    }

    private fun restoreEditDraftIfComposerIsEmpty(message: Message, text: String) {
        val state = _uiState.value
        if (state.messageText.isNotBlank() || state.attachmentUri != null) return
        _uiState.value = state.copy(
            messageText = text,
            editingMessage = message,
            selectedMessageId = message.id
        )
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
        stripHtmlTagsAndDecode()
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

    private fun setMuted(muted: Boolean) = viewModelScope.launch {
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
                    error = errorText(R.string.chat_error_update)
                )
            }
    }

    private fun setMemberInvitesEnabled(enabled: Boolean) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isConversationActionInProgress = true)
        repository.setMemberInvitesEnabled(conversationId, enabled)
            .onSuccess { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false) }
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = errorText(R.string.chat_error_update)) }
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
        participantCandidateSearchJob = viewModelScope.launch {
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
        viewModelScope.launch {
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
                        participantCandidateError = errorText(R.string.chat_error_add_participant)
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
        forwardCandidateSearchJob = viewModelScope.launch {
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
        forwardCandidatePageJob = viewModelScope.launch {
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
                    forwardCandidateError = errorText(R.string.chat_error_load_candidates)
                )
            }
        }
    }

    private fun loadParticipantConversationCandidates(reset: Boolean) {
        participantCandidatePageJob?.cancel()
        participantCandidatePageJob = viewModelScope.launch {
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
                    participantCandidateError = errorText(R.string.chat_error_load_candidates)
                )
            }
        }
    }

    private fun addParticipants() = viewModelScope.launch {
        val selectedIds = _uiState.value.selectedParticipantIds
        _uiState.value = _uiState.value.copy(
            isConversationActionInProgress = true,
            isAddParticipantsOpen = false,
            participantSearch = "",
            selectedParticipantIds = emptyList()
        )
        repository.addParticipants(conversationId, selectedIds)
            .onSuccess {
                _uiState.value = _uiState.value.copy(isConversationActionInProgress = false)
            }
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = errorText(R.string.chat_error_add_participants)) }
    }

    private fun hideConversation() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isConversationActionInProgress = true)
        repository.hideConversation(conversationId)
            .onSuccess { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, shouldCloseConversation = true) }
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = errorText(R.string.chat_error_delete_conversation)) }
    }

    private fun deleteConversation() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isConversationActionInProgress = true)
        repository.deleteConversation(conversationId)
            .onSuccess { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, shouldCloseConversation = true) }
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = errorText(R.string.chat_error_delete_conversation)) }
    }

    private fun leaveConversation() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isConversationActionInProgress = true)
        repository.leaveConversation(conversationId)
            .onSuccess { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, shouldCloseConversation = true) }
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = errorText(R.string.chat_error_leave_conversation)) }
    }

    private fun promoteModerator(userId: String) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isConversationActionInProgress = true)
        repository.promoteModerator(conversationId, userId)
            .onSuccess { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false) }
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = errorText(R.string.chat_error_promote_participant)) }
    }

    private fun demoteModerator(userId: String) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isConversationActionInProgress = true)
        repository.demoteModerator(conversationId, userId)
            .onSuccess { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false) }
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = errorText(R.string.chat_error_demote_participant)) }
    }

    private fun removeParticipant(userId: String) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isConversationActionInProgress = true)
        repository.removeParticipant(conversationId, userId)
            .onSuccess { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false) }
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = errorText(R.string.chat_error_remove_participant)) }
    }

    private fun blockParticipant(userId: String) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isConversationActionInProgress = true)
        repository.blockParticipant(conversationId, userId)
            .onSuccess { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false) }
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = errorText(R.string.chat_error_block_participant)) }
    }

    private fun selectedMessage() = _uiState.value.messages.firstOrNull { it.id == _uiState.value.selectedMessageId }

    private fun startReply() {
        selectedMessage()?.takeIf { !it.isLocalEcho }?.let { message ->
            _uiState.value = _uiState.value.copy(replyToMessage = message, selectedMessageId = null)
        }
    }

    private fun startEdit() {
        selectedMessage()?.takeIf { it.isMine && !it.isDeleted && !it.isLocalEcho }?.let { message ->
            _uiState.value = _uiState.value.copy(
                editingMessage = message,
                messageText = message.text,
                selectedMessageId = null
            )
        }
    }

    private fun toggleFavoriteSelected() = viewModelScope.launch {
        val message = selectedMessage()?.takeIf { !it.isLocalEcho } ?: return@launch
        repository.toggleFavoriteMessage(message.id)
            .onSuccess { _uiState.value = _uiState.value.copy(selectedMessageId = null) }
            .onFailure { _uiState.value = _uiState.value.copy(error = errorText(R.string.chat_error_update_favorite)) }
    }

    private fun deleteSelectedMessage() = viewModelScope.launch {
        val message = selectedMessage()?.takeIf { it.isMine && !it.isLocalEcho } ?: return@launch
        repository.deleteMessage(message.id)
            .onSuccess { _uiState.value = _uiState.value.copy(selectedMessageId = null) }
            .onFailure { _uiState.value = _uiState.value.copy(error = errorText(R.string.chat_error_delete_message)) }
    }

    private fun toggleForwardProfile(profileId: String) {
        val current = _uiState.value.selectedForwardProfileIds
        _uiState.value = _uiState.value.copy(
            selectedForwardProfileIds = if (profileId in current) current - profileId else current + profileId
        )
    }

    private fun sendForward() = viewModelScope.launch {
        val message = selectedMessage()?.takeIf { !it.isLocalEcho } ?: return@launch
        val selectedProfileIds = _uiState.value.selectedForwardProfileIds
        if (selectedProfileIds.isEmpty()) return@launch
        _uiState.value = _uiState.value.copy(
            isConversationActionInProgress = true,
            isForwardDialogOpen = false,
            selectedForwardProfileIds = emptyList()
        )
        runCatching {
            selectedProfileIds.map { profileId ->
                repository.openPrivateConversation(profileId).getOrThrow()
            }
                .filterNot { it == conversationId }
                .distinct()
        }.fold(
            onSuccess = { conversationIds ->
                if (conversationIds.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isConversationActionInProgress = false,
                        selectedMessageId = null
                    )
                    return@launch
                }
                repository.forwardMessage(message, conversationIds).onSuccess {
                    _uiState.value = _uiState.value.copy(
                        selectedMessageId = null,
                        isConversationActionInProgress = false
                    )
                }.onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isConversationActionInProgress = false,
                        error = errorText(R.string.chat_error_forward)
                    )
                }
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    isConversationActionInProgress = false,
                    error = errorText(R.string.chat_error_forward)
                )
            }
        )
    }

    companion object {
        private const val LOCAL_ECHO_MATCH_PAST_TOLERANCE_MILLIS = 2L * 60L * 1000L
        private const val LOCAL_ECHO_MATCH_FUTURE_TOLERANCE_MILLIS = 10L * 60L * 1000L
        private const val LOCAL_ECHO_ENRICHED_TEXT_MIN_LENGTH = 12
        private val ECHO_URL_REGEX = Regex("""https?://\S+|www\.\S+""", RegexOption.IGNORE_CASE)

        fun factory(conversationId: String, repository: ChatRepository, context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ChatViewModel(conversationId, repository, context.applicationContext::getString) as T
        }
    }

    private fun errorText(@StringRes id: Int): String = resolveString(id)

    override fun onCleared() {
        cleanupEmptyConversationIfNeeded()
        repository.setConversationVisible(conversationId, false)
        super.onCleared()
    }
}

private data class OutgoingDraft(
    val text: String,
    val attachmentUri: String?,
    val attachmentName: String?,
    val attachmentMimeType: String?,
    val replyToMessage: Message?,
    val clientMessageId: String
) {
    fun matches(
        text: String,
        attachmentUri: String?,
        attachmentName: String?,
        attachmentMimeType: String?,
        replyToMessage: Message?
    ): Boolean =
        this.text == text &&
            this.attachmentUri == attachmentUri &&
            this.attachmentName == attachmentName &&
            this.attachmentMimeType == attachmentMimeType &&
            this.replyToMessage?.id == replyToMessage?.id
}
