package com.quata.web

import com.quata.core.model.NotificationItem
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.notifications.domain.NotificationsRepository
import com.quata.feature.notifications.domain.toConversationNotificationItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Web inbox notifications derived from the authenticated chat inbox; Web Push remains delivery-only. */
class WebNotificationsRepository(private val chatRepository: ChatRepository) : NotificationsRepository {
    override suspend fun getNotifications(): Result<List<NotificationItem>> =
        chatRepository.getConversations().map { it.toConversationNotificationItems(chatRepository.activeConversationId.value) }

    override suspend fun getNotificationCount(): Result<Int> =
        getNotifications().map { items -> items.sumOf(NotificationItem::unreadCount) }

    override fun observeNotifications(): Flow<List<NotificationItem>> =
        chatRepository.observeConversations().map { it.toConversationNotificationItems(chatRepository.activeConversationId.value) }

    override fun observeNotificationCount(): Flow<Int> = observeNotifications()
        .map { items -> items.sumOf(NotificationItem::unreadCount) }
        .catch { emit(0) }

    override suspend fun markNotificationRead(notification: NotificationItem): Result<Unit> =
        chatRepository.markConversationRead(notification.conversationId)

    override suspend fun dismissNotification(notification: NotificationItem): Result<Unit> = markNotificationRead(notification)
}
