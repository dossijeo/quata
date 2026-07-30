package com.quata.feature.feed.presentation

import com.quata.core.model.AuthSession
import com.quata.core.session.IosRenewableAuthSession
import com.quata.feature.feed.data.IosFeedRuntimeConfiguration
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionWebSocketMessage
import platform.Foundation.NSURLSessionWebSocketTask
import platform.Network.NWPathMonitor
import platform.Network.NWPathStatusSatisfied
import platform.darwin.dispatch_queue_create

/** Native iOS Realtime transport. It deliberately starts only with a restored authenticated session. */
@OptIn(ExperimentalForeignApi::class)
class IosFeedPresence(
    private val configuration: IosFeedRuntimeConfiguration,
    private val authSession: IosRenewableAuthSession?,
) : FeedUserPresence {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val online = MutableStateFlow<Set<String>>(emptySet())
    override val onlineProfileIds: StateFlow<Set<String>> = online
    private var snapshot = FeedPresenceSnapshot()
    private var foreground = false
    private var networkAvailable = true
    private var closed = false
    private var reconnect: Job? = null
    private var heartbeat: Job? = null
    private var task: NSURLSessionWebSocketTask? = null
    private var attempt = 0
    private var ref = 0
    private var activeJoinRef: String? = null
    private val pathMonitor = NWPathMonitor()
    private val pathQueue = dispatch_queue_create("com.quata.feed.presence.path", null)

    init {
        pathMonitor.pathUpdateHandler = { path -> setNetworkAvailable(path?.status == NWPathStatusSatisfied) }
        pathMonitor.startWithQueue(pathQueue)
    }

    override fun observeProfiles(profileIds: Collection<String>) { snapshot = snapshot.observe(profileIds); publish() }
    override fun setForeground(isForeground: Boolean) { if (!closed) { foreground = isForeground; reconcile() } }
    override fun setNetworkAvailable(isAvailable: Boolean) { if (!closed) { networkAvailable = isAvailable; reconcile() } }
    override fun close() {
        if (closed) return
        closed = true
        foreground = false
        pathMonitor.cancel()
        disconnect()
        scope.cancel()
    }

    private fun reconcile() {
        if (closed) return
        if (!shouldConnectFeedPresence(foreground, networkAvailable, authSession?.restoredSession() != null)) {
            disconnect()
        } else if (task == null) scope.launch { connect(authSession?.currentSession()) }
    }

    private fun connect(session: AuthSession?) {
        if (session?.bearerToken.isNullOrBlank() || closed || !foreground || !networkAvailable) return
        val socketUrl = configuration.supabaseUrl.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") +
            "/realtime/v1/websocket?apikey=${configuration.supabasePublishableKey}&vsn=2.0.0"
        val current = NSURLSession.sharedSession.webSocketTaskWithURL(NSURL(string = socketUrl) ?: return)
        task = current
        current.resume()
        val joinRef = nextRef()
        activeJoinRef = joinRef
        send(current, joinRef, joinRef, "phx_join", buildJsonObject {
            put("access_token", session.bearerToken)
            put("config", buildJsonObject {
                put("broadcast", buildJsonObject { put("ack", false); put("self", false) })
                put("presence", buildJsonObject { put("key", session.userId); put("enabled", true) })
                put("postgres_changes", JsonArray(emptyList()))
                put("private", false)
            })
        })
        startHeartbeat(current)
        receive(current, session.userId)
    }

    private fun receive(current: NSURLSessionWebSocketTask, profileId: String) {
        current.receiveMessageWithCompletionHandler { message, error ->
            if (current !== task) return@receiveMessageWithCompletionHandler
            if (error != null || message == null) { task = null; stopHeartbeat(); activeJoinRef = null; scheduleReconnect(); return@receiveMessageWithCompletionHandler }
            message.string?.let { onMessage(current, it, profileId) }
            receive(current, profileId)
        }
    }

    private fun onMessage(current: NSURLSessionWebSocketTask, text: String, profileId: String) {
        val frame = runCatching { Json.parseToJsonElement(text) as JsonArray }.getOrNull() ?: return
        if (frame.size < 5) return
        val topic = (frame[2] as? JsonPrimitive)?.content
        val messageRef = (frame[1] as? JsonPrimitive)?.content
        val event = (frame[3] as? JsonPrimitive)?.content ?: return
        val payload = frame[4]
        if (isSuccessfulFeedPresenceJoinReply(event, topic, messageRef, activeJoinRef, payload)) {
            send(current, null, nextRef(), "presence", buildJsonObject {
                put("event", "track")
                put("payload", buildJsonObject { put("profile_id", profileId) })
            })
            attempt = 0
        } else if (topic == FeedPresenceTopic && (event == "presence_state" || event == "presence_diff")) {
            snapshot = snapshot.reduce(event, payload); publish()
        }
    }

    private fun send(current: NSURLSessionWebSocketTask, joinRef: String?, frameRef: String, event: String, payload: JsonObject, topic: String = FeedPresenceTopic) {
        val frame = buildJsonArray { add(joinRef?.let(::JsonPrimitive) ?: JsonNull); add(JsonPrimitive(frameRef)); add(JsonPrimitive(topic)); add(JsonPrimitive(event)); add(payload) }
        current.sendMessage(NSURLSessionWebSocketMessage(Json.encodeToString(JsonArray.serializer(), frame))) { error ->
            if (error != null && current === task) { task = null; stopHeartbeat(); activeJoinRef = null; scheduleReconnect() }
        }
    }

    private fun startHeartbeat(current: NSURLSessionWebSocketTask) {
        heartbeat?.cancel()
        heartbeat = scope.launch {
            while (!closed && current === task) {
                delay(FeedPresenceHeartbeatMillis)
                if (!closed && current === task) send(current, null, nextRef(), "heartbeat", JsonObject(emptyMap()), "phoenix")
            }
        }
    }

    private fun stopHeartbeat() { heartbeat?.cancel(); heartbeat = null }
    private fun disconnect() {
        reconnect?.cancel(); reconnect = null
        stopHeartbeat()
        activeJoinRef = null
        task?.cancelWithCloseCode(1000u, null)
        task = null
        online.value = emptySet()
    }

    private fun publish() { online.value = snapshot.visibleOnlineProfileIds }
    private fun nextRef() = (++ref).toString()
    private fun scheduleReconnect() {
        if (closed || !shouldConnectFeedPresence(foreground, networkAvailable, authSession?.restoredSession() != null) || reconnect?.isActive == true) return
        reconnect = scope.launch { delay(feedPresenceReconnectDelayMillis(attempt)); attempt = (attempt + 1).coerceAtMost(6); reconcile() }
    }
}
