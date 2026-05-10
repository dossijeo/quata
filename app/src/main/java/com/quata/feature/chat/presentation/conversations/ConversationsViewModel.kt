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

    init { observe() }

    fun onEvent(event: ConversationsUiEvent) { if (event is ConversationsUiEvent.Refresh) observe() }

    private fun observe() {
        conversationsJob?.cancel()
        conversationsJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.observeConversations()
                .catch { error ->
                    _uiState.value = ConversationsUiState(isLoading = false, error = error.message ?: "Error cargando chats")
                }
                .collect { conversations ->
                    observeMessagesFor(conversations)
                }
        }
    }

    private fun observeMessagesFor(conversations: List<Conversation>) {
        val messageFlows = conversations.map { conversation ->
            repository.observeMessages(conversation.id)
        }
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            if (messageFlows.isEmpty()) {
                _uiState.value = ConversationsUiState(isLoading = false, conversations = emptyList())
                return@launch
            }
            combine(messageFlows) { messages ->
                messages.toList()
            }
                .catch { error ->
                    _uiState.value = ConversationsUiState(isLoading = false, error = error.message ?: "Error cargando chats")
                }
                .collect { messageLists ->
                    val messagesByConversation = conversations.zip(messageLists).associate { (conversation, messages) ->
                        conversation.id to messages
                    }
                    val visibleConversations = conversations.filter { conversation ->
                        messagesByConversation[conversation.id].orEmpty().isNotEmpty()
                    }
                    _uiState.value = ConversationsUiState(
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
