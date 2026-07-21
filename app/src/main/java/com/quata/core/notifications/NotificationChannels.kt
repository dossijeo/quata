package com.quata.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.quata.R

class NotificationChannels(private val context: Context) {
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_SOCIAL,
                "Qüata social",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SOS_LOCATION,
                    context.getString(R.string.sos_location_recovery_channel),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.sos_location_recovery_channel_description)
                    setShowBadge(false)
                }
            )
        }
    }

    companion object {
        const val CHANNEL_SOCIAL = "quata_social"
        const val CHANNEL_SOS_LOCATION = "quata_sos_location"
    }
}
