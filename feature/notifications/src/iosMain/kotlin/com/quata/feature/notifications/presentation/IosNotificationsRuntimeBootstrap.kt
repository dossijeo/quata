package com.quata.feature.notifications.presentation

import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.notifications.data.ConversationNotificationsRepository
import com.quata.feature.notifications.domain.NotificationsRepository

/**
 * Authenticated iOS composition for the in-app notification inbox.
 *
 * The common inbox is derived from the same authenticated [ChatRepository] used by the Chat
 * host, so unread-state flows survive route changes and notification taps. APNs registration,
 * token delivery and permission prompting remain launcher responsibilities.
 */
class IosNotificationsRuntimeBootstrap(
    chatRepository: ChatRepository,
) {
    private val notificationsRepository: NotificationsRepository =
        ConversationNotificationsRepository(chatRepository)

    /** One injected repository instance is retained for the lifetime of the authenticated host. */
    fun repository(): NotificationsRepository = notificationsRepository
}

/** Swift-facing factory that avoids relying on Kotlin constructor export details. */
fun createIosNotificationsRuntimeBootstrap(
    chatRepository: ChatRepository,
): IosNotificationsRuntimeBootstrap = IosNotificationsRuntimeBootstrap(chatRepository)
