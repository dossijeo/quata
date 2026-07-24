package com.quata.feature.notifications.domain

import com.quata.core.model.Conversation
import com.quata.core.model.NotificationItem

const val ConversationNotificationPrefix = "notification_"

/** Maps unread visible chat threads to portable notification rows for every host. */
fun List<Conversation>.toConversationNotificationItems(activeConversationId: String?): List<NotificationItem> =
    filter { conversation ->
        conversation.id != activeConversationId && conversation.isVisible &&
            !conversation.isMuted && conversation.unreadCount > 0
    }.map { conversation ->
        NotificationItem(
            id = "$ConversationNotificationPrefix${conversation.id}",
            conversationId = conversation.id,
            title = when {
                conversation.isEmergency -> conversation.title.ifBlank { "SOS" }
                conversation.communityName?.isNotBlank() == true -> conversation.communityName.orEmpty()
                conversation.title.isNotBlank() -> conversation.title
                conversation.isGroup -> conversation.participantNames.take(3).joinToString(", ")
                else -> ""
            },
            body = conversation.lastMessagePreview.ifBlank { conversation.title },
            createdAt = conversation.updatedAt.ifBlank { "Ahora" },
            unreadCount = conversation.unreadCount,
        )
    }
