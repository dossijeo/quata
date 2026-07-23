package com.quata.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.quata.MainActivity
import com.quata.R
import com.quata.core.model.Conversation
import com.quata.core.navigation.quataChatUrl
import com.quata.core.text.localizedSosPreview

class NotificationFactory(private val context: Context) {
    fun showChatPush(
        conversationId: String,
        title: String,
        body: String,
        bodyKey: String = "",
        messageId: String? = null,
    ) {
        if (!hasNotificationPermission()) return
        val notificationId = chatNotificationId(conversationId)
        val localizedBody = localizedPushBody(bodyKey.ifBlank { body.notificationBodyKeyOrNull().orEmpty() })
            ?: context.localizedSosPreview(body)
            ?: body
        val intent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse(quataChatUrl(conversationId, messageId))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentTitle = title.ifBlank { context.getString(R.string.common_chat) }.boldNotificationTitle()
        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_SOCIAL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(contentTitle)
            .setContentText(localizedBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(localizedBody))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(replyAction(conversationId, notificationId))
            .build()
        if (notifySafely(notificationId, notification)) {
            rememberPostedNotificationId(notificationId)
        }
    }

    fun showChatMessage(conversation: Conversation) {
        if (conversation.isMuted || conversation.unreadCount <= 0) return
        if (!hasNotificationPermission()) return

        val notificationId = chatNotificationId(conversation.id)
        val contentTitle = conversation.notificationTitle().ifBlank {
            context.getString(R.string.common_chat)
        }.boldNotificationTitle()
        val contentText = (context.localizedSosPreview(conversation.lastMessagePreview) ?: conversation.lastMessagePreview)
            .takeIf { it.isNotBlank() }
            ?: context.getString(R.string.notification_new_message)
        val intent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse(quataChatUrl(conversation.id))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_SOCIAL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setNumber(conversation.unreadCount)
            .build()

        if (notifySafely(notificationId, notification)) {
            rememberPostedNotificationId(notificationId)
        }
    }

    fun showChatReplyFailed(conversationId: String) {
        if (!hasNotificationPermission()) return
        val notificationId = chatNotificationId(conversationId)
        val intent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse(quataChatUrl(conversationId))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentText = context.getString(R.string.notification_reply_failed_body)
        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_SOCIAL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notification_reply_failed_title).boldNotificationTitle())
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        if (notifySafely(notificationId, notification)) {
            rememberPostedNotificationId(notificationId)
        }
    }

    fun clearChatMessage(conversationId: String) {
        val notificationId = chatNotificationId(conversationId)
        context.getSystemService(NotificationManager::class.java)?.cancel(notificationId)
        NotificationManagerCompat.from(context).cancel(notificationId)
        forgetPostedNotificationId(notificationId)
    }

    fun replaceAnsweredChatMessageForDismissal(conversationId: String, replyText: String) {
        val notificationId = chatNotificationId(conversationId)
        if (hasNotificationPermission()) {
            val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_SOCIAL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.common_chat))
                .setContentText(context.getString(R.string.notification_reply_sent))
                .setRemoteInputHistory(arrayOf(replyText))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setLocalOnly(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setTimeoutAfter(REPLY_SENT_DISMISS_TIMEOUT_MILLIS)
                .build()
            notifySafely(notificationId, notification)
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifySafely(notificationId: Int, notification: android.app.Notification): Boolean {
        if (!hasNotificationPermission()) return false
        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun forgetPostedNotificationId(notificationId: Int) {
        val remainingIds = postedNotificationIds() - notificationId.toString()
        notificationPreferences()
            .edit()
            .putStringSet(KEY_POSTED_CHAT_NOTIFICATION_IDS, remainingIds)
            .apply()
    }

    fun clearChatMessages() {
        val manager = NotificationManagerCompat.from(context)
        postedNotificationIds()
            .mapNotNull { it.toIntOrNull() }
            .forEach(manager::cancel)
        notificationPreferences()
            .edit()
            .remove(KEY_POSTED_CHAT_NOTIFICATION_IDS)
            .apply()
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun localizedPushBody(bodyKey: String): String? = when (bodyKey) {
        BODY_KEY_VOICE_NOTE -> context.getString(R.string.notification_voice_note)
        BODY_KEY_ATTACHMENT -> context.getString(R.string.notification_attachment)
        BODY_KEY_MESSAGE -> context.getString(R.string.notification_new_message)
        else -> null
    }

    private fun replyAction(conversationId: String, notificationId: Int): NotificationCompat.Action {
        val replyIntent = Intent(context, QuataNotificationReplyReceiver::class.java).apply {
            action = QuataNotificationReplyReceiver.ACTION_REPLY
            putExtra(QuataNotificationReplyReceiver.EXTRA_CONVERSATION_ID, conversationId)
        }
        val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
        )
        val remoteInput = RemoteInput.Builder(QuataNotificationReplyReceiver.KEY_TEXT_REPLY)
            .setLabel(context.getString(R.string.notification_reply_hint))
            .build()
        return NotificationCompat.Action.Builder(
            R.drawable.ic_launcher_foreground,
            context.getString(R.string.notification_reply),
            pendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()
    }

    private fun Conversation.notificationTitle(): String = when {
        isEmergency -> title.ifBlank { "SOS" }
        communityName?.isNotBlank() == true -> communityName.orEmpty()
        title.isNotBlank() -> title
        isGroup -> participantNames.take(3).joinToString(", ")
        else -> context.getString(R.string.common_chat)
    }

    private fun String.boldNotificationTitle(): CharSequence =
        SpannableString(this).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

    private fun chatNotificationId(conversationId: String): Int =
        CHAT_NOTIFICATION_BASE_ID + (conversationId.hashCode() and 0x0FFFFFFF)

    private fun String.notificationBodyKeyOrNull(): String? {
        val trimmed = trim()
        if (!trimmed.startsWith(NOTIFICATION_SHORTCODE_PREFIX) || !trimmed.endsWith("]")) return null
        return trimmed
            .removePrefix(NOTIFICATION_SHORTCODE_PREFIX)
            .removeSuffix("]")
            .takeIf { it.isNotBlank() }
    }

    private fun rememberPostedNotificationId(notificationId: Int) {
        val updatedIds = postedNotificationIds() + notificationId.toString()
        notificationPreferences()
            .edit()
            .putStringSet(KEY_POSTED_CHAT_NOTIFICATION_IDS, updatedIds)
            .apply()
    }

    private fun postedNotificationIds(): Set<String> =
        notificationPreferences()
            .getStringSet(KEY_POSTED_CHAT_NOTIFICATION_IDS, emptySet())
            .orEmpty()
            .toSet()

    private fun notificationPreferences() =
        context.getSharedPreferences(CHAT_NOTIFICATION_PREFS, Context.MODE_PRIVATE)

    private companion object {
        const val CHAT_NOTIFICATION_BASE_ID = 20_000
        const val CHAT_NOTIFICATION_PREFS = "quata_chat_notifications"
        const val KEY_POSTED_CHAT_NOTIFICATION_IDS = "posted_chat_notification_ids"
        const val BODY_KEY_VOICE_NOTE = "chat_voice_note"
        const val BODY_KEY_ATTACHMENT = "chat_attachment"
        const val BODY_KEY_MESSAGE = "chat_message"
        const val NOTIFICATION_SHORTCODE_PREFIX = "[QUATA_NOTIFICATION:"
        const val REPLY_SENT_DISMISS_TIMEOUT_MILLIS = 500L
    }
}
