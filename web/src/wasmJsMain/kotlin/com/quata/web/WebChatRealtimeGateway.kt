@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import com.quata.feature.chat.data.ChatRealtimeChange
import com.quata.feature.chat.data.ChatRealtimeGateway
import com.quata.feature.chat.data.ChatTypingPresenceSnapshot
import com.quata.feature.chat.data.chatRealtimeReconnectDelayMillis
import com.quata.feature.chat.data.shouldConnectChatRealtime
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.toJsString
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Phoenix Realtime socket for browser chat: Postgres changes plus per-thread typing Presence. */
class WebChatRealtimeGateway(
    private val configuration: WebRuntimeConfiguration,
    private val authRepository: WebAuthRepository,
) : ChatRealtimeGateway {
    private val _online = MutableStateFlow(false)
    override val isOnline = _online
    private val _typing = MutableStateFlow<Set<String>>(emptySet())
    override val typingProfileIds = _typing
    private val _changes = MutableSharedFlow<ChatRealtimeChange>(extraBufferCapacity = 32)
    override val changes: Flow<ChatRealtimeChange> = _changes
    private var foreground = true
    private var network = true
    private var visibleConversation: String? = null
    private var presenceSnapshot = ChatTypingPresenceSnapshot()
    private var activeTopic = topicFor(null)
    private var socket: JsAny? = null
    private var joinRef: String? = null
    private var heartbeat: JsAny? = null
    private var reconnect: JsAny? = null
    private var attempt = 0
    private var ref = 0
    private var closed = false

    override fun setForeground(isForeground: Boolean) { foreground = isForeground; reconcile() }
    override fun setNetworkAvailable(isAvailable: Boolean) { network = isAvailable; reconcile() }
    override fun setVisibleConversation(conversationId: String?) {
        if (visibleConversation == conversationId) return
        visibleConversation = conversationId
        presenceSnapshot = ChatTypingPresenceSnapshot()
        _typing.value = emptySet()
        if (socket != null) disconnect("chat-conversation-changed")
        reconcile()
    }
    override fun setTyping(conversationId: String, isTyping: Boolean) {
        if (conversationId != visibleConversation || !_online.value) return
        send(nextRef(), "presence", buildJsonObject {
            put("event", if (isTyping) "track" else "untrack")
            put("payload", buildJsonObject { put("typing", isTyping); put("conversation_id", conversationId) })
        })
    }
    override fun close() { closed = true; disconnect("chat-closed") }

    private fun reconcile() {
        if (!shouldConnectChatRealtime(foreground, network, authRepository.activeProfileSessionOrNull() != null, closed)) disconnect("chat-paused")
        else if (socket == null) connect()
    }
    private fun connect() {
        val session = authRepository.activeProfileSessionOrNull() ?: return
        activeTopic = topicFor(visibleConversation)
        val base = configuration.supabaseUrl?.trim()?.trimEnd('/') ?: return
        val key = configuration.supabasePublishableKey ?: return
        lateinit var ws: JsAny
        ws = createWebChatSocket(base.toJsString(), key.toJsString(), {
            if (socket === ws) {
                val ref = nextRef(); joinRef = ref
                send(ref, "phx_join", joinPayload(session.accessToken, session.userId))
                heartbeat = webChatSetInterval(25_000) { if (socket === ws) send(nextRef(), "heartbeat", JsonObject(emptyMap()), "phoenix") }
            }
        }, { message -> if (socket === ws) receive(message.toString(), session.userId) }, {
            if (socket === ws) { socket = null; joinRef = null; _online.value = false; heartbeat?.let(::webChatClearInterval); heartbeat = null; scheduleReconnect() }
        })
        socket = ws
    }
    private fun receive(text: String, selfId: String) {
        val frame = runCatching { Json.parseToJsonElement(text) as JsonArray }.getOrNull() ?: return
        if (frame.size < 5) return
        val topic = (frame[2] as? JsonPrimitive)?.content.orEmpty()
        val event = (frame[3] as? JsonPrimitive)?.content.orEmpty()
        val payload = frame[4].jsonObject
        if (event == "phx_reply" && frame[1].jsonPrimitive.content == joinRef && payload["status"]?.jsonPrimitive?.content == "ok") {
            _online.value = true; attempt = 0
        } else if (event == "postgres_changes") {
            val data = payload["data"]?.jsonObject ?: return
            val table = data["table"]?.jsonPrimitive?.content ?: return
            val record = data["record"]?.jsonObject ?: data["old_record"]?.jsonObject
            _changes.tryEmit(ChatRealtimeChange(table, record?.get("thread_id")?.jsonPrimitive?.longOrNull))
        } else if ((event == "presence_state" || event == "presence_diff") && topic == activeTopic) {
            presenceSnapshot = presenceSnapshot.reduce(event, payload)
            _typing.value = presenceSnapshot.typingProfileIds(visibleConversation, selfId)
        }
    }
    private fun joinPayload(token: String, profileId: String) = buildJsonObject {
        put("access_token", token)
        put("config", buildJsonObject {
            put("broadcast", buildJsonObject { put("ack", false); put("self", false) })
            put("presence", buildJsonObject { put("key", profileId); put("enabled", true) })
            put("postgres_changes", buildJsonArray {
                RealtimeTables.forEachIndexed { index, table -> add(buildJsonObject { put("event", "*"); put("schema", "public"); put("table", table); put("filter", "") ; put("id", index + 1) }) }
            })
        })
    }
    private fun send(ref: String, event: String, payload: JsonObject, topic: String = activeTopic) {
        val frame = buildJsonArray { add(joinRef?.let(::JsonPrimitive) ?: JsonNull); add(JsonPrimitive(ref)); add(JsonPrimitive(topic)); add(JsonPrimitive(event)); add(payload) }
        socket?.let { sendWebChatFrame(it, Json.encodeToString(JsonArray.serializer(), frame).toJsString()) }
    }
    private fun scheduleReconnect() {
        if (!shouldConnectChatRealtime(foreground, network, authRepository.activeProfileSessionOrNull() != null, closed) || reconnect != null) return
        reconnect = webChatSetTimeout(chatRealtimeReconnectDelayMillis(attempt).toInt()) { reconnect = null; attempt = (attempt + 1).coerceAtMost(6); reconcile() }
    }
    private fun disconnect(reason: String) { reconnect?.let(::webChatClearTimeout); reconnect = null; heartbeat?.let(::webChatClearInterval); heartbeat = null; socket?.let { closeWebChatSocket(it, reason.toJsString()) }; socket = null; joinRef = null; _online.value = false; _typing.value = emptySet(); presenceSnapshot = ChatTypingPresenceSnapshot() }
    private fun nextRef() = (++ref).toString()
    private companion object {
        fun topicFor(conversationId: String?) = "realtime:chat:${conversationId ?: "inbox"}"
        val RealtimeTables = listOf("chat_threads", "chat_participants", "chat_messages", "chat_attachments", "chat_message_favorites", "chat_message_reads", "chat_message_states")
    }
}

@JsFun("""(base, key, open, message, closed) => { const url = base.replace(/^https:/,'wss:').replace(/^http:/,'ws:') + '/realtime/v1/websocket?apikey=' + encodeURIComponent(key) + '&vsn=2.0.0'; const ws = new WebSocket(url); ws.onopen=()=>open(); ws.onmessage=e=>message(String(e.data ?? '')); ws.onclose=()=>closed(); ws.onerror=()=>closed(); return ws; }""") private external fun createWebChatSocket(base: JsString, key: JsString, open: () -> Unit, message: (JsString) -> Unit, closed: () -> Unit): JsAny
@JsFun("(socket, frame) => socket.send(frame)") private external fun sendWebChatFrame(socket: JsAny, frame: JsString)
@JsFun("(socket, reason) => socket.close(1000, reason)") private external fun closeWebChatSocket(socket: JsAny, reason: JsString)
@JsFun("(delay, callback) => globalThis.setTimeout(callback, delay)") private external fun webChatSetTimeout(delay: Int, callback: () -> Unit): JsAny
@JsFun("timer => globalThis.clearTimeout(timer)") private external fun webChatClearTimeout(timer: JsAny)
@JsFun("(delay, callback) => globalThis.setInterval(callback, delay)") private external fun webChatSetInterval(delay: Int, callback: () -> Unit): JsAny
@JsFun("timer => globalThis.clearInterval(timer)") private external fun webChatClearInterval(timer: JsAny)
