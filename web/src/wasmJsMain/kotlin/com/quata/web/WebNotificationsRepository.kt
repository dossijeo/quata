package com.quata.web

import com.quata.feature.notifications.data.ConversationNotificationsRepository
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.notifications.domain.NotificationsRepository

/** Browser naming adapter; shared inbox behavior lives in [ConversationNotificationsRepository]. */
class WebNotificationsRepository(chatRepository: ChatRepository) : NotificationsRepository by
    ConversationNotificationsRepository(chatRepository)
