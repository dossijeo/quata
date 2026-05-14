package com.quata.feature.chat.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quata.feature.chat.domain.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val conversationId: String,
    private val repository: ChatRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        repository.setActiveConversation(conversationId)
        _uiState.value = _uiState.value.copy(currentUser = repository.currentUser())
        viewModelScope.launch {
            repository.markConversationRead(conversationId)
        }
        viewModelScope.launch {
            repository.observeConversations().collect { conversations ->
                _uiState.value = _uiState.value.copy(
                    conversation = conversations.firstOrNull { it.id == conversationId },
                    availableForwardConversations = conversations.filter { it.id != conversationId && it.isVisible }
                )
            }
        }
        viewModelScope.launch {
            repository.observeMessages(conversationId).collect { messages ->
                _uiState.value = _uiState.value.copy(messages = messages, isLoading = false)
                repository.markConversationRead(conversationId)
            }
        }
        viewModelScope.launch {
            repository.observeParticipantCandidates().collect { candidates ->
                _uiState.value = _uiState.value.copy(participantCandidates = candidates)
            }
        }
    }

    fun onEvent(event: ChatUiEvent) {
        when (event) {
            is ChatUiEvent.MessageChanged -> _uiState.value = _uiState.value.copy(messageText = event.value)
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
        }
    }

    private fun send() = viewModelScope.launch {
        val text = _uiState.value.messageText
        val editingMessage = _uiState.value.editingMessage
        val replyToMessage = _uiState.value.replyToMessage
        val result = when {
            editingMessage != null -> repository.editMessage(editingMessage.id, text)
            replyToMessage != null -> repository.sendReply(conversationId, text, replyToMessage)
            else -> repository.sendMessage(conversationId, text)
        }
        result
            .onSuccess {
                _uiState.value = _uiState.value.copy(
                    messageText = "",
                    editingMessage = null,
                    replyToMessage = null,
                    selectedMessageId = null
                )
            }
            .onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "No se pudo enviar") }
    }

    private fun toggleParticipant(userId: String) {
        val current = _uiState.value.selectedParticipantIds
        _uiState.value = _uiState.value.copy(
            selectedParticipantIds = if (userId in current) current - userId else current + userId
        )
    }

    private fun setMuted(muted: Boolean) = viewModelScope.launch {
        repository.setConversationMuted(conversationId, muted)
            .onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "No se pudo actualizar") }
    }

    private fun setMemberInvitesEnabled(enabled: Boolean) = viewModelScope.launch {
        repository.setMemberInvitesEnabled(conversationId, enabled)
            .onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "No se pudo actualizar") }
    }

    private fun addParticipants() = viewModelScope.launch {
        val selectedIds = _uiState.value.selectedParticipantIds
        repository.addParticipants(conversationId, selectedIds)
            .onSuccess {
                _uiState.value = _uiState.value.copy(
                    isAddParticipantsOpen = false,
                    participantSearch = "",
                    selectedParticipantIds = emptyList()
                )
            }
            .onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "No se pudieron anadir participantes") }
    }

    private fun hideConversation() = viewModelScope.launch {
        repository.hideConversation(conversationId)
            .onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "No se pudo borrar la conversacion") }
    }

    private fun deleteConversation() = viewModelScope.launch {
        repository.deleteConversation(conversationId)
            .onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "No se pudo borrar la conversacion") }
    }

    private fun leaveConversation() = viewModelScope.launch {
        repository.leaveConversation(conversationId)
            .onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "No se pudo abandonar la conversacion") }
    }

    private fun promoteModerator(userId: String) = viewModelScope.launch {
        repository.promoteModerator(conversationId, userId)
            .onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "No se pudo ascender") }
    }

    private fun removeParticipant(userId: String) = viewModelScope.launch {
        repository.removeParticipant(conversationId, userId)
            .onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "No se pudo expulsar") }
    }

    private fun blockParticipant(userId: String) = viewModelScope.launch {
        repository.blockParticipant(conversationId, userId)
            .onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "No se pudo bloquear") }
    }

    private fun selectedMessage() = _uiState.value.messages.firstOrNull { it.id == _uiState.value.selectedMessageId }

    private fun startReply() {
        selectedMessage()?.let { message ->
            _uiState.value = _uiState.value.copy(replyToMessage = message, selectedMessageId = null)
        }
    }

    private fun startEdit() {
        selectedMessage()?.takeIf { it.isMine && !it.isDeleted }?.let { message ->
            _uiState.value = _uiState.value.copy(
                editingMessage = message,
                messageText = message.text,
                selectedMessageId = null
            )
        }
    }

    private fun toggleFavoriteSelected() = viewModelScope.launch {
        val message = selectedMessage() ?: return@launch
        repository.toggleFavoriteMessage(message.id)
            .onSuccess { _uiState.value = _uiState.value.copy(selectedMessageId = null) }
            .onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "No se pudo actualizar favorito") }
    }

    private fun deleteSelectedMessage() = viewModelScope.launch {
        val message = selectedMessage()?.takeIf { it.isMine } ?: return@launch
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
        val message = selectedMessage() ?: return@launch
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
