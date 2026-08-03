@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import com.quata.feature.chat.data.ChatRealtimeChange
import com.quata.feature.chat.data.ChatRealtimeGateway
import com.quata.feature.chat.data.ChatRealtimePostgresTopic
import com.quata.feature.chat.data.ChatRealtimeTables
import com.quata.feature.chat.data.chatRealtimeReconnectDelayMillis
import com.quata.feature.chat.data.chatTypingTopic
import com.quata.feature.chat.data.parseChatRealtimeChange
import com.quata.feature.chat.data.parseChatTypingBroadcast
import com.quata.feature.chat.data.shouldConnectChatRealtime
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.toJsString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Browser Phoenix transport matching Android's Postgres and typing channels. */
class WebChatRealtimeGateway(
    private val configuration: WebRuntimeConfiguration,
    private val authRepository: WebAuthRepository,
) : ChatRealtimeGateway {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val online = MutableStateFlow(false)
    private val typing = MutableStateFlow<Set<String>>(emptySet())
    private val changeEvents = MutableSharedFlow<ChatRealtimeChange>(extraBufferCapacity = 64)
    override val isOnline: StateFlow<Boolean> = online.asStateFlow()
    override val typingProfileIds: StateFlow<Set<String>> = typing.asStateFlow()
    override val changes: Flow<ChatRealtimeChange> = changeEvents.asSharedFlow()

    private var foreground = false
    private var networkAvailable = webChatNetworkAvailable()
    private var closed = false
    private var databaseSocket: JsAny? = null
    private var typingSocket: JsAny? = null
    private var databaseReconnectTimer: JsAny? = null
    private var typingReconnectTimer: JsAny? = null
    private var databaseHeartbeatTimer: JsAny? = null
    private var typingHeartbeatTimer: JsAny? = null
    private var databaseJoinRef: String? = null
    private var typingJoinRef: String? = null
    private var typingSubscribed = false
    private var databaseAttempt = 0
    private var typingAttempt = 0
    private var ref = 0
    private var visibleConversationId: String? = null
    private var localTyping = false
    private var lastTypingActivityAt = 0L
    private var lastTypingBroadcastAt = 0L
    private var typingBroadcastJob: Job? = null
    private var typingIdleJob: Job? = null
    private var typingExpiryJob: Job? = null
    private val remoteTypingAt = linkedMapOf<String, Long>()
    private val disposeLifecycle = observeWebChatRealtimeLifecycle(
        onForeground = ::setForeground,
        onNetwork = ::setNetworkAvailable,
    )

    override fun setForeground(isForeground: Boolean) {
        if (closed || foreground == isForeground) return
        foreground = isForeground
        reconcile()
    }

    override fun setNetworkAvailable(isAvailable: Boolean) {
        if (closed || networkAvailable == isAvailable) return
        networkAvailable = isAvailable
        reconcile()
    }

    override fun setVisibleConversation(conversationId: String?) {
        if (closed || visibleConversationId == conversationId) return
        stopLocalTyping(sendStop = true)
        visibleConversationId = conversationId
        disconnectTyping("chat-route-changed")
        reconcileTyping()
    }

    override fun setTyping(conversationId: String, isTyping: Boolean) {
        if (closed || visibleConversationId != conversationId) return
        if (!isTyping) {
            stopLocalTyping(sendStop = true)
            return
        }
        lastTypingActivityAt = webChatRealtimeNowMillis()
        val wasTyping = localTyping
        localTyping = true
        scheduleTypingIdleTimeout()
        if (!wasTyping) scheduleTypingBroadcast(force = true)
    }

    override fun close() {
        if (closed) return
        closed = true
        foreground = false
        disposeLifecycle()
        stopLocalTyping(sendStop = true)
        disconnectDatabase("chat-closed")
        disconnectTyping("chat-closed")
        scope.cancel()
    }

    private fun reconcile() {
        if (!shouldConnect()) {
            disconnectDatabase("chat-paused")
            disconnectTyping("chat-paused")
            return
        }
        if (databaseSocket == null) connectDatabase()
        reconcileTyping()
    }

    private fun reconcileTyping() {
        if (!shouldConnect() || visibleConversationId == null) {
            disconnectTyping("chat-typing-paused")
        } else if (typingSocket == null) connectTyping(visibleConversationId ?: return)
    }

    private fun shouldConnect(): Boolean = shouldConnectChatRealtime(
        foreground = foreground,
        networkAvailable = networkAvailable,
        hasAuthenticatedSession = authRepository.activeProfileSessionOrNull() != null,
        closed = closed,
    )

    private fun connectDatabase() {
        val session = authRepository.activeProfileSessionOrNull() ?: return
        lateinit var socket: JsAny
        socket = createWebChatRealtimeSocket(
            baseUrl = configuration.supabaseUrl?.trim()?.trimEnd('/')?.toJsString() ?: return,
            apiKey = configuration.supabasePublishableKey?.toJsString() ?: return,
            onOpen = {
                if (databaseSocket === socket && !closed) {
                    val joinRef = nextRef()
                    databaseJoinRef = joinRef
                    send(databaseSocket, joinRef, joinRef, ChatRealtimePostgresTopic, "phx_join", postgresJoinPayload(session.accessToken))
                    startDatabaseHeartbeat(socket)
                }
            },
            onMessage = { message -> if (databaseSocket === socket) onDatabaseMessage(message.toString()) },
            onClosed = { if (databaseSocket === socket) { databaseSocket = null; stopDatabaseHeartbeat(); online.value = false; scheduleDatabaseReconnect() } },
        )
        databaseSocket = socket
    }

    private fun connectTyping(conversationId: String) {
        val session = authRepository.activeProfileSessionOrNull() ?: return
        val topic = chatTypingTopic(conversationId)
        lateinit var socket: JsAny
        socket = createWebChatRealtimeSocket(
            baseUrl = configuration.supabaseUrl?.trim()?.trimEnd('/')?.toJsString() ?: return,
            apiKey = configuration.supabasePublishableKey?.toJsString() ?: return,
            onOpen = {
                if (typingSocket === socket && visibleConversationId == conversationId && !closed) {
                    val joinRef = nextRef()
                    typingJoinRef = joinRef
                    send(typingSocket, joinRef, joinRef, topic, "phx_join", typingJoinPayload(session.accessToken))
                    startTypingHeartbeat(socket)
                }
            },
            onMessage = { message -> if (typingSocket === socket) onTypingMessage(message.toString(), session.userId, topic) },
            onClosed = { if (typingSocket === socket) { typingSocket = null; stopTypingHeartbeat(); clearRemoteTyping(); scheduleTypingReconnect() } },
        )
        typingSocket = socket
    }

    private fun onDatabaseMessage(text: String) {
        val frame = parseFrame(text) ?: return
        val (joinRef, messageRef, topic, event, payload) = frame
        if (event == "phx_reply" && topic == ChatRealtimePostgresTopic && messageRef == databaseJoinRef) {
            if (payload.statusOrNull() == "error") {
                disconnectDatabase("chat-join-failed")
                scheduleDatabaseReconnect()
            }
        } else if (event == "system" && payload.statusOrNull() == "ok") {
            databaseAttempt = 0
            online.value = true
        } else {
            parseChatRealtimeChange(event, payload)?.let {
                online.value = true
                changeEvents.tryEmit(it)
            }
        }
    }

    private fun onTypingMessage(text: String, selfProfileId: String, topic: String) {
        val frame = parseFrame(text) ?: return
        val (_, messageRef, frameTopic, event, payload) = frame
        if (frameTopic != topic) return
        if (event == "phx_reply" && messageRef == typingJoinRef && payload.statusOrNull() == "ok") {
            typingAttempt = 0
            typingSubscribed = true
            if (localTyping) scheduleTypingBroadcast(force = true)
            return
        }
        if (event == "phx_reply" && messageRef == typingJoinRef && payload.statusOrNull() == "error") {
            disconnectTyping("chat-typing-join-failed")
            scheduleTypingReconnect()
            return
        }
        val broadcast = parseChatTypingBroadcast(event, payload) ?: return
        if (broadcast.profileId == selfProfileId) return
        if (broadcast.isTyping) remoteTypingAt[broadcast.profileId] = webChatRealtimeNowMillis()
        else remoteTypingAt.remove(broadcast.profileId)
        publishRemoteTyping()
    }

    private fun scheduleTypingBroadcast(force: Boolean) {
        if (!localTyping) return
        typingBroadcastJob?.cancel()
        val elapsed = webChatRealtimeNowMillis() - lastTypingBroadcastAt
        val waitMillis = if (force) 0L else (TypingBroadcastIntervalMillis - elapsed).coerceAtLeast(0L)
        typingBroadcastJob = scope.launch {
            delay(waitMillis)
            if (!localTyping) return@launch
            if (webChatRealtimeNowMillis() - lastTypingActivityAt >= TypingTimeoutMillis) {
                stopLocalTyping(sendStop = true)
                return@launch
            }
            lastTypingBroadcastAt = webChatRealtimeNowMillis()
            sendTyping(true)
            scheduleTypingBroadcast(force = false)
        }
    }

    private fun scheduleTypingIdleTimeout() {
        typingIdleJob?.cancel()
        val activityAt = lastTypingActivityAt
        typingIdleJob = scope.launch {
            delay(TypingTimeoutMillis)
            if (localTyping && lastTypingActivityAt == activityAt) stopLocalTyping(sendStop = true)
        }
    }

    private fun stopLocalTyping(sendStop: Boolean) {
        if (sendStop && localTyping) sendTyping(false)
        localTyping = false
        lastTypingActivityAt = 0L
        typingBroadcastJob?.cancel(); typingBroadcastJob = null
        typingIdleJob?.cancel(); typingIdleJob = null
    }

    private fun sendTyping(isTyping: Boolean) {
        val session = authRepository.activeProfileSessionOrNull() ?: return
        val conversationId = visibleConversationId ?: return
        val joinRef = typingJoinRef ?: return
        if (!typingSubscribed) return
        send(typingSocket, joinRef, nextRef(), chatTypingTopic(conversationId), "broadcast", buildJsonObject {
            put("type", "broadcast")
            put("event", "typing")
            put("payload", buildJsonObject { put("profile_id", session.userId); put("is_typing", isTyping) })
        })
    }

    private fun publishRemoteTyping() {
        val now = webChatRealtimeNowMillis()
        remoteTypingAt.entries.removeAll { now - it.value >= TypingTimeoutMillis }
        typing.value = remoteTypingAt.keys.toSet()
        typingExpiryJob?.cancel()
        val expiry = remoteTypingAt.values.minOrNull()?.plus(TypingTimeoutMillis) ?: return
        typingExpiryJob = scope.launch { delay((expiry - webChatRealtimeNowMillis()).coerceAtLeast(1L)); publishRemoteTyping() }
    }

    private fun clearRemoteTyping() {
        typingExpiryJob?.cancel(); typingExpiryJob = null
        remoteTypingAt.clear(); typing.value = emptySet()
    }

    private fun disconnectDatabase(reason: String) {
        databaseReconnectTimer?.let(::webChatClearTimeout); databaseReconnectTimer = null
        stopDatabaseHeartbeat(); databaseJoinRef = null
        val current = databaseSocket; databaseSocket = null
        current?.let { closeWebChatRealtimeSocket(it, reason.toJsString()) }
        online.value = false
    }

    private fun disconnectTyping(reason: String) {
        typingReconnectTimer?.let(::webChatClearTimeout); typingReconnectTimer = null
        stopTypingHeartbeat(); typingJoinRef = null; typingSubscribed = false
        val current = typingSocket; typingSocket = null
        current?.let { closeWebChatRealtimeSocket(it, reason.toJsString()) }
        clearRemoteTyping()
    }

    private fun scheduleDatabaseReconnect() {
        if (!shouldConnect() || databaseReconnectTimer != null) return
        databaseReconnectTimer = webChatSetTimeout(chatRealtimeReconnectDelayMillis(databaseAttempt).toInt()) {
            databaseReconnectTimer = null; databaseAttempt = (databaseAttempt + 1).coerceAtMost(6); reconcile()
        }
    }

    private fun scheduleTypingReconnect() {
        if (!shouldConnect() || visibleConversationId == null || typingReconnectTimer != null) return
        typingReconnectTimer = webChatSetTimeout(chatRealtimeReconnectDelayMillis(typingAttempt).toInt()) {
            typingReconnectTimer = null; typingAttempt = (typingAttempt + 1).coerceAtMost(6); reconcileTyping()
        }
    }

    private fun startDatabaseHeartbeat(socket: JsAny) {
        stopDatabaseHeartbeat()
        databaseHeartbeatTimer = webChatSetInterval(HeartbeatMillis) { if (databaseSocket === socket) send(databaseSocket, null, nextRef(), "phoenix", "heartbeat", JsonObject(emptyMap())) }
    }

    private fun startTypingHeartbeat(socket: JsAny) {
        stopTypingHeartbeat()
        typingHeartbeatTimer = webChatSetInterval(HeartbeatMillis) { if (typingSocket === socket) send(typingSocket, null, nextRef(), "phoenix", "heartbeat", JsonObject(emptyMap())) }
    }

    private fun stopDatabaseHeartbeat() { databaseHeartbeatTimer?.let(::webChatClearInterval); databaseHeartbeatTimer = null }
    private fun stopTypingHeartbeat() { typingHeartbeatTimer?.let(::webChatClearInterval); typingHeartbeatTimer = null }
    private fun nextRef(): String = (++ref).toString()

    private fun send(socket: JsAny?, joinRef: String?, frameRef: String, topic: String, event: String, payload: JsonObject) {
        val frame = buildJsonArray { add(joinRef?.let(::JsonPrimitive) ?: JsonNull); add(JsonPrimitive(frameRef)); add(JsonPrimitive(topic)); add(JsonPrimitive(event)); add(payload) }
        socket?.let { sendWebChatRealtimeFrame(it, Json.encodeToString(JsonArray.serializer(), frame).toJsString()) }
    }

    private fun postgresJoinPayload(accessToken: String) = buildJsonObject {
        put("access_token", accessToken)
        put("config", buildJsonObject {
            put("broadcast", buildJsonObject { put("ack", false); put("self", false) })
            put("presence", buildJsonObject { put("enabled", false) })
            put("postgres_changes", JsonArray(ChatRealtimeTables.map { table -> buildJsonObject { put("event", "*"); put("schema", "public"); put("table", table) } }))
            put("private", false)
        })
    }

    private fun typingJoinPayload(accessToken: String) = buildJsonObject {
        put("access_token", accessToken)
        put("config", buildJsonObject {
            put("broadcast", buildJsonObject { put("ack", false); put("self", false) })
            put("presence", buildJsonObject { put("enabled", false) })
            put("postgres_changes", JsonArray(emptyList()))
            put("private", false)
        })
    }

    private fun parseFrame(text: String): ChatPhoenixFrame? {
        val frame = runCatching { Json.parseToJsonElement(text) as JsonArray }.getOrNull() ?: return null
        if (frame.size < 5) return null
        return ChatPhoenixFrame(
            joinRef = (frame[0] as? JsonPrimitive)?.contentOrNull,
            messageRef = (frame[1] as? JsonPrimitive)?.contentOrNull,
            topic = (frame[2] as? JsonPrimitive)?.contentOrNull ?: return null,
            event = (frame[3] as? JsonPrimitive)?.contentOrNull ?: return null,
            payload = frame[4],
        )
    }

    private data class ChatPhoenixFrame(val joinRef: String?, val messageRef: String?, val topic: String, val event: String, val payload: kotlinx.serialization.json.JsonElement)
    private fun kotlinx.serialization.json.JsonElement.statusOrNull(): String? = (this as? JsonObject)?.get("status")?.jsonPrimitive?.contentOrNull

    private companion object {
        const val HeartbeatMillis = 25_000
        const val TypingBroadcastIntervalMillis = 2_000L
        const val TypingTimeoutMillis = 3_000L
    }
}

@JsFun("() => globalThis.navigator?.onLine !== false")
private external fun webChatNetworkAvailable(): Boolean

@JsFun("() => Date.now()")
private external fun webChatRealtimeNowMillisAsDouble(): Double

private fun webChatRealtimeNowMillis(): Long = webChatRealtimeNowMillisAsDouble().toLong()

@JsFun("""(baseUrl, apiKey, onOpen, onMessage, onClosed) => {
  const url = baseUrl.replace(/^https:/, 'wss:').replace(/^http:/, 'ws:') + '/realtime/v1/websocket?apikey=' + encodeURIComponent(apiKey) + '&vsn=2.0.0';
  const socket = new WebSocket(url);
  socket.onopen = () => onOpen();
  socket.onmessage = event => onMessage(String(event.data ?? ''));
  socket.onclose = () => onClosed();
  socket.onerror = () => { try { socket.close(); } finally { onClosed(); } };
  return socket;
}""")
private external fun createWebChatRealtimeSocket(baseUrl: JsString, apiKey: JsString, onOpen: () -> Unit, onMessage: (JsString) -> Unit, onClosed: () -> Unit): JsAny

@JsFun("(socket, frame) => socket.send(frame)")
private external fun sendWebChatRealtimeFrame(socket: JsAny, frame: JsString)

@JsFun("(socket, reason) => socket.close(1000, reason)")
private external fun closeWebChatRealtimeSocket(socket: JsAny, reason: JsString)

@JsFun("(delay, callback) => globalThis.setTimeout(callback, delay)")
private external fun webChatSetTimeout(delay: Int, callback: () -> Unit): JsAny

@JsFun("timer => globalThis.clearTimeout(timer)")
private external fun webChatClearTimeout(timer: JsAny)

@JsFun("(delay, callback) => globalThis.setInterval(callback, delay)")
private external fun webChatSetInterval(delay: Int, callback: () -> Unit): JsAny

@JsFun("timer => globalThis.clearInterval(timer)")
private external fun webChatClearInterval(timer: JsAny)

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
private external fun observeWebChatRealtimeLifecycle(onForeground: (Boolean) -> Unit, onNetwork: (Boolean) -> Unit): () -> Unit
