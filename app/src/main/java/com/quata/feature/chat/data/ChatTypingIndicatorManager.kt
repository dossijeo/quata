package com.quata.feature.chat.data

import android.util.Log
import com.quata.core.config.AppConfig
import com.quata.core.session.SessionManager
import com.quata.data.supabase.RealtimeBroadcastClient
import com.quata.data.supabase.RealtimeRawEvent
import com.quata.data.supabase.RealtimeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Broadcast-based typing state. It intentionally has neither storage nor offline replay. */
class ChatTypingIndicatorManager(
    private val realtimeClient: RealtimeBroadcastClient,
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
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private var connectionGeneration = 0L
    private var channelConnecting = false

    @Synchronized
    fun setAppForeground(isForeground: Boolean) {
        if (appForeground == isForeground) return
        appForeground = isForeground
        if (isForeground) connectIfPossible() else disconnect(sendStop = true)
    }

    @Synchronized
    fun setDeviceNetworkAvailable(isAvailable: Boolean) {
        if (networkAvailable == isAvailable) return
        networkAvailable = isAvailable
        if (isAvailable) connectIfPossible() else disconnect(sendStop = false)
    }

    @Synchronized
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

    @Synchronized
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

    @Synchronized
    private fun connectIfPossible() {
        val conversationId = activeConversationId ?: return
        if (
            AppConfig.USE_MOCK_BACKEND ||
            !appForeground ||
            !networkAvailable ||
            channelSubscribed ||
            channelConnecting
        ) return
        val session = sessionManager.currentSession()?.takeIf { it.isSupabaseAuthenticated() } ?: return
        reconnectJob?.cancel()
        reconnectJob = null
        channelConnecting = true
        val generation = ++connectionGeneration
        runCatching {
            realtimeClient.connectBroadcast(
                accessToken = session.bearerToken,
                presenceKey = session.userId,
                topic = "realtime:quata-typing-$conversationId",
                onEvent = { event -> handleRealtimeEvent(generation, event) },
                onStatus = { status -> handleRealtimeStatus(generation, status) },
                onFailure = { handleConnectionLoss(generation) }
            )
        }.onFailure {
            channelConnecting = false
            scheduleReconnect()
        }
    }

    @Synchronized
    private fun handleRealtimeEvent(generation: Long, event: RealtimeRawEvent) {
        if (generation != connectionGeneration) return
        onRealtimeEvent(event)
    }

    @Synchronized
    private fun handleRealtimeStatus(generation: Long, status: RealtimeStatus) {
        if (generation != connectionGeneration) return
        when (status) {
            RealtimeStatus.Subscribed -> {
                channelConnecting = false
                channelSubscribed = true
                reconnectAttempt = 0
                if (localTyping) scheduleTypingBroadcast(force = true)
            }
            RealtimeStatus.Closed, RealtimeStatus.Error -> handleConnectionLoss(generation)
            else -> Unit
        }
    }

    @Synchronized
    private fun handleConnectionLoss(generation: Long) {
        if (generation != connectionGeneration) return
        channelConnecting = false
        channelSubscribed = false
        expiryJob?.cancel()
        expiryJob = null
        remoteTypingAt.clear()
        _typingProfileIds.value = emptySet()
        scheduleReconnect()
    }

    @Synchronized
    private fun scheduleReconnect() {
        if (
            AppConfig.USE_MOCK_BACKEND ||
            !appForeground ||
            !networkAvailable ||
            activeConversationId == null ||
            reconnectJob?.isActive == true
        ) return
        val delayMillis = (RECONNECT_BASE_DELAY_MILLIS * (1L shl reconnectAttempt.coerceAtMost(4)))
            .coerceAtMost(RECONNECT_MAX_DELAY_MILLIS)
        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(5)
        reconnectJob = scope.launch {
            delay(delayMillis)
            synchronized(this@ChatTypingIndicatorManager) {
                reconnectJob = null
                connectIfPossible()
            }
        }
    }

    @Synchronized
    private fun disconnect(sendStop: Boolean) {
        if (sendStop && localTyping) sendTyping(false)
        connectionGeneration += 1
        channelConnecting = false
        localTyping = false
        lastTypingActivityAt = 0L
        channelSubscribed = false
        typingBroadcastJob?.cancel()
        typingBroadcastJob = null
        localTypingIdleJob?.cancel()
        localTypingIdleJob = null
        expiryJob?.cancel()
        expiryJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
        remoteTypingAt.clear()
        _typingProfileIds.value = emptySet()
        realtimeClient.disconnect()
    }

    @Synchronized
    private fun scheduleTypingBroadcast(force: Boolean = false) {
        if (!localTyping) return
        typingBroadcastJob?.cancel()
        val elapsed = System.currentTimeMillis() - lastTypingBroadcastAt
        val delayMillis = if (force) 0L else (TYPING_BROADCAST_INTERVAL_MILLIS - elapsed).coerceAtLeast(0L)
        typingBroadcastJob = scope.launch {
            delay(delayMillis)
            synchronized(this@ChatTypingIndicatorManager) {
                if (localTyping) {
                    if (System.currentTimeMillis() - lastTypingActivityAt >= TYPING_TIMEOUT_MILLIS) {
                        stopLocalTyping()
                        return@synchronized
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
    }

    @Synchronized
    private fun scheduleLocalTypingTimeout() {
        localTypingIdleJob?.cancel()
        val activityAt = lastTypingActivityAt
        localTypingIdleJob = scope.launch {
            delay(TYPING_TIMEOUT_MILLIS)
            synchronized(this@ChatTypingIndicatorManager) {
                if (localTyping && lastTypingActivityAt == activityAt) {
                    stopLocalTyping()
                }
            }
        }
    }

    @Synchronized
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

    @Synchronized
    private fun sendTyping(isTyping: Boolean) {
        val session = sessionManager.currentSession() ?: return
        if (!channelSubscribed || !appForeground || !networkAvailable) return
        // RealtimeBroadcastClient.sendBroadcast delegates to WebSocket.send, which only
        // enqueues a frame. Keep that non-blocking enqueue inside the lifecycle monitor so
        // the frame cannot migrate to a later conversation and a final stop precedes close.
        val sent = realtimeClient.sendBroadcast(
            event = "typing",
            payload = buildJsonObject {
                put("profile_id", session.userId)
                put("is_typing", isTyping)
            }
        )
        runCatching { Log.d(TAG, "Typing broadcast ${if (sent) "sent" else "not sent"}") }
    }

    @Synchronized
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

    @Synchronized
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
        const val RECONNECT_BASE_DELAY_MILLIS = 500L
        const val RECONNECT_MAX_DELAY_MILLIS = 8_000L
        const val TAG = "ChatTyping"
    }
}
