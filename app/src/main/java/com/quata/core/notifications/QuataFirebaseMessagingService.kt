package com.quata.core.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.quata.QuataApp
import com.quata.feature.chat.data.ChatMessageStateAckStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

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
        data["message_id"]?.toLongOrNull()?.let { messageId ->
            runBlocking(Dispatchers.IO) {
                quataApp?.container?.chatMessageStateAckManager?.markMessages(
                    messageIds = listOf(messageId),
                    status = ChatMessageStateAckStatus.Delivered,
                    source = "fcm"
                )
            }
        }
        if (quataApp?.isAppForeground == true) return
        val threadId = data["thread_id"]?.takeIf { it.isNotBlank() }
        val conversationId = data["conversation_id"]?.takeIf { it.isNotBlank() }
            ?: threadId?.let { "sb:$it" }
            ?: return
        NotificationChannels(this).ensureChannels()
        NotificationFactory(this).showChatPush(
            conversationId = conversationId,
            title = data["title"].orEmpty(),
            body = data["body"].orEmpty().ifBlank { data["message"].orEmpty() },
            bodyKey = data["body_key"].orEmpty()
        )
    }
}
