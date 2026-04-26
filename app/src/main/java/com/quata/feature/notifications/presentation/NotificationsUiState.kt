package com.quata.feature.notifications.presentation

import com.quata.core.model.NotificationItem

data class NotificationsUiState(
    val isLoading: Boolean = true,
    val items: List<NotificationItem> = emptyList(),
    val error: String? = null
)
