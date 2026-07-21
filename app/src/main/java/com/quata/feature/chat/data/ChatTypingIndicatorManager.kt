package com.quata.feature.chat.data

import com.quata.core.config.AppConfig
import com.quata.core.session.SessionManager
import com.quata.data.supabase.RealtimeRawEvent
import com.quata.data.supabase.RealtimeStatus
import com.quata.data.supabase.SupabaseRealtimeClient
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Broadcast-based typing state. It intentionally has neither storage nor offline replay. */
class ChatTypingIndicatorManager(
    private val realtimeClient: SupabaseRealtimeClient,
    private val sessionManager: SessionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _typingProfileIds = MutableStateFlow<Set<String>>(emptySet())
    val typingProfileIds: StateFlow<Set<String>> = _typingProfileIds.asStateFlow()

    private val remoteTypingAt = linkedMapOf<String, Long>()
    private var activeConversationId: String? = null
    private var appForeground = false
    private var networkAvailable = true
    private var channelSubscribed = false
    private var localTyping = false
    private var lastTypingActivityAt = 0L
    private var lastTypingBroadcastAt = 0L
    private var typingBroadcastJob: Job? = null
    private var localTypingIdleJob: Job? = null
    private var expiryJob: Job? = null

    fun setAppForeground(isForeground: Boolean) {
        if (appForeground == isForeground) return
        appForeground = isForeground
        if (isForeground) connectIfPossible() else disconnect(sendStop = true)
    }

    fun setDeviceNetworkAvailable(isAvailable: Boolean) {
        if (networkAvailable == isAvailable) return
        networkAvailable = isAvailable
        if (isAvailable) connectIfPossible() else disconnect(sendStop = false)
    }

    fun setVisibleConversation(conversationId: String, visible: Boolean) {
        if (!visible && activeConversationId == conversationId) {
            disconnect(sendStop = true)
            activeConversationId = null
            return
        }
        if (!visible) return
        if (activeConversationId == conversationId) return
        disconnect(sendStop = true)
        activeConversationId = conversationId
        connectIfPossible()
    }

    fun setTyping(conversationId: String, isTyping: Boolean) {
        if (activeConversationId != conversationId) return
        if (!isTyping) {
            stopLocalTyping()
            return
        }

        // A non-empty composer alone is not typing activity. Every text change refreshes
        // this timestamp so a paused draft stops advertising itself after three seconds.
        lastTypingActivityAt = System.currentTimeMillis()
        val wasTyping = localTyping
        localTyping = true
        scheduleLocalTypingTimeout()
        if (!wasTyping) {
            scheduleTypingBroadcast(force = true)
        }
    }

    private fun connectIfPossible() {
        val conversationId = activeConversationId ?: return
        if (AppConfig.USE_MOCK_BACKEND || !appForeground || !networkAvailable || channelSubscribed) return
        val session = sessionManager.currentSession()?.takeIf { it.isSupabaseAuthenticated() } ?: return
        realtimeClient.connectBroadcast(
            accessToken = session.bearerToken,
            presenceKey = session.userId,
            topic = "realtime:quata-typing-$conversationId",
            onEvent = ::onRealtimeEvent,
            onStatus = { status ->
                when (status) {
                    RealtimeStatus.Subscribed -> {
                        channelSubscribed = true
                        if (localTyping) scheduleTypingBroadcast(force = true)
                    }
                    RealtimeStatus.Closed, RealtimeStatus.Error -> channelSubscribed = false
                    else -> Unit
                }
            },
            onFailure = { channelSubscribed = false }
        )
    }

    private fun disconnect(sendStop: Boolean) {
        if (sendStop && localTyping) sendTyping(false)
        localTyping = false
        lastTypingActivityAt = 0L
        channelSubscribed = false
        typingBroadcastJob?.cancel()
        typingBroadcastJob = null
        localTypingIdleJob?.cancel()
        localTypingIdleJob = null
        expiryJob?.cancel()
        expiryJob = null
        remoteTypingAt.clear()
        _typingProfileIds.value = emptySet()
        realtimeClient.disconnect()
    }

    private fun scheduleTypingBroadcast(force: Boolean = false) {
        if (!localTyping) return
        typingBroadcastJob?.cancel()
        val elapsed = System.currentTimeMillis() - lastTypingBroadcastAt
        val delayMillis = if (force) 0L else (TYPING_BROADCAST_INTERVAL_MILLIS - elapsed).coerceAtLeast(0L)
        typingBroadcastJob = scope.launch {
            delay(delayMillis)
            if (localTyping) {
                if (System.currentTimeMillis() - lastTypingActivityAt >= TYPING_TIMEOUT_MILLIS) {
                    stopLocalTyping()
                    return@launch
                }
                // Reserve the cadence before the asynchronous network attempt. A failed
                // broadcast must never cause a tight retry loop from the text field.
                lastTypingBroadcastAt = System.currentTimeMillis()
                sendTyping(true)
                // Continue heartbeats only while keystrokes keep the local typing state fresh.
                scheduleTypingBroadcast()
            }
        }
    }

    private fun scheduleLocalTypingTimeout() {
        localTypingIdleJob?.cancel()
        val activityAt = lastTypingActivityAt
        localTypingIdleJob = scope.launch {
            delay(TYPING_TIMEOUT_MILLIS)
            if (localTyping && lastTypingActivityAt == activityAt) {
                stopLocalTyping()
            }
        }
    }

    private fun stopLocalTyping() {
        if (!localTyping) return
        localTyping = false
        lastTypingActivityAt = 0L
        typingBroadcastJob?.cancel()
        typingBroadcastJob = null
        localTypingIdleJob?.cancel()
        localTypingIdleJob = null
        sendTyping(false)
    }

    private fun sendTyping(isTyping: Boolean) {
        val session = sessionManager.currentSession() ?: return
        if (!channelSubscribed || !appForeground || !networkAvailable) return
        // Never let composer input wait for an unhealthy websocket. Broadcast itself is
        // non-blocking, and the dedicated IO worker is bounded as a final ANR safeguard.
        scope.launch {
            val sent = withTimeoutOrNull(BROADCAST_SEND_TIMEOUT_MILLIS) {
                realtimeClient.sendBroadcast(
                    event = "typing",
                    payload = buildJsonObject {
                        put("profile_id", session.userId)
                        put("is_typing", isTyping)
                    }
                )
            } ?: false
            Log.d(TAG, "Typing broadcast ${if (sent) "sent" else "not sent"}")
        }
    }

    private fun onRealtimeEvent(event: RealtimeRawEvent) {
        if (event.event != "broadcast") return
        val envelope = event.payload as? JsonObject ?: return
        if (envelope["event"]?.jsonPrimitive?.contentOrNull != "typing") return
        val payload = envelope["payload"]?.jsonObject ?: return
        val profileId = payload["profile_id"]?.jsonPrimitive?.contentOrNull ?: return
        if (profileId == sessionManager.currentSession()?.userId) return
        val isTyping = payload["is_typing"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: return
        if (isTyping) {
            remoteTypingAt[profileId] = System.currentTimeMillis()
            Log.d(TAG, "Typing received")
        } else {
            remoteTypingAt.remove(profileId)
        }
        publishTypingProfiles()
    }

    private fun publishTypingProfiles() {
        val now = System.currentTimeMillis()
        remoteTypingAt.entries.removeAll { now - it.value >= TYPING_TIMEOUT_MILLIS }
        _typingProfileIds.value = remoteTypingAt.keys.toSet()
        expiryJob?.cancel()
        val nextExpiry = remoteTypingAt.values.minOrNull()?.plus(TYPING_TIMEOUT_MILLIS) ?: return
        expiryJob = scope.launch {
            delay((nextExpiry - System.currentTimeMillis()).coerceAtLeast(1L))
            publishTypingProfiles()
        }
    }

    private companion object {
        const val TYPING_BROADCAST_INTERVAL_MILLIS = 2_000L
        const val TYPING_TIMEOUT_MILLIS = 3_000L
        const val BROADCAST_SEND_TIMEOUT_MILLIS = 750L
        const val TAG = "ChatTyping"
    }
}
