package com.quata.feature.chat.data

import com.quata.core.session.IosRenewableAuthSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionWebSocketMessage
import platform.Foundation.NSURLSessionWebSocketTask

/** iOS NSURLSession Phoenix socket used by the portable chat repository. */
class IosChatRealtimeGateway(
    private val configuration: IosChatRuntimeConfiguration,
    private val authSession: IosRenewableAuthSession,
) : ChatRealtimeGateway {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _online = MutableStateFlow(false)
    override val isOnline = _online
    private val _typing = MutableStateFlow<Set<String>>(emptySet())
    override val typingProfileIds = _typing
    private val _changes = MutableSharedFlow<ChatRealtimeChange>(extraBufferCapacity = 32)
    override val changes: Flow<ChatRealtimeChange> = _changes
    private var foreground = true
    private var network = true
    private var visible: String? = null
    private var task: NSURLSessionWebSocketTask? = null
    private var joinRef: String? = null
    private var ref = 0
    private var retry = 0
    private var reconnecting = false
    private var closed = false
    override fun setForeground(isForeground: Boolean) { foreground = isForeground; reconcile() }
    override fun setNetworkAvailable(isAvailable: Boolean) { network = isAvailable; reconcile() }
    override fun setVisibleConversation(conversationId: String?) { visible = conversationId }
    override fun setTyping(conversationId: String, isTyping: Boolean) { if (_online.value && conversationId == visible) send(nextRef(), "presence", buildJsonObject { put("event", if (isTyping) "track" else "untrack"); put("payload", buildJsonObject { put("typing", isTyping); put("conversation_id", conversationId) }) }) }
    override fun close() { closed = true; disconnect() }
    private fun reconcile() { if (closed || !foreground || !network || authSession.restoredSession() == null) disconnect() else if (task == null) scope.launch { connect() } }
    private suspend fun connect() {
        val session = authSession.currentSession() ?: return
        val url = configuration.supabaseUrl.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/realtime/v1/websocket?apikey=${configuration.supabasePublishableKey}&vsn=2.0.0"
        val current = NSURLSession.sharedSession.webSocketTaskWithURL(NSURL(string = url) ?: return)
        task = current; current.resume(); val reference = nextRef(); joinRef = reference
        send(reference, "phx_join", joinPayload(session.bearerToken, session.userId), current)
        heartbeat(current); receive(current, session.userId)
    }
    private fun receive(current: NSURLSessionWebSocketTask, selfId: String) { current.receiveMessageWithCompletionHandler { message, error -> if (current !== task) return@receiveMessageWithCompletionHandler; if (error != null || message == null) { task = null; _online.value = false; scheduleReconnect(); return@receiveMessageWithCompletionHandler }; message.string?.let { consume(it, selfId) }; receive(current, selfId) } }
    private fun consume(text: String, selfId: String) { val frame = runCatching { Json.parseToJsonElement(text) as JsonArray }.getOrNull() ?: return; if (frame.size < 5) return; val event = (frame[3] as? JsonPrimitive)?.content.orEmpty(); val payload = frame[4].jsonObject; if (event == "phx_reply" && frame[1].jsonPrimitive.content == joinRef && payload["status"]?.jsonPrimitive?.content == "ok") { _online.value = true; retry = 0 } else if (event == "postgres_changes") { val data = payload["data"]?.jsonObject ?: return; val record = data["record"]?.jsonObject ?: data["old_record"]?.jsonObject; data["table"]?.jsonPrimitive?.content?.let { _changes.tryEmit(ChatRealtimeChange(it, record?.get("thread_id")?.jsonPrimitive?.longOrNull)) } } else if (event == "presence_state" || event == "presence_diff") _typing.value = payload.keys.filter { it != selfId }.toSet() }
    private fun heartbeat(current: NSURLSessionWebSocketTask) { scope.launch { while (!closed && current === task) { delay(25_000); if (current === task) send(nextRef(), "heartbeat", JsonObject(emptyMap()), current, "phoenix") } } }
    private fun joinPayload(token: String, profileId: String) = buildJsonObject { put("access_token", token); put("config", buildJsonObject { put("broadcast", buildJsonObject { put("ack", false); put("self", false) }); put("presence", buildJsonObject { put("key", profileId); put("enabled", true) }); put("postgres_changes", buildJsonArray { Tables.forEachIndexed { index, table -> add(buildJsonObject { put("event", "*"); put("schema", "public"); put("table", table); put("filter", ""); put("id", index + 1) }) } }) }) }
    private fun send(reference: String, event: String, payload: JsonObject, current: NSURLSessionWebSocketTask? = task, topic: String = Topic) { val socket = current ?: return; val frame = buildJsonArray { add(joinRef?.let(::JsonPrimitive) ?: JsonNull); add(JsonPrimitive(reference)); add(JsonPrimitive(topic)); add(JsonPrimitive(event)); add(payload) }; socket.sendMessage(NSURLSessionWebSocketMessage(Json.encodeToString(JsonArray.serializer(), frame))) { if (it != null && socket === task) { task = null; _online.value = false; scheduleReconnect() } } }
    private fun scheduleReconnect() { if (closed || !foreground || !network || reconnecting) return; reconnecting = true; scope.launch { delay((1_000L shl retry.coerceAtMost(5)).coerceAtMost(30_000L)); retry = (retry + 1).coerceAtMost(6); reconnecting = false; reconcile() } }
    private fun disconnect() { task?.cancelWithCloseCode(1000L, null); task = null; joinRef = null; _online.value = false; _typing.value = emptySet() }
    private fun nextRef() = (++ref).toString()
    private companion object { const val Topic = "realtime:public:chat"; val Tables = listOf("chat_threads", "chat_participants", "chat_messages", "chat_attachments", "chat_message_favorites", "chat_message_reads", "chat_message_states") }
}
