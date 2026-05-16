package com.quata.bettermessages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class WpAjaxResponse<T>(
    val success: Boolean,
    val data: T? = null
)

@Serializable
data class BmProfileContextData(
    @SerialName("profile_id") val profileId: String
)

@Serializable
data class BmSyncSessionData(
    @SerialName("user_id") val userId: Int? = null,
    @SerialName("profile_id") val profileId: String? = null,
    val profile: JsonElement? = null
)

@Serializable
data class BmUnreadCountData(
    @SerialName("unread_total") val unreadTotal: Int = 0,
    @SerialName("user_id") val userId: Int? = null
)

@Serializable
data class BmUrlData(
    val url: String,
    val route: String? = null,
    val mode: String? = null,
    @SerialName("thread_id") val threadId: Int? = null,
    @SerialName("user_id") val userId: Int? = null
)

@Serializable
data class BmSendSosData(
    val sent: Int = 0,
    val errors: List<String> = emptyList(),
    val mode: String? = null,
    @SerialName("self_send_blocked") val selfSendBlocked: Boolean? = null,
    @SerialName("only_saved_emergency_contacts") val onlySavedEmergencyContacts: Boolean? = null
)
