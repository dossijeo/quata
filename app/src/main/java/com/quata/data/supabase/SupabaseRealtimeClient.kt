package com.quata.data.supabase

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicInteger

class SupabaseRealtimeClient(
    private val config: SupabaseConfig,
    private val okHttp: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false }
) {
    private val ref = AtomicInteger(1)
    private var socket: WebSocket? = null

    fun connect(
        profileId: String? = null,
        tables: List<String> = listOf("community_notifications", "community_messages", "community_private_messages", "community_private_chats"),
        onEvent: (RealtimeRawEvent) -> Unit,
        onFailure: (Throwable) -> Unit = {}
    ) {
        disconnect()
        val request = Request.Builder().url(config.realtimeUrl).build()
        socket = okHttp.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                tables.forEach { table -> joinTable(webSocket, table, profileId) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val parsed = runCatching { json.decodeFromString<RealtimeRawEvent>(text) }.getOrNull()
                if (parsed != null) onEvent(parsed)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onFailure(t)
            }
        })
    }

    fun disconnect() {
        socket?.close(1000, "client-close")
        socket = null
    }

    fun heartbeat() {
        socket?.send(json.encodeToString(RealtimePush("phoenix", "heartbeat", buildJsonObject {}, ref.incrementAndGet().toString())))
    }

    private fun joinTable(webSocket: WebSocket, table: String, profileId: String?) {
        val filter = when {
            table == "community_notifications" && !profileId.isNullOrBlank() -> "recipient_profile_id=eq.$profileId"
            else -> null
        }
        val payload = realtimeJoinPayload(table, profileId ?: "android", filter)
        webSocket.send(json.encodeToString(RealtimePush("realtime:public:$table", "phx_join", payload, ref.incrementAndGet().toString())))
    }

    private fun realtimeJoinPayload(table: String, presenceKey: String, filter: String?): JsonObject = buildJsonObject {
        putJsonObject("config") {
            putJsonObject("broadcast") {
                put("ack", false)
                put("self", false)
            }
            putJsonObject("presence") {
                put("key", presenceKey)
            }
            putJsonArray("postgres_changes") {
                add(buildJsonObject {
                    put("event", "*")
                    put("schema", "public")
                    put("table", table)
                    if (filter != null) put("filter", filter)
                })
            }
            put("private", false)
        }
    }
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
    val payload: kotlinx.serialization.json.JsonElement? = null,
    val ref: String? = null
)
