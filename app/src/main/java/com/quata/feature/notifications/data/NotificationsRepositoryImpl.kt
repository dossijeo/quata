package com.quata.feature.notifications.data

import com.quata.core.data.MockData
import com.quata.core.model.NotificationItem
import com.quata.feature.notifications.domain.NotificationsRepository

class NotificationsRepositoryImpl : NotificationsRepository {
    override suspend fun getNotifications(): Result<List<NotificationItem>> = Result.success(MockData.notifications)
    override suspend fun getNotificationCount(): Result<Int> = getNotifications().map { it.size }
}
