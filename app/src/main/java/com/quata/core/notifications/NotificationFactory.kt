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

        val notificationId = CHAT_NOTIFICATION_BASE_ID + (conversation.id.hashCode() and 0x0FFFFFFF)
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

    private companion object {
        const val CHAT_NOTIFICATION_BASE_ID = 20_000
    }
}
