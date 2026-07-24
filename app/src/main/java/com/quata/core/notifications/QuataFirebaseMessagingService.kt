package com.quata.core.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.quata.QuataApp
import com.quata.core.navigation.quataNotificationChatDeepLinkOrNull
import com.quata.feature.chat.data.ChatMessageStateWorkScheduler

class QuataFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        (application as? QuataApp)?.container?.pushTokenManager?.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val quataApp = application as? QuataApp
        val data = message.data
        if (data["type"] != "chat_message") return
        val expectedProfileId = data["recipient_profile_id"]?.takeIf { it.isNotBlank() }
        val currentProfileId = quataApp?.container?.sessionManager?.currentSession()?.userId
        if (expectedProfileId != null && expectedProfileId != currentProfileId) return
        val chatTarget = data.quataNotificationChatDeepLinkOrNull()
        val messageIdValue = chatTarget?.messageId ?: data["message_id"]?.takeIf { it.isNotBlank() }
        val messageId = messageIdValue?.toLongOrNull()

        // Posting the user-visible notification takes priority over background
        // bookkeeping during a cold or resource-constrained process start.
        if (quataApp?.isAppForeground != true) {
            chatTarget?.let { target ->
                NotificationFactory(this).showChatPush(
                    conversationId = target.conversationId,
                    title = data["title"].orEmpty(),
                    body = data["body"].orEmpty().ifBlank { data["message"].orEmpty() },
                    bodyKey = data["body_key"].orEmpty(),
                    messageId = messageIdValue,
                )
            }
        }

        if (currentProfileId != null && messageId != null) {
            ChatMessageStateWorkScheduler.scheduleDelivered(
                context = this,
                profileId = currentProfileId,
                messageId = messageId
            )
        }
    }
}
