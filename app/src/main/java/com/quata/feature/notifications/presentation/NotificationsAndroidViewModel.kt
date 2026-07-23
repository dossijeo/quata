package com.quata.feature.notifications.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.quata.core.model.NotificationItem
import com.quata.feature.notifications.domain.NotificationsRepository
import kotlinx.coroutines.flow.StateFlow

/** Android lifecycle adapter for shared notifications presentation logic. */
class NotificationsAndroidViewModel(repository: NotificationsRepository) : ViewModel() {
    private val delegate = NotificationsViewModel(repository)
    val uiState: StateFlow<NotificationsUiState> = delegate.uiState
    fun markRead(notification: NotificationItem) = delegate.markRead(notification)
    fun dismiss(notification: NotificationItem) = delegate.dismiss(notification)

    override fun onCleared() = delegate.close()

    companion object {
        fun factory(repository: NotificationsRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = NotificationsAndroidViewModel(repository) as T
        }
    }
}
