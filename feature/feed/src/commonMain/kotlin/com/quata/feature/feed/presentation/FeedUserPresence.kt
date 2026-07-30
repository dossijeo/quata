package com.quata.feature.feed.presentation

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Ephemeral Feed presence boundary. Implementations connect only while their host is visible and
 * authenticated; a missing source is represented by [onlineProfileIds] being empty, never by a
 * fabricated offline result.
 */
interface FeedUserPresence {
    val onlineProfileIds: StateFlow<Set<String>>

    fun observeProfiles(profileIds: Collection<String>)
    fun setForeground(isForeground: Boolean)
    fun setNetworkAvailable(isAvailable: Boolean)
    fun close()
}

data class FeedPresenceSnapshot(
    val allOnlineProfileIds: Set<String> = emptySet(),
    val observedProfileIds: Set<String> = emptySet(),
) {
    val visibleOnlineProfileIds: Set<String> get() = allOnlineProfileIds.intersect(observedProfileIds)

    fun observe(profileIds: Collection<String>): FeedPresenceSnapshot = copy(
        observedProfileIds = observedProfileIds + profileIds.map(String::trim).filter(::isProfileId),
    )

    fun reduce(event: String, payload: JsonElement?): FeedPresenceSnapshot = when (event) {
        "presence_state" -> copy(allOnlineProfileIds = payload.profileIds())
        "presence_diff" -> copy(
            allOnlineProfileIds = (allOnlineProfileIds + payload.asObject()["joins"].profileIds()) -
                payload.asObject()["leaves"].profileIds(),
        )
        else -> this
    }
}

/** Shared Supabase Presence decoder: state is a map of metas; diffs contain joins/leaves maps. */
internal fun JsonElement?.profileIds(): Set<String> {
    val result = linkedSetOf<String>()
    fun visit(element: JsonElement?, mapKey: String? = null) {
        when (element) {
            is JsonObject -> {
                element["profile_id"]?.jsonPrimitive?.content?.trim()?.takeIf(::isProfileId)?.let(result::add)
                element["user_id"]?.jsonPrimitive?.content?.trim()?.takeIf(::isProfileId)?.let(result::add)
                mapKey?.takeIf(::isProfileId)?.let(result::add)
                element.forEach { (key, value) -> visit(value, key) }
            }
            else -> runCatching { element?.jsonArray }.getOrNull()?.forEach { visit(it, mapKey) }
        }
    }
    visit(this)
    return result
}

private fun JsonElement?.asObject(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())

internal fun isProfileId(value: String): Boolean =
    value.length == 36 && value.count { it == '-' } == 4

const val FeedPresenceTopic = "realtime:quata-presence"
const val FeedPresenceHeartbeatMillis = 25_000L

/** Bounded retry timing shared by native transports; foreground/network gates remain platform-owned. */
fun feedPresenceReconnectDelayMillis(attempt: Int): Long =
    (1_500L shl attempt.coerceIn(0, 5)).coerceAtMost(30_000L)

fun shouldConnectFeedPresence(
    isForeground: Boolean,
    isNetworkAvailable: Boolean,
    isAuthenticated: Boolean,
): Boolean = isForeground && isNetworkAvailable && isAuthenticated

/**
 * The policy applied to native reachability flags. A reachable route that needs a connection is
 * usable only when the system can establish it without asking the user.
 */
internal fun isFeedPresenceNetworkReachable(
    isReachable: Boolean,
    connectionRequired: Boolean,
    canConnectAutomatically: Boolean,
    requiresUserIntervention: Boolean,
): Boolean = isReachable &&
    (!connectionRequired || (canConnectAutomatically && !requiresUserIntervention))

/** A track is legal only for the successful reply to this channel's current join. */
fun isSuccessfulFeedPresenceJoinReply(
    event: String,
    topic: String?,
    ref: String?,
    activeJoinRef: String?,
    payload: JsonElement?,
): Boolean = event == "phx_reply" &&
    topic == FeedPresenceTopic &&
    ref != null && ref == activeJoinRef &&
    (payload as? JsonObject)?.get("status")?.jsonPrimitive?.content == "ok"
