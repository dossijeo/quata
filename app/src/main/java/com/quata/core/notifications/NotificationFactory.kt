package com.quata.core.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.quata.MainActivity
import com.quata.R
import com.quata.core.model.Conversation
import com.quata.core.navigation.quataChatUrl

class NotificationFactory(private val context: Context) {
    fun showChatMessage(conversation: Conversation) {
        if (conversation.isMuted || conversation.unreadCount <= 0) return
        if (!hasNotificationPermission()) return

        val notificationId = chatNotificationId(conversation.id)
        val contentTitle = conversation.notificationTitle().ifBlank {
            context.getString(R.string.common_chat)
        }
        val contentText = conversation.lastMessagePreview
            .takeIf { it.isNotBlank() }
            ?: "Nuevo mensaje"
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

        NotificationManagerCompat.from(context).notify(notificationId, notification)
        rememberPostedNotificationId(notificationId)
    }

    fun clearChatMessage(conversationId: String) {
        val notificationId = chatNotificationId(conversationId)
        NotificationManagerCompat.from(context).cancel(notificationId)
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

    private fun Conversation.notificationTitle(): String = when {
        isEmergency -> title.ifBlank { "SOS" }
        communityName?.isNotBlank() == true -> communityName.orEmpty()
        title.isNotBlank() -> title
        isGroup -> participantNames.take(3).joinToString(", ")
        else -> context.getString(R.string.common_chat)
    }

    private fun chatNotificationId(conversationId: String): Int =
        CHAT_NOTIFICATION_BASE_ID + (conversationId.hashCode() and 0x0FFFFFFF)

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
    }
}
