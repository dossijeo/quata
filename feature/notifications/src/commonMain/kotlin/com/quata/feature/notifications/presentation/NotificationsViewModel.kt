package com.quata.feature.notifications.presentation

import com.quata.core.common.AppDispatchers
import com.quata.core.model.NotificationItem
import com.quata.feature.notifications.domain.NotificationsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotificationsViewModel(
    private val repository: NotificationsRepository,
    dispatchers: AppDispatchers = AppDispatchers()
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init { observe() }

    private fun observe() = scope.launch {
        repository.observeNotifications()
            .catch { error ->
                _uiState.value = NotificationsUiState(isLoading = false, error = error.message ?: "Error")
            }
            .collect { items ->
                _uiState.value = NotificationsUiState(isLoading = false, items = items)
            }
    }

    fun markRead(notification: NotificationItem) = scope.launch {
        repository.markNotificationRead(notification)
            .onFailure { error -> _uiState.value = _uiState.value.copy(error = error.message ?: "Error") }
    }

    fun dismiss(notification: NotificationItem) = scope.launch {
        repository.dismissNotification(notification)
            .onFailure { error -> _uiState.value = _uiState.value.copy(error = error.message ?: "Error") }
    }

    fun close() {
        scope.coroutineContext.cancel()
    }
}
