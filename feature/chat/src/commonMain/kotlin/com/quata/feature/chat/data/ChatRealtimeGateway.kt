package com.quata.feature.chat.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Platform-owned Supabase Realtime connection for chat.  The common repository owns mapping
 * and refreshes; hosts own sockets, lifecycle and the current bearer token.
 */
interface ChatRealtimeGateway {
    val isOnline: StateFlow<Boolean>
    val typingProfileIds: StateFlow<Set<String>>
    val changes: Flow<ChatRealtimeChange>

    fun setForeground(isForeground: Boolean)
    fun setNetworkAvailable(isAvailable: Boolean)
    fun setVisibleConversation(conversationId: String?)
    fun setTyping(conversationId: String, isTyping: Boolean)
    fun close()
}

data class ChatRealtimeChange(
    val table: String,
    val threadId: Long? = null,
)

/** Shared lifecycle rule used by both Phoenix clients and covered without platform sockets. */
fun shouldConnectChatRealtime(
    foreground: Boolean,
    networkAvailable: Boolean,
    hasAuthenticatedSession: Boolean,
    closed: Boolean = false,
): Boolean = !closed && foreground && networkAvailable && hasAuthenticatedSession

/** Deterministic capped backoff. Hosts may add jitter at the platform boundary. */
fun chatRealtimeReconnectDelayMillis(attempt: Int): Long =
    (1_000L shl attempt.coerceIn(0, 5)).coerceAtMost(30_000L)

/** Phoenix Presence state keyed by profile and `phx_ref`, including exact join/leave diffs. */
class ChatTypingPresenceSnapshot private constructor(
    private val metasByProfile: Map<String, Map<String, ChatTypingPresenceMeta>>,
) {
    constructor() : this(emptyMap())
    fun reduce(event: String, payload: JsonElement): ChatTypingPresenceSnapshot = when (event) {
        "presence_state" -> ChatTypingPresenceSnapshot(parsePresenceEntries(payload.jsonObject))
        "presence_diff" -> {
            val root = payload.jsonObject
            val joined = parsePresenceEntries(root["joins"]?.jsonObject ?: JsonObject(emptyMap()))
            val leaving = parsePresenceEntries(root["leaves"]?.jsonObject ?: JsonObject(emptyMap()))
            val next = metasByProfile.mapValues { it.value.toMutableMap() }.toMutableMap()
            joined.forEach { (profile, metas) -> next.getOrPut(profile) { mutableMapOf() }.putAll(metas) }
            leaving.forEach { (profile, metas) ->
                val current = next[profile] ?: return@forEach
                if (metas.isEmpty()) next.remove(profile) else metas.keys.forEach(current::remove)
                if (current.isEmpty()) next.remove(profile)
            }
            ChatTypingPresenceSnapshot(next)
        }
        else -> this
    }

    fun typingProfileIds(conversationId: String?, selfProfileId: String): Set<String> =
        if (conversationId == null) emptySet() else metasByProfile
            .filterKeys { it != selfProfileId }
            .filterValues { metas -> metas.values.any { it.typing && it.conversationId == conversationId } }
            .keys
}

private data class ChatTypingPresenceMeta(val conversationId: String?, val typing: Boolean)

private fun parsePresenceEntries(root: JsonObject): Map<String, Map<String, ChatTypingPresenceMeta>> =
    root.mapValues { (_, value) ->
        value.jsonObject["metas"]?.jsonArray.orEmpty().mapIndexedNotNull { index, element ->
            val meta = element.jsonObject
            val ref = meta["phx_ref"]?.jsonPrimitive?.contentOrNull ?: "unreferenced:$index"
            ref to ChatTypingPresenceMeta(
                conversationId = meta["conversation_id"]?.jsonPrimitive?.contentOrNull,
                typing = meta["typing"]?.jsonPrimitive?.booleanOrNull == true,
            )
        }.toMap()
    }
