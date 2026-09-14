package com.quata.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.quata.QuataApp
import com.quata.feature.chat.data.supabaseThreadIdOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class QuataNotificationReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY) return
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return
        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_TEXT_REPLY)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (replyText.isBlank()) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var clearNotificationAfterFinish = false
            val factory = NotificationFactory(context.applicationContext)
            try {
                val app = context.applicationContext as? QuataApp
                val sent = app?.container?.let { container ->
                    val threadId = conversationId.supabaseThreadIdOrNull()
                    val session = runCatching { container.supabaseCommunityApi.ensureFreshSession() }
                        .getOrNull()
                        ?: container.sessionManager.currentSession()
                    if (threadId == null || session == null) {
                        false
                    } else {
                        sendNotificationReply(
                            send = { clientMessageId ->
                                container.supabaseCommunityApi.sendChatMessage(
                                    profileId = session.userId,
                                    threadId = threadId,
                                    message = replyText,
                                    clientMessageId = clientMessageId
                                )
                                Log.i(TAG, "Notification reply sent threadId=$threadId")
                            },
                            onFailure = { attempt, failure ->
                                Log.w(TAG, "Could not send notification reply attempt=$attempt", failure)
                            }
                        )
                    }
                } ?: false
                if (sent) {
                    clearNotificationAfterFinish = true
                } else {
                    factory.showChatReplyFailed(conversationId)
                }
            } finally {
                pendingResult.finish()
            }
            if (clearNotificationAfterFinish) {
                delay(NOTIFICATION_CLEAR_RETRY_MILLIS)
                factory.replaceAnsweredChatMessageForDismissal(conversationId, replyText)
                delay(NOTIFICATION_CLEAR_RETRY_MILLIS)
                factory.clearChatMessage(conversationId)
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "com.quata.action.REPLY_CHAT_NOTIFICATION"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val KEY_TEXT_REPLY = "text_reply"
        private const val NOTIFICATION_CLEAR_RETRY_MILLIS = 750L
        private const val TAG = "QuataNotificationReply"
    }
}
