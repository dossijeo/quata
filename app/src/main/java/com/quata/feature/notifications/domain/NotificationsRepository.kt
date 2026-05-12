package com.quata.feature.notifications.domain

import com.quata.core.model.NotificationItem
import kotlinx.coroutines.flow.Flow

interface NotificationsRepository {
    suspend fun getNotifications(): Result<List<NotificationItem>>
    suspend fun getNotificationCount(): Result<Int>
    fun observeNotifications(): Flow<List<NotificationItem>>
    fun observeNotificationCount(): Flow<Int>
    suspend fun markNotificationRead(notification: NotificationItem): Result<Unit>
    suspend fun dismissNotification(notification: NotificationItem): Result<Unit>
}
