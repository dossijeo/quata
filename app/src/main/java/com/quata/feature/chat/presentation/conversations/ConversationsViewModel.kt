package com.quata.feature.chat.presentation.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quata.feature.chat.domain.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConversationsViewModel(private val repository: ChatRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()

    init { load() }

    fun onEvent(event: ConversationsUiEvent) { if (event is ConversationsUiEvent.Refresh) load() }

    private fun load() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        repository.getConversations()
            .onSuccess { _uiState.value = ConversationsUiState(isLoading = false, conversations = it) }
            .onFailure { _uiState.value = ConversationsUiState(isLoading = false, error = it.message ?: "Error cargando chats") }
    }

    companion object {
        fun factory(repository: ChatRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ConversationsViewModel(repository) as T
        }
    }
}
