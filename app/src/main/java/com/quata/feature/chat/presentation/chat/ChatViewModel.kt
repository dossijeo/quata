package com.quata.feature.chat.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quata.core.model.Message
import com.quata.feature.chat.domain.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    private val conversationId: String,
    private val repository: ChatRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private var backendMessages: List<Message> = emptyList()
    private var localEchoMessages: List<Message> = emptyList()
    private var optimisticEditedMessages: Map<String, Message> = emptyMap()

    init {
        repository.setActiveConversation(conversationId)
        _uiState.value = _uiState.value.copy(currentUser = repository.currentUser())
        viewModelScope.launch {
            repository.markConversationRead(conversationId)
        }
        viewModelScope.launch {
            repository.observeConversations()
                .catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "No se pudieron cargar los chats"
                    )
                }
                .collect { conversations ->
                    _uiState.value = _uiState.value.copy(
                        conversation = conversations.firstOrNull { it.id == conversationId },
                        availableForwardConversations = conversations.filter { it.id != conversationId && it.isVisible }
                    )
                }
        }
        viewModelScope.launch {
            repository.observeMessages(conversationId)
                .catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "No se pudieron cargar los mensajes"
                    )
                }
                .collect { messages ->
                    backendMessages = messages
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
                    repository.markConversationRead(conversationId)
                }
        }
        viewModelScope.launch {
            repository.observeParticipantCandidates()
                .catch { error ->
                    _uiState.value = _uiState.value.copy(error = error.message ?: "No se pudieron cargar los contactos")
                }
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
            is ChatUiEvent.ParticipantSearchChanged -> _uiState.value = _uiState.value.copy(participantSearch = event.value)
            is ChatUiEvent.ParticipantSelectionToggled -> toggleParticipant(event.userId)
            is ChatUiEvent.MessageSelected -> _uiState.value = _uiState.value.copy(selectedMessageId = event.messageId)
            is ChatUiEvent.ForwardConversationToggled -> toggleForwardConversation(event.conversationId)
            is ChatUiEvent.ConversationMutedChanged -> setMuted(event.muted)
            is ChatUiEvent.MemberInvitesChanged -> setMemberInvitesEnabled(event.enabled)
            ChatUiEvent.OpenAddParticipants -> _uiState.value = _uiState.value.copy(isAddParticipantsOpen = true)
            ChatUiEvent.CloseAddParticipants -> _uiState.value = _uiState.value.copy(
                isAddParticipantsOpen = false,
                participantSearch = "",
                selectedParticipantIds = emptyList()
            )
            ChatUiEvent.AddSelectedParticipants -> addParticipants()
            ChatUiEvent.StartReply -> startReply()
            ChatUiEvent.ClearReply -> _uiState.value = _uiState.value.copy(replyToMessage = null)
            ChatUiEvent.StartEdit -> startEdit()
            ChatUiEvent.CancelEdit -> _uiState.value = _uiState.value.copy(editingMessage = null, messageText = "")
            ChatUiEvent.ToggleFavoriteSelected -> toggleFavoriteSelected()
            ChatUiEvent.DeleteSelectedMessage -> deleteSelectedMessage()
            ChatUiEvent.OpenForwardDialog -> _uiState.value = _uiState.value.copy(isForwardDialogOpen = true)
            ChatUiEvent.CloseForwardDialog -> _uiState.value = _uiState.value.copy(
                isForwardDialogOpen = false,
                selectedForwardConversationIds = emptyList()
            )
            ChatUiEvent.SendForward -> sendForward()
            is ChatUiEvent.PromoteModerator -> promoteModerator(event.userId)
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
        val draft = OutgoingDraft(
            text = text,
            attachmentUri = attachmentUri,
            attachmentName = attachmentName,
            attachmentMimeType = attachmentMimeType,
            replyToMessage = replyToMessage
        )
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
            replyToMessage != null -> repository.sendReply(conversationId, text, replyToMessage)
            else -> repository.sendMessage(
                conversationId = conversationId,
                text = text,
                attachmentUri = attachmentUri,
                attachmentName = attachmentName,
                attachmentMimeType = attachmentMimeType
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
                    localEchoMessages = localEchoMessages.filterNot { it.id == failedMessage.id }
                    restoreDraftIfComposerIsEmpty(draft)
                    publishMessages(isLoading = false)
                }
                if (editingMessage != null) {
                    optimisticEditedMessages = optimisticEditedMessages - editingMessage.id
                    restoreEditDraftIfComposerIsEmpty(editingMessage, text)
                    publishMessages(isLoading = false)
                }
                _uiState.value = _uiState.value.copy(error = error.message ?: "No se pudo enviar")
            }
    }

    private fun publishMessages(isLoading: Boolean = _uiState.value.isLoading) {
        val editedBackendMessages = backendMessages.map { message ->
            optimisticEditedMessages[message.id] ?: message
        }
        val visibleMessages = (editedBackendMessages + localEchoMessages)
            .distinctBy { it.id }
            .sortedBy { it.sentAtMillis ?: Long.MAX_VALUE }
        _uiState.value = _uiState.value.copy(messages = visibleMessages, isLoading = isLoading)
    }

    private fun createOptimisticMessage(draft: OutgoingDraft): Message {
        val currentUser = _uiState.value.currentUser
        val now = System.currentTimeMillis()
        return Message(
            id = "local:${UUID.randomUUID()}",
            conversationId = conversationId,
            senderId = currentUser?.id.orEmpty(),
            senderName = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Tu",
            text = draft.text,
            sentAt = "Ahora",
            sentAtMillis = now,
            isMine = true,
            isRead = false,
            replyToMessageId = draft.replyToMessage?.id,
            replyToSenderName = draft.replyToMessage?.senderName,
            replyToText = draft.replyToMessage?.text,
            attachmentUri = draft.attachmentUri,
            attachmentName = draft.attachmentName,
            attachmentMimeType = draft.attachmentMimeType,
            isPending = true,
            isLocalEcho = true
        )
    }

    private fun markLocalEchoSent(message: Message) {
        localEchoMessages = localEchoMessages.map { local ->
            if (local.id == message.id) local.copy(isPending = false) else local
        }.filterNot { local ->
            backendMessages.any { remote -> remote.matchesLocalEcho(local) }
        }
        publishMessages(isLoading = false)
    }

    private fun restoreDraftIfComposerIsEmpty(draft: OutgoingDraft) {
        val state = _uiState.value
        if (state.messageText.isNotBlank() || state.attachmentUri != null) return
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
        if (!local.isLocalEcho || local.isPending || !isMine) return false
        val sameText = text.trim() == local.text.trim()
        val sameAttachment = local.attachmentName.isNullOrBlank() ||
            attachmentName == local.attachmentName ||
            attachmentMimeType == local.attachmentMimeType
        val remoteTime = sentAtMillis
        val localTime = local.sentAtMillis
        val closeInTime = remoteTime == null || localTime == null ||
            remoteTime in (localTime - LOCAL_ECHO_MATCH_PAST_TOLERANCE_MILLIS)..(localTime + LOCAL_ECHO_MATCH_FUTURE_TOLERANCE_MILLIS)
        return sameText && sameAttachment && closeInTime
    }

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
            isConversationActionInProgress = true,
            availableForwardConversations = _uiState.value.availableForwardConversations.map { conversation ->
                if (conversation.id == conversationId) conversation.copy(isMuted = muted) else conversation
            }
        )
        repository.setConversationMuted(conversationId, muted)
            .onSuccess {
                _uiState.value = _uiState.value.copy(isConversationActionInProgress = false)
            }
            .onFailure {
                _uiState.value = _uiState.value.copy(
                    conversation = previousConversation,
                    isConversationActionInProgress = false,
                    availableForwardConversations = _uiState.value.availableForwardConversations.map { conversation ->
                        if (conversation.id == conversationId && previousConversation != null) {
                            conversation.copy(isMuted = previousConversation.isMuted)
                        } else {
                            conversation
                        }
                    },
                    error = it.message ?: "No se pudo actualizar"
                )
            }
    }

    private fun setMemberInvitesEnabled(enabled: Boolean) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isConversationActionInProgress = true)
        repository.setMemberInvitesEnabled(conversationId, enabled)
            .onSuccess { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false) }
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = it.message ?: "No se pudo actualizar") }
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
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = it.message ?: "No se pudieron anadir participantes") }
    }

    private fun hideConversation() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isConversationActionInProgress = true)
        repository.hideConversation(conversationId)
            .onSuccess { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, shouldCloseConversation = true) }
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = it.message ?: "No se pudo borrar la conversacion") }
    }

    private fun deleteConversation() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isConversationActionInProgress = true)
        repository.deleteConversation(conversationId)
            .onSuccess { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, shouldCloseConversation = true) }
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = it.message ?: "No se pudo borrar la conversacion") }
    }

    private fun leaveConversation() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isConversationActionInProgress = true)
        repository.leaveConversation(conversationId)
            .onSuccess { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, shouldCloseConversation = true) }
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = it.message ?: "No se pudo abandonar la conversacion") }
    }

    private fun promoteModerator(userId: String) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isConversationActionInProgress = true)
        repository.promoteModerator(conversationId, userId)
            .onSuccess { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false) }
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = it.message ?: "No se pudo ascender") }
    }

    private fun removeParticipant(userId: String) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isConversationActionInProgress = true)
        repository.removeParticipant(conversationId, userId)
            .onSuccess { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false) }
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = it.message ?: "No se pudo expulsar") }
    }

    private fun blockParticipant(userId: String) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isConversationActionInProgress = true)
        repository.blockParticipant(conversationId, userId)
            .onSuccess { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false) }
            .onFailure { _uiState.value = _uiState.value.copy(isConversationActionInProgress = false, error = it.message ?: "No se pudo bloquear") }
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
            .onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "No se pudo actualizar favorito") }
    }

    private fun deleteSelectedMessage() = viewModelScope.launch {
        val message = selectedMessage()?.takeIf { it.isMine && !it.isLocalEcho } ?: return@launch
        repository.deleteMessage(message.id)
            .onSuccess { _uiState.value = _uiState.value.copy(selectedMessageId = null) }
            .onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "No se pudo eliminar") }
    }

    private fun toggleForwardConversation(conversationId: String) {
        val current = _uiState.value.selectedForwardConversationIds
        _uiState.value = _uiState.value.copy(
            selectedForwardConversationIds = if (conversationId in current) current - conversationId else current + conversationId
        )
    }

    private fun sendForward() = viewModelScope.launch {
        val message = selectedMessage()?.takeIf { !it.isLocalEcho } ?: return@launch
        val conversationIds = _uiState.value.selectedForwardConversationIds
        repository.forwardMessage(message, conversationIds)
            .onSuccess {
                _uiState.value = _uiState.value.copy(
                    selectedMessageId = null,
                    isForwardDialogOpen = false,
                    selectedForwardConversationIds = emptyList()
                )
            }
            .onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "No se pudo reenviar") }
    }

    companion object {
        private const val LOCAL_ECHO_MATCH_PAST_TOLERANCE_MILLIS = 2L * 60L * 1000L
        private const val LOCAL_ECHO_MATCH_FUTURE_TOLERANCE_MILLIS = 10L * 60L * 1000L

        fun factory(conversationId: String, repository: ChatRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(conversationId, repository) as T
        }
    }

    override fun onCleared() {
        repository.setActiveConversation(null)
        super.onCleared()
    }
}

private data class OutgoingDraft(
    val text: String,
    val attachmentUri: String?,
    val attachmentName: String?,
    val attachmentMimeType: String?,
    val replyToMessage: Message?
)
