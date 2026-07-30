@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import com.quata.feature.feed.presentation.FeedPresenceHeartbeatMillis
import com.quata.feature.feed.presentation.FeedPresenceSnapshot
import com.quata.feature.feed.presentation.FeedPresenceTopic
import com.quata.feature.feed.presentation.FeedUserPresence
import com.quata.feature.feed.presentation.feedPresenceReconnectDelayMillis
import com.quata.feature.feed.presentation.isSuccessfulFeedPresenceJoinReply
import com.quata.feature.feed.presentation.shouldConnectFeedPresence
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.toJsString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Browser implementation of the same Phoenix Presence protocol used by Android. */
class WebFeedPresence(
    private val configuration: WebRuntimeConfiguration,
    private val authRepository: WebAuthRepository,
) : FeedUserPresence {
    private val online = MutableStateFlow<Set<String>>(emptySet())
    override val onlineProfileIds: StateFlow<Set<String>> = online
    private var snapshot = FeedPresenceSnapshot()
    private var foreground = browserDocumentIsVisible()
    private var networkAvailable = true
    private var closed = false
    private var reconnectAttempt = 0
    private var socket: JsAny? = null
    private var reconnectTimer: JsAny? = null
    private var heartbeatTimer: JsAny? = null
    private var activeJoinRef: String? = null
    private val disposeLifecycle: () -> Unit
    private var ref = 0

    init {
        disposeLifecycle = observeWebPresenceLifecycle({ setForeground(it) }, { setNetworkAvailable(it) })
    }

    override fun observeProfiles(profileIds: Collection<String>) {
        snapshot = snapshot.observe(profileIds)
        publish()
    }

    override fun setForeground(isForeground: Boolean) {
        if (!closed) {
            foreground = isForeground
            reconcile()
        }
    }

    override fun setNetworkAvailable(isAvailable: Boolean) {
        if (!closed) {
            networkAvailable = isAvailable
            reconcile()
        }
    }

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
        } else if (socket == null) {
            connect()
        }
    }

    private fun connect() {
        val session = authRepository.activeProfileSessionOrNull() ?: return
        val baseUrl = configuration.supabaseUrl?.trim()?.trimEnd('/') ?: return
        val apiKey = configuration.supabasePublishableKey ?: return
        lateinit var ws: JsAny
        ws = createWebFeedPresenceSocket(
            baseUrl = baseUrl.toJsString(),
            apiKey = apiKey.toJsString(),
            onOpen = {
                if (socket === ws && !closed) {
                    val joinRef = nextRef()
                    activeJoinRef = joinRef
                    send(joinRef, joinRef, FeedPresenceTopic, "phx_join", buildJoinPayload(session.accessToken, session.userId))
                    startHeartbeat(ws)
                }
            },
            onMessage = { text -> if (socket === ws) onMessage(text.toString(), session.userId) },
            onClosed = {
                if (socket === ws) {
                    socket = null
                    stopHeartbeat()
                    activeJoinRef = null
                    scheduleReconnect()
                }
            },
        )
        socket = ws
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
                put("payload", buildJsonObject {
                    put("profile_id", profileId)
                    put("online_at", browserNowMillis())
                })
            })
            reconnectAttempt = 0
        } else if (topic == FeedPresenceTopic && (event == "presence_state" || event == "presence_diff")) {
            snapshot = snapshot.reduce(event, payload)
            publish()
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

    private fun startHeartbeat(ws: JsAny) {
        stopHeartbeat()
        heartbeatTimer = browserSetInterval(FeedPresenceHeartbeatMillis.toInt()) {
            if (socket === ws && !closed) send(null, nextRef(), "phoenix", "heartbeat", JsonObject(emptyMap()))
        }
    }

    private fun stopHeartbeat() {
        heartbeatTimer?.let(::browserClearInterval)
        heartbeatTimer = null
    }

    private fun disconnect(reason: String) {
        reconnectTimer?.let(::browserClearTimeout)
        reconnectTimer = null
        stopHeartbeat()
        activeJoinRef = null
        val current = socket
        socket = null
        current?.let { closeWebFeedPresenceSocket(it, reason.toJsString()) }
        online.value = emptySet()
    }

    private fun publish() { online.value = snapshot.visibleOnlineProfileIds }
    private fun nextRef(): String = (++ref).toString()

    private fun send(joinRef: String?, frameRef: String, topic: String, event: String, payload: JsonObject) {
        val frame = buildJsonArray {
            add(joinRef?.let(::JsonPrimitive) ?: JsonNull)
            add(JsonPrimitive(frameRef))
            add(JsonPrimitive(topic))
            add(JsonPrimitive(event))
            add(payload)
        }
        socket?.let { sendWebFeedPresenceFrame(it, Json.encodeToString(JsonArray.serializer(), frame).toJsString()) }
    }

    private fun scheduleReconnect() {
        if (closed || !shouldConnectFeedPresence(foreground, networkAvailable, authRepository.activeProfileSessionOrNull() != null) || reconnectTimer != null) return
        val delay = feedPresenceReconnectDelayMillis(reconnectAttempt).toInt()
        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(6)
        reconnectTimer = browserSetTimeout(delay) {
            reconnectTimer = null
            reconcile()
        }
    }
}

@JsFun("() => !globalThis.document.hidden")
private external fun browserDocumentIsVisible(): Boolean

@JsFun("() => Date.now()")
private external fun browserNowMillis(): Double

@JsFun("""(baseUrl, apiKey, onOpen, onMessage, onClosed) => {
  const schemeUrl = baseUrl.replace(/^https:/, 'wss:').replace(/^http:/, 'ws:');
  const socket = new WebSocket(schemeUrl + '/realtime/v1/websocket?apikey=' + encodeURIComponent(apiKey) + '&vsn=2.0.0');
  socket.onopen = () => onOpen();
  socket.onmessage = event => onMessage(String(event.data ?? ''));
  socket.onclose = () => onClosed();
  socket.onerror = () => onClosed();
  return socket;
}""")
private external fun createWebFeedPresenceSocket(
    baseUrl: JsString,
    apiKey: JsString,
    onOpen: () -> Unit,
    onMessage: (JsString) -> Unit,
    onClosed: () -> Unit,
): JsAny

@JsFun("(socket, frame) => socket.send(frame)")
private external fun sendWebFeedPresenceFrame(socket: JsAny, frame: JsString)

@JsFun("(socket, reason) => socket.close(1000, reason)")
private external fun closeWebFeedPresenceSocket(socket: JsAny, reason: JsString)

@JsFun("(delay, callback) => globalThis.setTimeout(callback, delay)")
private external fun browserSetTimeout(delay: Int, callback: () -> Unit): JsAny

@JsFun("timer => globalThis.clearTimeout(timer)")
private external fun browserClearTimeout(timer: JsAny)

@JsFun("(delay, callback) => globalThis.setInterval(callback, delay)")
private external fun browserSetInterval(delay: Int, callback: () -> Unit): JsAny

@JsFun("timer => globalThis.clearInterval(timer)")
private external fun browserClearInterval(timer: JsAny)

@JsFun("""(onForeground, onNetwork) => {
  const visible = () => onForeground(!globalThis.document.hidden);
  const online = () => onNetwork(true);
  const offline = () => onNetwork(false);
  globalThis.document.addEventListener('visibilitychange', visible);
  globalThis.addEventListener('online', online);
  globalThis.addEventListener('offline', offline);
  return () => {
    globalThis.document.removeEventListener('visibilitychange', visible);
    globalThis.removeEventListener('online', online);
    globalThis.removeEventListener('offline', offline);
  };
}""")
private external fun observeWebPresenceLifecycle(
    onForeground: (Boolean) -> Unit,
    onNetwork: (Boolean) -> Unit,
): () -> Unit
