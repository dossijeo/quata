package com.quata.core.location

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.quata.MainActivity
import com.quata.QuataApp
import com.quata.R
import com.quata.core.navigation.quataChatUrl
import com.quata.core.notifications.NotificationChannels
import com.quata.core.text.SosShortcodeKind
import com.quata.core.text.buildSosShortcode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Completes a user-triggered SOS with a fresher location without tracking the
 * device beyond the bounded recovery window.
 */
class SosLocationRecoveryService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recoveryJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val request = intent?.toRecoveryRequest()
        if (request == null || !hasQuataLocationPermission()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startAsForeground(request.conversationId)
        recoveryJob?.cancel()
        recoveryJob = serviceScope.launch {
            try {
                recoverLocation(request)
            } finally {
                stopSelf(startId)
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        recoveryJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun recoverLocation(request: RecoveryRequest) {
        if (!hasExpectedSession(request.expectedProfileId)) return
        val precise = quataPreciseLocationWithRetries()
            ?.takeIf { it.isNewerThan(request.initialLocationTimeMillis) }
        if (precise != null) {
            sendLocationUpdate(request, precise)
            return
        }

        val remainingMillis = request.deadlineMillis - System.currentTimeMillis()
        if (remainingMillis <= 0L) return
        Log.d(TAG, "Precise SOS recovery failed; waiting passively for ${remainingMillis}ms")
        val passive = quataPassiveLocation(remainingMillis)
            ?.takeIf { it.isNewerThan(request.initialLocationTimeMillis) }
            ?: return
        sendLocationUpdate(request, passive)
    }

    private suspend fun sendLocationUpdate(request: RecoveryRequest, location: Location) {
        if (!hasExpectedSession(request.expectedProfileId)) return
        val text = buildSosShortcode(
            kind = SosShortcodeKind.LocationUpdate,
            senderName = request.senderName,
            customMessage = request.customMessage,
            latitude = location.latitude,
            longitude = location.longitude,
            ageMillis = max(0L, System.currentTimeMillis() - location.time),
            accuracyMeters = location.takeIf(Location::hasAccuracy)?.accuracy?.toDouble(),
            speedKmh = location.takeIf(Location::hasSpeed)?.speed?.times(3.6f)?.toDouble()
        )
        val app = application as? QuataApp ?: return
        app.container.chatRepository.sendMessage(
            conversationId = request.conversationId,
            text = text,
            clientMessageId = "sos-location-${request.conversationId}-${request.startedAtMillis}"
        ).onFailure { Log.w(TAG, "Could not queue recovered SOS location", it) }
    }

    private fun hasExpectedSession(profileId: String): Boolean =
        (application as? QuataApp)?.container?.sessionManager?.currentSession()?.userId == profileId

    private fun startAsForeground(conversationId: String) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            data = android.net.Uri.parse(quataChatUrl(conversationId))
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, NotificationChannels.CHANNEL_SOS_LOCATION)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.sos_location_recovery_title))
            .setContentText(getString(R.string.sos_location_recovery_body))
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundType)
    }

    private fun Intent.toRecoveryRequest(): RecoveryRequest? {
        val conversationId = getStringExtra(EXTRA_CONVERSATION_ID)?.takeIf(String::isNotBlank) ?: return null
        val expectedProfileId = getStringExtra(EXTRA_PROFILE_ID)?.takeIf(String::isNotBlank) ?: return null
        val senderName = getStringExtra(EXTRA_SENDER_NAME)?.takeIf(String::isNotBlank) ?: return null
        val startedAt = getLongExtra(EXTRA_STARTED_AT, 0L).takeIf { it > 0L } ?: return null
        val deadline = getLongExtra(EXTRA_DEADLINE, 0L).takeIf { it > System.currentTimeMillis() } ?: return null
        return RecoveryRequest(
            conversationId = conversationId,
            expectedProfileId = expectedProfileId,
            senderName = senderName,
            customMessage = getStringExtra(EXTRA_CUSTOM_MESSAGE),
            initialLocationTimeMillis = getLongExtra(EXTRA_INITIAL_LOCATION_TIME, 0L),
            startedAtMillis = startedAt,
            deadlineMillis = deadline
        )
    }

    private data class RecoveryRequest(
        val conversationId: String,
        val expectedProfileId: String,
        val senderName: String,
        val customMessage: String?,
        val initialLocationTimeMillis: Long,
        val startedAtMillis: Long,
        val deadlineMillis: Long
    )

    companion object {
        private const val TAG = "QuataSosLocation"
        private const val NOTIFICATION_ID = 7302
        private const val RECOVERY_WINDOW_MILLIS = 30L * 60L * 1000L
        private const val EXTRA_CONVERSATION_ID = "conversation_id"
        private const val EXTRA_PROFILE_ID = "profile_id"
        private const val EXTRA_SENDER_NAME = "sender_name"
        private const val EXTRA_CUSTOM_MESSAGE = "custom_message"
        private const val EXTRA_INITIAL_LOCATION_TIME = "initial_location_time"
        private const val EXTRA_STARTED_AT = "started_at"
        private const val EXTRA_DEADLINE = "deadline"

        fun start(
            context: Context,
            conversationId: String,
            expectedProfileId: String,
            senderName: String,
            customMessage: String?,
            initialLocationTimeMillis: Long
        ) {
            val startedAt = System.currentTimeMillis()
            val intent = Intent(context, SosLocationRecoveryService::class.java).apply {
                putExtra(EXTRA_CONVERSATION_ID, conversationId)
                putExtra(EXTRA_PROFILE_ID, expectedProfileId)
                putExtra(EXTRA_SENDER_NAME, senderName)
                putExtra(EXTRA_CUSTOM_MESSAGE, customMessage)
                putExtra(EXTRA_INITIAL_LOCATION_TIME, initialLocationTimeMillis)
                putExtra(EXTRA_STARTED_AT, startedAt)
                putExtra(EXTRA_DEADLINE, startedAt + RECOVERY_WINDOW_MILLIS)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

private fun Location.isNewerThan(previousTimeMillis: Long): Boolean =
    time > previousTimeMillis && time >= System.currentTimeMillis() - MAX_ACCEPTED_LOCATION_AGE_MILLIS

private const val MAX_ACCEPTED_LOCATION_AGE_MILLIS = 2L * 60L * 1000L
