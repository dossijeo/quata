package com.quata.feature.chat.data

import com.quata.core.model.AuthSession
import com.quata.core.session.IosRenewableAuthSession
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFAbsoluteTimeGetCurrent
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionWebSocketMessage
import platform.Foundation.NSURLSessionWebSocketTask
import platform.SystemConfiguration.SCNetworkReachabilityCreateWithName
import platform.SystemConfiguration.SCNetworkReachabilityGetFlags
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsConnectionAutomatic
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsConnectionOnDemand
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsConnectionOnTraffic
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsConnectionRequired
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsInterventionRequired
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsReachable

/** Native iOS Phoenix transport for the same Chat tables and typing broadcasts used by Android. */
@OptIn(ExperimentalForeignApi::class)
class IosChatRealtimeGateway(
    private val configuration: IosChatRuntimeConfiguration,
    private val authSession: IosRenewableAuthSession,
) : ChatRealtimeGateway {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val online = MutableStateFlow(false)
    private val typing = MutableStateFlow<Set<String>>(emptySet())
    private val changeEvents = MutableSharedFlow<ChatRealtimeChange>(extraBufferCapacity = 64)
    override val isOnline: StateFlow<Boolean> = online.asStateFlow()
    override val typingProfileIds: StateFlow<Set<String>> = typing.asStateFlow()
    override val changes: Flow<ChatRealtimeChange> = changeEvents.asSharedFlow()

    private val reachabilityHost = configuration.supabaseUrl.reachabilityHost()
    private var foreground = false
    private var networkAvailable = reachabilityHost?.let(::iosChatNetworkAvailable) ?: false
    private var closed = false
    private var visibleConversationId: String? = null
    private var databaseAttempt = 0
    private var typingAttempt = 0
    private var databaseReconnect: Job? = null
    private var typingReconnect: Job? = null
    private var reachabilityPolling: Job? = null
    private var databaseChannel: IosChatPhoenixChannel? = null
    private var typingChannel: IosChatPhoenixChannel? = null
    private var typingSubscribed = false
    private var localTyping = false
    private var lastTypingActivityAt = 0L
    private var lastTypingBroadcastAt = 0L
    private var typingBroadcastJob: Job? = null
    private var typingIdleJob: Job? = null
    private var typingExpiryJob: Job? = null
    private val remoteTypingAt = linkedMapOf<String, Long>()

    init {
        reachabilityPolling = scope.launch {
            while (!closed) {
                delay(ReachabilityPollMillis)
                reachabilityHost?.let(::iosChatNetworkAvailable)?.let(::setNetworkAvailable)
            }
        }
    }

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
        disconnectTyping()
        reconcileTyping()
    }

    override fun setTyping(conversationId: String, isTyping: Boolean) {
        if (closed || visibleConversationId != conversationId) return
        if (!isTyping) {
            stopLocalTyping(sendStop = true)
            return
        }
        lastTypingActivityAt = iosChatRealtimeNowMillis()
        val wasTyping = localTyping
        localTyping = true
        scheduleTypingIdleTimeout()
        if (!wasTyping) scheduleTypingBroadcast(force = true)
    }

    override fun close() {
        if (closed) return
        closed = true
        foreground = false
        reachabilityPolling?.cancel(); reachabilityPolling = null
        stopLocalTyping(sendStop = true)
        disconnectDatabase()
        disconnectTyping()
        scope.cancel()
    }

    private fun reconcile() {
        if (!shouldConnect()) {
            disconnectDatabase()
            disconnectTyping()
            return
        }
        if (databaseChannel == null) scope.launch { connectDatabase(authSession.currentSession()) }
        reconcileTyping()
    }

    private fun reconcileTyping() {
        val conversationId = visibleConversationId
        if (!shouldConnect() || conversationId == null) {
            disconnectTyping()
        } else if (typingChannel == null) {
            scope.launch { connectTyping(authSession.currentSession(), conversationId) }
        }
    }

    private fun shouldConnect(): Boolean = shouldConnectChatRealtime(
        foreground = foreground,
        networkAvailable = networkAvailable,
        hasAuthenticatedSession = authSession.restoredSession() != null,
        closed = closed,
    )

    private fun connectDatabase(session: AuthSession?) {
        if (session == null || !shouldConnect() || databaseChannel != null) return
        lateinit var channel: IosChatPhoenixChannel
        channel = IosChatPhoenixChannel(
            configuration = configuration,
            session = session,
            topic = ChatRealtimePostgresTopic,
            tables = ChatRealtimeTables,
            onSubscribed = {},
            onReady = { if (databaseChannel === channel) { databaseAttempt = 0; online.value = true } },
            onEvent = { event, payload ->
                if (databaseChannel === channel) parseChatRealtimeChange(event, payload)?.let {
                    online.value = true
                    changeEvents.tryEmit(it)
                }
            },
            onDisconnected = { if (databaseChannel === channel) { databaseChannel = null; online.value = false; scheduleDatabaseReconnect() } },
        )
        databaseChannel = channel
        channel.connect()
    }

    private fun connectTyping(session: AuthSession?, conversationId: String) {
        if (session == null || !shouldConnect() || visibleConversationId != conversationId || typingChannel != null) return
        lateinit var channel: IosChatPhoenixChannel
        channel = IosChatPhoenixChannel(
            configuration = configuration,
            session = session,
            topic = chatTypingTopic(conversationId),
            tables = emptyList(),
            onSubscribed = {
                if (typingChannel === channel) {
                    typingAttempt = 0
                    typingSubscribed = true
                    if (localTyping) scheduleTypingBroadcast(force = true)
                }
            },
            onReady = {},
            onEvent = typingEvent@ { event, payload ->
                if (typingChannel !== channel) return@typingEvent
                val broadcast = parseChatTypingBroadcast(event, payload) ?: return@typingEvent
                if (broadcast.profileId == session.userId) return@typingEvent
                if (broadcast.isTyping) remoteTypingAt[broadcast.profileId] = iosChatRealtimeNowMillis()
                else remoteTypingAt.remove(broadcast.profileId)
                publishRemoteTyping()
            },
            onDisconnected = { if (typingChannel === channel) { typingChannel = null; typingSubscribed = false; clearRemoteTyping(); scheduleTypingReconnect() } },
        )
        typingChannel = channel
        channel.connect()
    }

    private fun scheduleTypingBroadcast(force: Boolean) {
        if (!localTyping) return
        typingBroadcastJob?.cancel()
        val elapsed = iosChatRealtimeNowMillis() - lastTypingBroadcastAt
        val waitMillis = if (force) 0L else (TypingBroadcastIntervalMillis - elapsed).coerceAtLeast(0L)
        typingBroadcastJob = scope.launch {
            delay(waitMillis)
            if (!localTyping) return@launch
            if (iosChatRealtimeNowMillis() - lastTypingActivityAt >= TypingTimeoutMillis) {
                stopLocalTyping(sendStop = true)
                return@launch
            }
            lastTypingBroadcastAt = iosChatRealtimeNowMillis()
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
        val session = authSession.restoredSession() ?: return
        if (!typingSubscribed) return
        typingChannel?.sendBroadcast("typing", buildJsonObject {
            put("profile_id", session.userId)
            put("is_typing", isTyping)
        })
    }

    private fun publishRemoteTyping() {
        val now = iosChatRealtimeNowMillis()
        remoteTypingAt.entries.removeAll { now - it.value >= TypingTimeoutMillis }
        typing.value = remoteTypingAt.keys.toSet()
        typingExpiryJob?.cancel()
        val expiry = remoteTypingAt.values.minOrNull()?.plus(TypingTimeoutMillis) ?: return
        typingExpiryJob = scope.launch { delay((expiry - iosChatRealtimeNowMillis()).coerceAtLeast(1L)); publishRemoteTyping() }
    }

    private fun clearRemoteTyping() {
        typingExpiryJob?.cancel(); typingExpiryJob = null
        remoteTypingAt.clear(); typing.value = emptySet()
    }

    private fun disconnectDatabase() {
        databaseReconnect?.cancel(); databaseReconnect = null
        val channel = databaseChannel; databaseChannel = null
        channel?.close()
        online.value = false
    }

    private fun disconnectTyping() {
        typingReconnect?.cancel(); typingReconnect = null
        typingSubscribed = false
        val channel = typingChannel; typingChannel = null
        channel?.close()
        clearRemoteTyping()
    }

    private fun scheduleDatabaseReconnect() {
        if (!shouldConnect() || databaseReconnect?.isActive == true) return
        databaseReconnect = scope.launch {
            delay(chatRealtimeReconnectDelayMillis(databaseAttempt))
            databaseAttempt = (databaseAttempt + 1).coerceAtMost(6)
            reconcile()
        }
    }

    private fun scheduleTypingReconnect() {
        if (!shouldConnect() || visibleConversationId == null || typingReconnect?.isActive == true) return
        typingReconnect = scope.launch {
            delay(chatRealtimeReconnectDelayMillis(typingAttempt))
            typingAttempt = (typingAttempt + 1).coerceAtMost(6)
            reconcileTyping()
        }
    }

    private companion object {
        const val ReachabilityPollMillis = 5_000L
        const val TypingBroadcastIntervalMillis = 2_000L
        const val TypingTimeoutMillis = 3_000L
    }
}

private class IosChatPhoenixChannel(
    private val configuration: IosChatRuntimeConfiguration,
    private val session: AuthSession,
    private val topic: String,
    private val tables: List<String>,
    private val onSubscribed: () -> Unit,
    private val onReady: () -> Unit,
    private val onEvent: (String, JsonElement) -> Unit,
    private val onDisconnected: () -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var task: NSURLSessionWebSocketTask? = null
    private var heartbeat: Job? = null
    private var joinRef: String? = null
    private var ref = 0

    fun connect() {
        if (task != null) return
        val socketUrl = configuration.supabaseUrl.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") +
            "/realtime/v1/websocket?apikey=${configuration.supabasePublishableKey}&vsn=2.0.0"
        val current = NSURLSession.sharedSession.webSocketTaskWithURL(NSURL(string = socketUrl) ?: return)
        task = current
        current.resume()
        val currentJoinRef = nextRef()
        joinRef = currentJoinRef
        send(current, null, currentJoinRef, "phx_join", buildJoinPayload())
        heartbeat = scope.launch {
            while (current === task) {
                delay(25_000L)
                if (current === task) send(current, null, nextRef(), "heartbeat", JsonObject(emptyMap()), "phoenix")
            }
        }
        receive(current)
    }

    fun sendBroadcast(event: String, payload: JsonObject) {
        val current = task ?: return
        send(current, joinRef, nextRef(), "broadcast", buildJsonObject {
            put("type", "broadcast")
            put("event", event)
            put("payload", payload)
        })
    }

    fun close() {
        val current = task
        task = null
        joinRef = null
        heartbeat?.cancel(); heartbeat = null
        current?.cancelWithCloseCode(1000L, null)
        scope.cancel()
    }

    private fun receive(current: NSURLSessionWebSocketTask) {
        current.receiveMessageWithCompletionHandler { message, error ->
            if (current !== task) return@receiveMessageWithCompletionHandler
            if (error != null || message == null) {
                task = null
                heartbeat?.cancel(); heartbeat = null
                onDisconnected()
                return@receiveMessageWithCompletionHandler
            }
            message.string?.let(::onMessage)
            receive(current)
        }
    }

    private fun onMessage(text: String) {
        val frame = runCatching { Json.parseToJsonElement(text) as JsonArray }.getOrNull() ?: return
        if (frame.size < 5) return
        val messageRef = (frame[1] as? JsonPrimitive)?.contentOrNull
        val frameTopic = (frame[2] as? JsonPrimitive)?.contentOrNull ?: return
        val event = (frame[3] as? JsonPrimitive)?.contentOrNull ?: return
        val payload = frame[4]
        if (frameTopic != topic) return
        if (event == "phx_reply" && messageRef == joinRef) {
            when ((payload as? JsonObject)?.get("status")?.jsonPrimitive?.contentOrNull) {
                "ok" -> onSubscribed()
                "error" -> fail()
            }
        } else if (event == "system" && (payload as? JsonObject)?.get("status")?.jsonPrimitive?.contentOrNull == "ok") {
            onReady()
        } else if (event == "postgres_changes" || event == "broadcast") {
            onEvent(event, payload)
        } else if (event == "phx_error" || event == "phx_close") {
            fail()
        }
    }

    private fun fail() {
        val current = task
        task = null
        heartbeat?.cancel(); heartbeat = null
        current?.cancelWithCloseCode(1011L, null)
        onDisconnected()
    }

    private fun buildJoinPayload() = buildJsonObject {
        put("access_token", session.bearerToken)
        put("config", buildJsonObject {
            put("broadcast", buildJsonObject { put("ack", false); put("self", false) })
            put("presence", buildJsonObject { put("enabled", false) })
            put("postgres_changes", JsonArray(tables.map { table -> buildJsonObject { put("event", "*"); put("schema", "public"); put("table", table) } }))
            put("private", false)
        })
    }

    private fun send(current: NSURLSessionWebSocketTask, joinRef: String?, frameRef: String, event: String, payload: JsonObject, frameTopic: String = topic) {
        val frame = buildJsonArray { add(joinRef?.let(::JsonPrimitive) ?: JsonNull); add(JsonPrimitive(frameRef)); add(JsonPrimitive(frameTopic)); add(JsonPrimitive(event)); add(payload) }
        current.sendMessage(NSURLSessionWebSocketMessage(Json.encodeToString(JsonArray.serializer(), frame))) { error ->
            if (error != null && current === task) fail()
        }
    }

    private fun nextRef(): String = (++ref).toString()
}

private fun iosChatRealtimeNowMillis(): Long = ((CFAbsoluteTimeGetCurrent() + AppleEpochOffsetSeconds) * 1_000.0).toLong()

private const val AppleEpochOffsetSeconds = 978_307_200.0

private fun String.reachabilityHost(): String? =
    substringAfter("://", this).substringBefore('/').substringBefore('?').substringBefore('@').substringBefore(':').trim().takeIf(String::isNotEmpty)

@OptIn(ExperimentalForeignApi::class)
private fun iosChatNetworkAvailable(host: String): Boolean? = memScoped {
    val reachability = SCNetworkReachabilityCreateWithName(null, host) ?: return@memScoped null
    try {
        val flags = alloc<UIntVar>()
        if (!SCNetworkReachabilityGetFlags(reachability, flags.ptr)) return@memScoped null
        val value = flags.value
        val reachable = value and kSCNetworkReachabilityFlagsReachable != 0u
        val connectionRequired = value and kSCNetworkReachabilityFlagsConnectionRequired != 0u
        val automatic = value and (kSCNetworkReachabilityFlagsConnectionOnTraffic or kSCNetworkReachabilityFlagsConnectionOnDemand or kSCNetworkReachabilityFlagsConnectionAutomatic) != 0u
        val intervention = value and kSCNetworkReachabilityFlagsInterventionRequired != 0u
        reachable && (!connectionRequired || (automatic && !intervention))
    } finally {
        CFRelease(reachability)
    }
}
