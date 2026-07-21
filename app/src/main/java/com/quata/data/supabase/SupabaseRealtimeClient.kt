package com.quata.data.supabase

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class SupabaseRealtimeClient(
    private val config: SupabaseConfig,
    private val okHttp: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false }
) {
    private val ref = AtomicInteger(1)
    private val intentionallyClosedSockets = ConcurrentHashMap.newKeySet<WebSocket>()
    private var socket: WebSocket? = null
    private var heartbeatTimer: Timer? = null
    private var activeTopic: String? = null
    private var activeJoinRef: String? = null
    private var onChannelJoined: (() -> Unit)? = null

    fun connect(
        accessToken: String,
        presenceKey: String,
        tables: List<String>,
        onEvent: (RealtimeRawEvent) -> Unit,
        onStatus: (RealtimeStatus) -> Unit = {},
        onFailure: (Throwable) -> Unit = {}
    ) {
        connectChannel(
            accessToken = accessToken,
            presenceKey = presenceKey,
            topic = "realtime:quata-chat-$presenceKey",
            tables = tables,
            presenceEnabled = false,
            onEvent = onEvent,
            onStatus = onStatus,
            onFailure = onFailure
        )
    }

    /** Opens a lightweight Presence/Broadcast channel, separate from database replication. */
    fun connectPresence(
        accessToken: String,
        presenceKey: String,
        topic: String,
        presencePayload: JsonObject,
        onEvent: (RealtimeRawEvent) -> Unit,
        onStatus: (RealtimeStatus) -> Unit = {},
        onFailure: (Throwable) -> Unit = {}
    ) {
        connectChannel(
            accessToken = accessToken,
            presenceKey = presenceKey,
            topic = topic,
            tables = emptyList(),
            presenceEnabled = true,
            onEvent = onEvent,
            onStatus = onStatus,
            onFailure = onFailure,
            onJoined = { trackPresence(presencePayload) }
        )
    }

    /** Opens a Broadcast-only channel. */
    fun connectBroadcast(
        accessToken: String,
        presenceKey: String,
        topic: String,
        onEvent: (RealtimeRawEvent) -> Unit,
        onStatus: (RealtimeStatus) -> Unit = {},
        onFailure: (Throwable) -> Unit = {}
    ) {
        connectChannel(
            accessToken = accessToken,
            presenceKey = presenceKey,
            topic = topic,
            tables = emptyList(),
            presenceEnabled = false,
            onEvent = onEvent,
            onStatus = onStatus,
            onFailure = onFailure
        )
    }

    fun sendBroadcast(event: String, payload: JsonObject): Boolean {
        val topic = activeTopic ?: return false
        val currentSocket = socket ?: return false
        return currentSocket.send(
            encodePhoenixFrame(
                joinRef = activeJoinRef,
                ref = ref.incrementAndGet().toString(),
                topic = topic,
                event = "broadcast",
                payload = buildJsonObject {
                    put("type", "broadcast")
                    put("event", event)
                    put("payload", payload)
                }
            )
        )
    }

    private fun connectChannel(
        accessToken: String,
        presenceKey: String,
        topic: String,
        tables: List<String>,
        presenceEnabled: Boolean,
        onEvent: (RealtimeRawEvent) -> Unit,
        onStatus: (RealtimeStatus) -> Unit,
        onFailure: (Throwable) -> Unit,
        onJoined: (() -> Unit)? = null
    ) {
        disconnect()
        activeTopic = topic
        onChannelJoined = onJoined
        val realtimeApiKey = accessToken.takeIf { it.isNotBlank() } ?: config.anonKey
        val request = Request.Builder()
            .url(config.realtimeUrl(realtimeApiKey))
            .header("apikey", config.anonKey)
            .header("Authorization", "Bearer $accessToken")
            .build()
        socket = okHttp.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (socket !== webSocket) return
                Log.d(TAG, "Realtime websocket opened")
                onStatus(RealtimeStatus.Connected)
                startHeartbeat(webSocket)
                joinChannel(webSocket, accessToken, presenceKey, topic, tables, presenceEnabled)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (socket !== webSocket) return
                val parsed = runCatching { parseRealtimeEvent(text) }.getOrNull()
                    ?: run {
                        Log.d(TAG, "Realtime message could not be parsed")
                        return
                    }
                Log.d(
                    TAG,
                    "Realtime message event=${parsed.event.orEmpty()} topic=${parsed.topic.orEmpty()} status=${parsed.payload.statusOrNull().orEmpty()} table=${parsed.table.orEmpty()}"
                )
                when {
                    parsed.event == "phx_reply" &&
                        parsed.topic.orEmpty().startsWith("realtime:") &&
                        parsed.payload.statusOrNull() == "ok" &&
                        parsed.ref == activeJoinRef -> {
                        Log.d(TAG, "Realtime subscribed")
                        onStatus(RealtimeStatus.Subscribed)
                        onChannelJoined?.invoke()
                    }
                    parsed.event == "phx_reply" &&
                        parsed.topic.orEmpty().startsWith("realtime:") &&
                        parsed.payload.statusOrNull() == "error" -> {
                        Log.w(TAG, "Realtime join failed")
                        onStatus(RealtimeStatus.Error)
                    }
                    parsed.event == "system" && parsed.payload.statusOrNull() == "ok" -> {
                        Log.d(TAG, "Realtime postgres ready")
                        onStatus(RealtimeStatus.PostgresReady)
                    }
                    parsed.event == "system" -> {
                        Log.w(TAG, "Realtime system event payload=${parsed.payload}")
                    }
                    parsed.event == "postgres_changes" ||
                        parsed.event == "presence_state" ||
                        parsed.event == "presence_diff" ||
                        parsed.event == "broadcast" -> {
                        Log.d(TAG, "Realtime change table=${parsed.table.orEmpty()}")
                        onEvent(parsed)
                    }
                    parsed.event == "phx_error" -> onStatus(RealtimeStatus.Error)
                    parsed.event == "phx_close" -> onStatus(RealtimeStatus.Closed)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                val wasClientClose = intentionallyClosedSockets.remove(webSocket)
                Log.d(TAG, "Realtime websocket closed code=$code")
                if (socket === webSocket) {
                    stopHeartbeat()
                    socket = null
                }
                if (wasClientClose) return
                onStatus(RealtimeStatus.Closed)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val wasClientClose = intentionallyClosedSockets.remove(webSocket)
                Log.w(TAG, "Realtime websocket failure code=${response?.code}", t)
                if (socket === webSocket) {
                    stopHeartbeat()
                    socket = null
                }
                if (wasClientClose) return
                onStatus(RealtimeStatus.Error)
                onFailure(t)
            }
        })
    }

    fun disconnect() {
        val currentSocket = socket
        stopHeartbeat()
        socket = null
        activeTopic = null
        activeJoinRef = null
        onChannelJoined = null
        if (currentSocket != null) {
            intentionallyClosedSockets.add(currentSocket)
            currentSocket.close(1000, "client-close")
        }
    }

    private fun joinChannel(
        webSocket: WebSocket,
        accessToken: String,
        presenceKey: String,
        topic: String,
        tables: List<String>,
        presenceEnabled: Boolean
    ) {
        val payload = realtimeJoinPayload(
            accessToken = accessToken,
            presenceKey = presenceKey,
            tables = tables.distinct().filter { it.isNotBlank() },
            presenceEnabled = presenceEnabled
        )
        val currentRef = ref.incrementAndGet().toString()
        activeJoinRef = currentRef
        val sent = webSocket.send(encodePhoenixFrame(null, currentRef, topic, "phx_join", payload))
        Log.d(TAG, "Realtime join sent=$sent tables=${tables.size}")
    }

    private fun realtimeJoinPayload(
        accessToken: String,
        presenceKey: String,
        tables: List<String>,
        presenceEnabled: Boolean
    ): JsonObject = buildJsonObject {
        put("access_token", accessToken)
        putJsonObject("config") {
            putJsonObject("broadcast") {
                put("ack", false)
                put("self", false)
            }
            putJsonObject("presence") {
                put("key", presenceKey)
                put("enabled", presenceEnabled)
            }
            putJsonArray("postgres_changes") {
                tables.forEach { table ->
                    add(buildJsonObject {
                        put("event", "*")
                        put("schema", "public")
                        put("table", table)
                    })
                }
            }
            put("private", false)
        }
    }

    private fun trackPresence(payload: JsonObject) {
        val topic = activeTopic ?: return
        val currentSocket = socket ?: return
        currentSocket.send(
            encodePhoenixFrame(
                joinRef = activeJoinRef,
                ref = ref.incrementAndGet().toString(),
                topic = topic,
                event = "presence",
                payload = buildJsonObject {
                    put("event", "track")
                    put("payload", payload)
                }
            )
        )
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        stopHeartbeat()
        heartbeatTimer = Timer("quata-supabase-realtime-heartbeat", true).also { timer ->
            timer.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    webSocket.send(
                        encodePhoenixFrame(
                            joinRef = null,
                            ref = ref.incrementAndGet().toString(),
                            topic = "phoenix",
                            event = "heartbeat",
                            payload = buildJsonObject {}
                        )
                    )
                }
            }, HEARTBEAT_MILLIS, HEARTBEAT_MILLIS)
        }
    }

    private fun encodePhoenixFrame(
        joinRef: String?,
        ref: String?,
        topic: String,
        event: String,
        payload: JsonElement
    ): String = json.encodeToString(
        buildJsonArray {
            add(joinRef?.let(::JsonPrimitive) ?: JsonNull)
            add(ref?.let(::JsonPrimitive) ?: JsonNull)
            add(JsonPrimitive(topic))
            add(JsonPrimitive(event))
            add(payload)
        }
    )

    private fun parseRealtimeEvent(text: String): RealtimeRawEvent? {
        val element = json.parseToJsonElement(text)
        if (element is JsonObject) return json.decodeFromJsonElement(RealtimeRawEvent.serializer(), element)
        val frame = element as? JsonArray ?: return null
        if (frame.size < 5) return null
        return RealtimeRawEvent(
            topic = frame[2].stringOrNull(),
            event = frame[3].stringOrNull(),
            payload = frame[4],
            ref = frame[1].stringOrNull()
        )
    }

    private fun stopHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = null
    }

    private fun JsonElement?.statusOrNull(): String? =
        (this as? JsonObject)
            ?.get("status")
            ?.jsonPrimitive
            ?.contentOrNull

    private fun JsonElement.stringOrNull(): String? =
        (this as? JsonPrimitive)?.contentOrNull

    private companion object {
        const val TAG = "QuataRealtime"
        const val HEARTBEAT_MILLIS = 25_000L
    }
}

enum class RealtimeStatus {
    Connected,
    Subscribed,
    PostgresReady,
    Closed,
    Error
}

@Serializable
data class RealtimePush(
    val topic: String,
    val event: String,
    val payload: JsonElement,
    val ref: String
)

@Serializable
data class RealtimeRawEvent(
    val topic: String? = null,
    val event: String? = null,
    val payload: JsonElement? = null,
    val ref: String? = null
) {
    val table: String?
        get() = payload?.jsonObject?.get("data")?.jsonObject?.get("table")?.jsonPrimitive?.contentOrNull
            ?: payload?.jsonObject?.get("table")?.jsonPrimitive?.contentOrNull

    val record: JsonObject?
        get() = payload?.jsonObject?.get("data")?.jsonObject?.get("record")?.jsonObject
            ?: payload?.jsonObject?.get("record")?.jsonObject
            ?: payload?.jsonObject?.get("new")?.jsonObject

    val oldRecord: JsonObject?
        get() = payload?.jsonObject?.get("data")?.jsonObject?.get("old_record")?.jsonObject
            ?: payload?.jsonObject?.get("old_record")?.jsonObject
            ?: payload?.jsonObject?.get("old")?.jsonObject
}
