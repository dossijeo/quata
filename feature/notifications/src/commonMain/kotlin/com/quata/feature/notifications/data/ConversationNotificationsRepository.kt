package com.quata.feature.notifications.data

import com.quata.core.model.NotificationItem
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.notifications.domain.NotificationsRepository
import com.quata.feature.notifications.domain.toConversationNotificationItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Portable notifications inbox backed by the chat conversations already available to a host.
 *
 * Platform delivery mechanisms (FCM, APNs and Web Push) remain responsible for waking the app;
 * this repository owns the shared in-app representation of unread chat conversations.
 */
class ConversationNotificationsRepository(
    private val chatRepository: ChatRepository,
) : NotificationsRepository {
    override suspend fun getNotifications(): Result<List<NotificationItem>> =
        chatRepository.getConversations().map { conversations ->
            conversations.toConversationNotificationItems(chatRepository.activeConversationId.value)
        }

    override suspend fun getNotificationCount(): Result<Int> =
        getNotifications().map { notifications -> notifications.sumOf(NotificationItem::unreadCount) }

    override fun observeNotifications(): Flow<List<NotificationItem>> =
        chatRepository.observeConversations().combine(chatRepository.activeConversationId) { conversations, activeConversationId ->
            conversations.toConversationNotificationItems(activeConversationId)
        }

    override fun observeNotificationCount(): Flow<Int> = observeNotifications()
        .map { notifications -> notifications.sumOf(NotificationItem::unreadCount) }
        .catch { emit(0) }

    override suspend fun markNotificationRead(notification: NotificationItem): Result<Unit> =
        chatRepository.markConversationRead(notification.conversationId)

    override suspend fun dismissNotification(notification: NotificationItem): Result<Unit> =
        markNotificationRead(notification)
}
