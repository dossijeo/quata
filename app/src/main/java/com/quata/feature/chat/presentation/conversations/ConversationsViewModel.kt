package com.quata.feature.chat.presentation.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quata.core.model.Conversation
import com.quata.feature.chat.domain.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ConversationsViewModel(private val repository: ChatRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()
    private var conversationsJob: Job? = null
    private var messagesJob: Job? = null
    private var usersJob: Job? = null
    private var pendingDeleteJob: Job? = null

    init { observe() }

    fun onEvent(event: ConversationsUiEvent) {
        when (event) {
            ConversationsUiEvent.Refresh -> observe()
            ConversationsUiEvent.RestoreDeletedConversation -> restoreDeletedConversation()
            ConversationsUiEvent.FinalizeDeletedConversation -> finalizeDeletedConversation()
        }
    }

    private fun observe() {
        conversationsJob?.cancel()
        usersJob?.cancel()
        _uiState.value = _uiState.value.copy(currentUser = repository.currentUser())
        usersJob = viewModelScope.launch {
            repository.observeParticipantCandidates()
                .catch { }
                .collect { users ->
                    val currentUser = repository.currentUser()
                    _uiState.value = _uiState.value.copy(
                        currentUser = currentUser,
                        usersById = (users + listOfNotNull(currentUser)).associateBy { it.id }
                    )
                }
        }
        pendingDeleteJob?.cancel()
        pendingDeleteJob = viewModelScope.launch {
            repository.pendingDeletedConversation.collect { conversation ->
                _uiState.value = _uiState.value.copy(pendingDeletedConversation = conversation)
            }
        }
        conversationsJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.observeConversations()
                .catch { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error.message ?: "Error cargando chats")
                }
                .collect { conversations ->
                    observeMessagesFor(conversations)
                }
        }
    }

    private fun restoreDeletedConversation() = viewModelScope.launch {
        repository.restorePendingDeletedConversation()
            .onFailure { error -> _uiState.value = _uiState.value.copy(error = error.message ?: "No se pudo restaurar") }
    }

    private fun finalizeDeletedConversation() = viewModelScope.launch {
        repository.finalizePendingDeletedConversation()
            .onFailure { error -> _uiState.value = _uiState.value.copy(error = error.message ?: "No se pudo borrar") }
    }

    private fun observeMessagesFor(conversations: List<Conversation>) {
        val messageFlows = conversations.map { conversation ->
            repository.observeMessages(conversation.id)
        }
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            if (messageFlows.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = false, conversations = emptyList(), messagesByConversation = emptyMap())
                return@launch
            }
            combine(messageFlows) { messages ->
                messages.toList()
            }
                .catch { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error.message ?: "Error cargando chats")
                }
                .collect { messageLists ->
                    val messagesByConversation = conversations.zip(messageLists).associate { (conversation, messages) ->
                        conversation.id to messages
                    }
                    val visibleConversations = conversations.filter { conversation ->
                        conversation.isVisible && messagesByConversation[conversation.id].orEmpty().isNotEmpty()
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        conversations = visibleConversations,
                        messagesByConversation = messagesByConversation
                    )
                }
        }
    }

    companion object {
        fun factory(repository: ChatRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ConversationsViewModel(repository) as T
        }
    }
}
