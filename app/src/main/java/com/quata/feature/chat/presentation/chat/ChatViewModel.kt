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
        viewModelScope.launch {
            repository.observeMessages(conversationId).collect { messages ->
                _uiState.value = _uiState.value.copy(messages = messages, isLoading = false)
            }
        }
    }

    fun onEvent(event: ChatUiEvent) {
        when (event) {
            is ChatUiEvent.MessageChanged -> _uiState.value = _uiState.value.copy(messageText = event.value)
            ChatUiEvent.Send -> send()
        }
    }

    private fun send() = viewModelScope.launch {
        val text = _uiState.value.messageText
        repository.sendMessage(conversationId, text)
            .onSuccess { _uiState.value = _uiState.value.copy(messageText = "") }
            .onFailure { _uiState.value = _uiState.value.copy(error = it.message ?: "No se pudo enviar") }
    }

    companion object {
        fun factory(conversationId: String, repository: ChatRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(conversationId, repository) as T
        }
    }
}
