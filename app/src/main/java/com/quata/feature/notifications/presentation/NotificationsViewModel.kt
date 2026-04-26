package com.quata.feature.notifications.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quata.feature.notifications.domain.NotificationsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotificationsViewModel(private val repository: NotificationsRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init { load() }

    private fun load() = viewModelScope.launch {
        repository.getNotifications()
            .onSuccess { _uiState.value = NotificationsUiState(isLoading = false, items = it) }
            .onFailure { _uiState.value = NotificationsUiState(isLoading = false, error = it.message ?: "Error") }
    }

    companion object {
        fun factory(repository: NotificationsRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = NotificationsViewModel(repository) as T
        }
    }
}
