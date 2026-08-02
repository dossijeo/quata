package com.quata.feature.notifications.presentation

import com.quata.core.common.AppDispatchers
import com.quata.core.model.NotificationItem
import com.quata.feature.notifications.domain.NotificationsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.job

class NotificationsViewModel(
    private val repository: NotificationsRepository,
    dispatchers: AppDispatchers = AppDispatchers(),
    private val initialLoadTimeoutMillis: Long = InitialLoadTimeoutMillis,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    private var observation: Job? = null
    private var attempt = 0L

    init { observe() }

    private fun observe() {
        observation?.cancel()
        val currentAttempt = ++attempt
        _uiState.value = NotificationsUiState()
        observation = scope.launch {
            var receivedFirstValue = false
            val observerJob = coroutineContext.job
            val watchdog = launch {
                delay(initialLoadTimeoutMillis.coerceAtLeast(1L))
                if (!receivedFirstValue && currentAttempt == attempt) {
                    _uiState.value = NotificationsUiState(isLoading = false, error = "notifications_initial_load_timeout")
                    observerJob.cancel(InitialLoadTimeoutCancellation())
                }
            }
            try {
                repository.observeNotifications().collect { items ->
                    receivedFirstValue = true
                    watchdog.cancel()
                    if (currentAttempt == attempt) _uiState.value = NotificationsUiState(isLoading = false, items = items)
                }
                if (!receivedFirstValue && currentAttempt == attempt) {
                    _uiState.value = NotificationsUiState(isLoading = false, error = "notifications_stream_completed_without_value")
                }
            } catch (error: Throwable) {
                ensureActive()
                if (currentAttempt == attempt) _uiState.value = NotificationsUiState(isLoading = false, error = error.message ?: "Error")
            } finally {
                watchdog.cancel()
            }
        }
    }

    fun retry() = observe()

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

    private companion object { const val InitialLoadTimeoutMillis = 15_000L }
}

private class InitialLoadTimeoutCancellation : CancellationException("notifications_initial_load_timeout")
