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
                    conversation = conversations.firstOrNull { it.id == conversationId }
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
            is ChatUiEvent.ConversationMutedChanged -> setMuted(event.muted)
            ChatUiEvent.OpenAddParticipants -> _uiState.value = _uiState.value.copy(isAddParticipantsOpen = true)
            ChatUiEvent.CloseAddParticipants -> _uiState.value = _uiState.value.copy(
                isAddParticipantsOpen = false,
                participantSearch = "",
                selectedParticipantIds = emptyList()
            )
            ChatUiEvent.AddSelectedParticipants -> addParticipants()
            ChatUiEvent.HideConversation -> hideConversation()
            ChatUiEvent.Send -> send()
        }
    }

    private fun send() = viewModelScope.launch {
        val text = _uiState.value.messageText
        repository.sendMessage(conversationId, text)
            .onSuccess { _uiState.value = _uiState.value.copy(messageText = "") }
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
