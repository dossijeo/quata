package com.quata.feature.notifications.domain

import com.quata.core.model.NotificationItem

interface NotificationsRepository {
    suspend fun getNotifications(): Result<List<NotificationItem>>
}
