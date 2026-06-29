package com.quata.core.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.quata.QuataApp

class QuataFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        (application as? QuataApp)?.container?.pushTokenManager?.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val quataApp = application as? QuataApp
        if (quataApp?.isAppForeground == true) return
        val data = message.data
        if (data["type"] != "chat_message") return
        val threadId = data["thread_id"]?.takeIf { it.isNotBlank() }
        val conversationId = data["conversation_id"]?.takeIf { it.isNotBlank() }
            ?: threadId?.let { "sb:$it" }
            ?: return
        NotificationChannels(this).ensureChannels()
        NotificationFactory(this).showChatPush(
            conversationId = conversationId,
            title = data["title"].orEmpty(),
            body = data["body"].orEmpty().ifBlank { data["message"].orEmpty() }
        )
    }
}
