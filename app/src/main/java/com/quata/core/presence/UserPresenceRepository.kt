package com.quata.core.presence

import com.quata.core.config.AppConfig
import com.quata.core.session.SessionManager
import com.quata.data.supabase.RealtimeRawEvent
import com.quata.data.supabase.RealtimeStatus
import com.quata.data.supabase.SupabaseRealtimeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Ephemeral, device-scoped presence. No online state is persisted locally. */
interface UserPresenceRepository {
    val onlineProfileIds: StateFlow<Set<String>>
    val isConnected: StateFlow<Boolean>

    fun setAppForeground(isForeground: Boolean)
    fun setDeviceNetworkAvailable(isAvailable: Boolean)
    fun observeProfiles(profileIds: Collection<String>)
}

class UserPresenceRepositoryImpl(
    private val realtimeClient: SupabaseRealtimeClient,
    private val sessionManager: SessionManager
) : UserPresenceRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _onlineProfileIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isConnected = MutableStateFlow(false)
    private val observedProfileIds = MutableStateFlow<Set<String>>(emptySet())
    private var allOnlineProfileIds: Set<String> = emptySet()
    private var appForeground = false
    private var networkAvailable = true
    private var connectedProfileId: String? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0

    override val onlineProfileIds: StateFlow<Set<String>> = _onlineProfileIds.asStateFlow()
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    override fun setAppForeground(isForeground: Boolean) {
        if (appForeground == isForeground) return
        appForeground = isForeground
        if (isForeground) connectIfPossible() else disconnect()
    }

    override fun setDeviceNetworkAvailable(isAvailable: Boolean) {
        if (networkAvailable == isAvailable) return
        networkAvailable = isAvailable
        if (isAvailable) connectIfPossible() else disconnect()
    }

    override fun observeProfiles(profileIds: Collection<String>) {
        val normalized = profileIds.map(String::trim).filter { it.isNotBlank() }.toSet()
        if (normalized.isEmpty()) return
        observedProfileIds.value = observedProfileIds.value + normalized
        publishVisibleOnlineProfiles()
    }

    private fun connectIfPossible() {
        if (AppConfig.USE_MOCK_BACKEND || !appForeground || !networkAvailable) return
        val session = sessionManager.currentSession()?.takeIf { it.isSupabaseAuthenticated() } ?: run {
            disconnect()
            return
        }
        if (connectedProfileId == session.userId && _isConnected.value) return
        reconnectJob?.cancel()
        connectedProfileId = session.userId
        realtimeClient.connectPresence(
            accessToken = session.bearerToken,
            presenceKey = session.userId,
            topic = PRESENCE_TOPIC,
            presencePayload = buildJsonObject {
                put("profile_id", session.userId)
                put("online_at", System.currentTimeMillis())
            },
            onEvent = ::onRealtimeEvent,
            onStatus = { status ->
                when (status) {
                    RealtimeStatus.Subscribed -> {
                        reconnectAttempt = 0
                        _isConnected.value = true
                    }
                    RealtimeStatus.Closed, RealtimeStatus.Error -> {
                        _isConnected.value = false
                        scheduleReconnect()
                    }
                    else -> Unit
                }
            },
            onFailure = {
                _isConnected.value = false
                scheduleReconnect()
            }
        )
    }

    private fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
        connectedProfileId = null
        _isConnected.value = false
        _onlineProfileIds.value = emptySet()
        allOnlineProfileIds = emptySet()
        realtimeClient.disconnect()
    }

    private fun scheduleReconnect() {
        if (!appForeground || !networkAvailable || reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay((1_500L shl reconnectAttempt.coerceAtMost(5)).coerceAtMost(30_000L))
            reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(6)
            connectedProfileId = null
            connectIfPossible()
        }
    }

    private fun onRealtimeEvent(event: RealtimeRawEvent) {
        val payload = event.payload as? JsonObject ?: return
        when (event.event) {
            "presence_state" -> {
                allOnlineProfileIds = payload.extractPresenceProfileIds()
                publishVisibleOnlineProfiles()
            }
            "presence_diff" -> {
                val joined = payload["joins"].extractPresenceProfileIds()
                val left = payload["leaves"].extractPresenceProfileIds()
                allOnlineProfileIds = (allOnlineProfileIds + joined) - left
                publishVisibleOnlineProfiles()
            }
        }
    }

    private fun publishVisibleOnlineProfiles() {
        // Presence can contain the full network. Keep only profiles the app has actually rendered
        // from the current feed page or its cached conversation list.
        val observed = observedProfileIds.value
        _onlineProfileIds.value = if (observed.isEmpty()) emptySet() else allOnlineProfileIds.intersect(observed)
    }

    private fun JsonElement?.extractPresenceProfileIds(): Set<String> {
        val result = linkedSetOf<String>()
        fun visit(element: JsonElement?, mapKey: String? = null) {
            when (element) {
                is JsonObject -> {
                    element["profile_id"]?.jsonPrimitive?.contentOrNull?.let(result::add)
                    element["user_id"]?.jsonPrimitive?.contentOrNull?.let(result::add)
                    if (mapKey != null && mapKey.length == 36 && mapKey.count { it == '-' } == 4) result += mapKey
                    element.forEach { (key, value) -> visit(value, key) }
                }
                is kotlinx.serialization.json.JsonArray -> element.forEach { visit(it, mapKey) }
                else -> Unit
            }
        }
        visit(this)
        return result.filter { candidate -> candidate.length == 36 && candidate.count { it == '-' } == 4 }.toSet()
    }

    private fun String.looksLikeProfileId(): Boolean =
        length == 36 && count { it == '-' } == 4

    private companion object {
        const val PRESENCE_TOPIC = "realtime:quata-presence"
    }
}
