@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import com.quata.feature.feed.presentation.FeedPresenceHeartbeatMillis
import com.quata.feature.feed.presentation.FeedPresenceSnapshot
import com.quata.feature.feed.presentation.FeedPresenceTopic
import com.quata.feature.feed.presentation.FeedUserPresence
import com.quata.feature.feed.presentation.feedPresenceReconnectDelayMillis
import com.quata.feature.feed.presentation.isSuccessfulFeedPresenceJoinReply
import com.quata.feature.feed.presentation.shouldConnectFeedPresence
import kotlinx.browser.document
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Browser implementation of the same Phoenix Presence protocol used by Android. */
class WebFeedPresence(
    private val configuration: WebRuntimeConfiguration,
    private val authRepository: WebAuthRepository,
) : FeedUserPresence {
    private val online = MutableStateFlow<Set<String>>(emptySet())
    override val onlineProfileIds: StateFlow<Set<String>> = online
    private var snapshot = FeedPresenceSnapshot()
    private var foreground = !document.hidden
    private var networkAvailable = true
    private var closed = false
    private var reconnectAttempt = 0
    private var socket: dynamic = null
    private var reconnectTimer: Int? = null
    private var heartbeatTimer: Int? = null
    private var activeJoinRef: String? = null
    private val disposeLifecycle: () -> Unit
    private var ref = 0

    init {
        disposeLifecycle = observeWebPresenceLifecycle({ setForeground(it) }, { setNetworkAvailable(it) })
    }

    override fun observeProfiles(profileIds: Collection<String>) { snapshot = snapshot.observe(profileIds); publish() }
    override fun setForeground(isForeground: Boolean) { if (!closed) { foreground = isForeground; reconcile() } }
    override fun setNetworkAvailable(isAvailable: Boolean) { if (!closed) { networkAvailable = isAvailable; reconcile() } }

    override fun close() {
        if (closed) return
        closed = true
        foreground = false
        disposeLifecycle()
        disconnect("feed-closed")
    }

    private fun reconcile() {
        if (closed) return
        if (!shouldConnectFeedPresence(foreground, networkAvailable, authRepository.activeProfileSessionOrNull() != null)) {
            disconnect("feed-paused")
        } else if (socket == null) connect()
    }

    private fun connect() {
        val session = authRepository.activeProfileSessionOrNull() ?: return
        val url = configuration.supabaseUrl?.trim()?.trimEnd('/')?.replaceFirst("https://", "wss://")
            ?.replaceFirst("http://", "ws://") ?: return
        val ws: dynamic = js("new WebSocket(url + '/realtime/v1/websocket?apikey=' + encodeURIComponent(configuration.supabasePublishableKey) + '&vsn=2.0.0')")
        socket = ws
        ws.onopen = {
            if (socket === ws && !closed) {
                val joinRef = nextRef()
                activeJoinRef = joinRef
                send(joinRef, joinRef, FeedPresenceTopic, "phx_join", buildJoinPayload(session.accessToken, session.userId))
                startHeartbeat(ws)
            }
        }
        ws.onmessage = { event: dynamic -> if (socket === ws) onMessage(event.data as String, session.userId) }
        ws.onclose = { if (socket === ws) { socket = null; stopHeartbeat(); activeJoinRef = null; scheduleReconnect() } }
        ws.onerror = { if (socket === ws) { socket = null; stopHeartbeat(); activeJoinRef = null; scheduleReconnect() } }
    }

    private fun onMessage(text: String, profileId: String) {
        val frame = runCatching { Json.parseToJsonElement(text) as JsonArray }.getOrNull() ?: return
        if (frame.size < 5) return
        val topic = (frame[2] as? JsonPrimitive)?.content
        val messageRef = (frame[1] as? JsonPrimitive)?.content
        val event = (frame[3] as? JsonPrimitive)?.content ?: return
        val payload = frame[4]
        if (isSuccessfulFeedPresenceJoinReply(event, topic, messageRef, activeJoinRef, payload)) {
            send(null, nextRef(), FeedPresenceTopic, "presence", buildJsonObject {
                put("event", "track")
                put("payload", buildJsonObject { put("profile_id", profileId); put("online_at", js("Date.now()") as Double) })
            })
            reconnectAttempt = 0
        } else if (topic == FeedPresenceTopic && (event == "presence_state" || event == "presence_diff")) {
            snapshot = snapshot.reduce(event, payload); publish()
        }
    }

    private fun buildJoinPayload(accessToken: String, profileId: String) = buildJsonObject {
        put("access_token", accessToken)
        put("config", buildJsonObject {
            put("broadcast", buildJsonObject { put("ack", false); put("self", false) })
            put("presence", buildJsonObject { put("key", profileId); put("enabled", true) })
            put("postgres_changes", JsonArray(emptyList()))
            put("private", false)
        })
    }

    private fun startHeartbeat(ws: dynamic) {
        stopHeartbeat()
        heartbeatTimer = setInterval({
            if (socket === ws && !closed) send(null, nextRef(), "phoenix", "heartbeat", JsonObject(emptyMap()))
        }, FeedPresenceHeartbeatMillis.toInt())
    }

    private fun stopHeartbeat() { heartbeatTimer?.let(::clearInterval); heartbeatTimer = null }
    private fun disconnect(reason: String) {
        reconnectTimer?.let(::clearTimeout); reconnectTimer = null
        stopHeartbeat()
        activeJoinRef = null
        val current = socket
        socket = null
        current?.close(1000, reason)
        online.value = emptySet()
    }
    private fun publish() { online.value = snapshot.visibleOnlineProfileIds }
    private fun nextRef(): String = (++ref).toString()
    private fun send(joinRef: String?, frameRef: String, topic: String, event: String, payload: JsonObject) {
        val frame = buildJsonArray { add(joinRef?.let(::JsonPrimitive) ?: JsonNull); add(JsonPrimitive(frameRef)); add(JsonPrimitive(topic)); add(JsonPrimitive(event)); add(payload) }
        socket?.send(Json.encodeToString(JsonArray.serializer(), frame))
    }
    private fun scheduleReconnect() {
        if (closed || !shouldConnectFeedPresence(foreground, networkAvailable, authRepository.activeProfileSessionOrNull() != null) || reconnectTimer != null) return
        val delay = feedPresenceReconnectDelayMillis(reconnectAttempt).toInt()
        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(6)
        reconnectTimer = setTimeout({ reconnectTimer = null; reconcile() }, delay)
    }
}

private fun observeWebPresenceLifecycle(onForeground: (Boolean) -> Unit, onNetwork: (Boolean) -> Unit): () -> Unit = js("""
(() => {
  const visible = () => onForeground(!globalThis.document.hidden);
  const online = () => onNetwork(true);
  const offline = () => onNetwork(false);
  globalThis.document.addEventListener('visibilitychange', visible);
  globalThis.addEventListener('online', online);
  globalThis.addEventListener('offline', offline);
  return () => { globalThis.document.removeEventListener('visibilitychange', visible); globalThis.removeEventListener('online', online); globalThis.removeEventListener('offline', offline); };
})()
""")
