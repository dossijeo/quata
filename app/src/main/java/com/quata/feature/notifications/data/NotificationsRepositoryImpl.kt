package com.quata.feature.notifications.data

import com.quata.core.model.Conversation
import com.quata.core.model.NotificationItem
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.notifications.domain.NotificationsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class NotificationsRepositoryImpl(
    private val chatRepository: ChatRepository
) : NotificationsRepository {
    override suspend fun getNotifications(): Result<List<NotificationItem>> =
        chatRepository.getConversations().map { conversations ->
            conversations.toNotificationItems(chatRepository.activeConversationId.value)
        }

    override suspend fun getNotificationCount(): Result<Int> =
        getNotifications().map { notifications -> notifications.sumOf { it.unreadCount } }

    override fun observeNotifications(): Flow<List<NotificationItem>> =
        combine(
            chatRepository.observeConversations(),
            chatRepository.activeConversationId
        ) { conversations, activeConversationId ->
            conversations.toNotificationItems(activeConversationId)
        }

    override fun observeNotificationCount(): Flow<Int> =
        observeNotifications().map { notifications -> notifications.sumOf { it.unreadCount } }

    override suspend fun markNotificationRead(notification: NotificationItem): Result<Unit> =
        chatRepository.markConversationRead(notification.conversationId)

    override suspend fun dismissNotification(notification: NotificationItem): Result<Unit> =
        chatRepository.markConversationRead(notification.conversationId)
}

private fun List<Conversation>.toNotificationItems(activeConversationId: String?): List<NotificationItem> =
    filter { conversation ->
        conversation.id != activeConversationId &&
            conversation.isVisible &&
            !conversation.isMuted &&
            conversation.unreadCount > 0
    }.map { conversation ->
        NotificationItem(
            id = "notification_${conversation.id}",
            conversationId = conversation.id,
            title = conversation.notificationTitle(),
            body = conversation.lastMessagePreview.ifBlank { conversation.title },
            createdAt = conversation.updatedAt.ifBlank { "Ahora" },
            unreadCount = conversation.unreadCount
        )
    }

private fun Conversation.notificationTitle(): String = when {
    isEmergency -> title.ifBlank { "\uD83D\uDEA8 SOS" }
    communityName?.isNotBlank() == true -> communityName
    isGroup -> participantNames.take(3).joinToString(", ").ifBlank { title }
    else -> title
}.orEmpty()
