package com.quata.feature.notifications.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quata.core.model.NotificationItem
import com.quata.feature.notifications.domain.NotificationsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class NotificationsViewModel(private val repository: NotificationsRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init { observe() }

    private fun observe() = viewModelScope.launch {
        repository.observeNotifications()
            .catch { error ->
                _uiState.value = NotificationsUiState(isLoading = false, error = error.message ?: "Error")
            }
            .collect { items ->
                _uiState.value = NotificationsUiState(isLoading = false, items = items)
            }
    }

    fun markRead(notification: NotificationItem) = viewModelScope.launch {
        repository.markNotificationRead(notification)
            .onFailure { error -> _uiState.value = _uiState.value.copy(error = error.message ?: "Error") }
    }

    fun dismiss(notification: NotificationItem) = viewModelScope.launch {
        repository.dismissNotification(notification)
            .onFailure { error -> _uiState.value = _uiState.value.copy(error = error.message ?: "Error") }
    }

    companion object {
        fun factory(repository: NotificationsRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = NotificationsViewModel(repository) as T
        }
    }
}
