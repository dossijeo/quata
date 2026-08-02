package com.quata.feature.notifications.data

import com.quata.core.model.NotificationItem
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.notifications.domain.NotificationsRepository
import com.quata.feature.notifications.domain.toConversationNotificationItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

/**
 * Portable notifications inbox backed by the chat conversations already available to a host.
 *
 * Platform delivery mechanisms (FCM, APNs and Web Push) remain responsible for waking the app;
 * this repository owns the shared in-app representation of unread chat conversations.
 */
class ConversationNotificationsRepository(
    private val chatRepository: ChatRepository,
    private val pollIntervalMillis: Long = 15_000L,
) : NotificationsRepository {
    override suspend fun getNotifications(): Result<List<NotificationItem>> =
        chatRepository.getConversations().map { conversations ->
            conversations.toConversationNotificationItems(chatRepository.activeConversationId.value)
        }

    override suspend fun getNotificationCount(): Result<Int> =
        getNotifications().map { notifications -> notifications.sumOf(NotificationItem::unreadCount) }

    override fun observeNotifications(): Flow<List<NotificationItem>> = observeConversationNotifications(
        loadConversations = { chatRepository.getConversations().getOrThrow() },
        isAppForeground = chatRepository.isAppForeground,
        activeConversationId = chatRepository.activeConversationId,
        pollIntervalMillis = pollIntervalMillis,
    )

    override fun observeNotificationCount(): Flow<Int> = observeNotifications()
        .map { notifications -> notifications.sumOf(NotificationItem::unreadCount) }

    override suspend fun markNotificationRead(notification: NotificationItem): Result<Unit> =
        chatRepository.markConversationRead(notification.conversationId)

    override suspend fun dismissNotification(notification: NotificationItem): Result<Unit> =
        markNotificationRead(notification)
}

/** Remote reads are interval-bounded; active-chat changes remap only the latest local snapshot. */
internal fun observeConversationNotifications(
    loadConversations: suspend () -> List<com.quata.core.model.Conversation>,
    isAppForeground: Flow<Boolean>,
    activeConversationId: Flow<String?>,
    pollIntervalMillis: Long,
): Flow<List<NotificationItem>> {
    val remoteSnapshots = flow {
        while (currentCoroutineContext().isActive) {
            isAppForeground.filter { it }.first()
            emit(loadConversations())
            delay(pollIntervalMillis.coerceAtLeast(1L))
        }
    }
    return remoteSnapshots.combine(activeConversationId) { conversations, activeId ->
        conversations.toConversationNotificationItems(activeId)
    }
}
