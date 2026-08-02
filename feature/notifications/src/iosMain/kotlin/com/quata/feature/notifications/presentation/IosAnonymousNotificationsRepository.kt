package com.quata.feature.notifications.presentation

import com.quata.core.model.NotificationItem
import com.quata.feature.notifications.domain.NotificationsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Anonymous Inbox is a real public route, but notification data is personal.  It therefore
 * exposes the honest empty state and never converts an authenticated transport failure to empty.
 */
class IosAnonymousNotificationsRepository : NotificationsRepository {
    override suspend fun getNotifications(): Result<List<NotificationItem>> = Result.success(emptyList())
    override suspend fun getNotificationCount(): Result<Int> = Result.success(0)
    override fun observeNotifications(): Flow<List<NotificationItem>> = flowOf(emptyList())
    override fun observeNotificationCount(): Flow<Int> = flowOf(0)
    override suspend fun markNotificationRead(notification: NotificationItem): Result<Unit> = Result.failure(AuthRequiredForNotifications())
    override suspend fun dismissNotification(notification: NotificationItem): Result<Unit> = Result.failure(AuthRequiredForNotifications())
}

private class AuthRequiredForNotifications : IllegalStateException("auth_required")

fun createIosAnonymousNotificationsRepository(): NotificationsRepository = IosAnonymousNotificationsRepository()
