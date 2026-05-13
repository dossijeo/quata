package com.quata.core.network.supabase

import com.google.gson.annotations.SerializedName

data class SupabasePostDto(
    val id: String,
    @SerializedName("user_id") val userId: String?,
    val text: String?,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("created_at") val createdAt: String?
)

data class SupabaseCreatePostRequest(
    @SerializedName("user_id") val userId: String,
    val text: String,
    @SerializedName("image_url") val imageUrl: String? = null
)

data class SupabaseConversationDto(
    val id: String,
    val title: String?,
    @SerializedName("last_message_preview") val lastMessagePreview: String?,
    @SerializedName("unread_count") val unreadCount: Int?,
    @SerializedName("updated_at") val updatedAt: String?,
    @SerializedName("participant_names") val participantNames: List<String>? = null,
    @SerializedName("participant_ids") val participantIds: List<String>? = null,
    @SerializedName("community_name") val communityName: String? = null,
    @SerializedName("is_muted") val isMuted: Boolean? = null,
    @SerializedName("is_visible") val isVisible: Boolean? = null
)

data class SupabaseCreateConversationRequest(
    val title: String,
    @SerializedName("participant_ids") val participantIds: List<String>,
    @SerializedName("last_message_preview") val lastMessagePreview: String,
    @SerializedName("community_name") val communityName: String? = null
)

data class SupabaseMessageDto(
    val id: String,
    @SerializedName("conversation_id") val conversationId: String,
    @SerializedName("sender_id") val senderId: String,
    @SerializedName("sender_name") val senderName: String?,
    val text: String,
    @SerializedName("is_read") val isRead: Boolean? = null,
    @SerializedName("created_at") val createdAt: String?
)

data class SupabaseSendMessageRequest(
    @SerializedName("conversation_id") val conversationId: String,
    @SerializedName("sender_id") val senderId: String,
    @SerializedName("sender_name") val senderName: String,
    val text: String,
    @SerializedName("is_read") val isRead: Boolean = false
)

data class SupabaseMessageUpdateRequest(
    @SerializedName("is_read") val isRead: Boolean
)

data class SupabaseConversationUpdateRequest(
    @SerializedName("participant_ids") val participantIds: List<String>? = null,
    @SerializedName("participant_names") val participantNames: List<String>? = null,
    @SerializedName("community_name") val communityName: String? = null,
    @SerializedName("unread_count") val unreadCount: Int? = null,
    @SerializedName("is_muted") val isMuted: Boolean? = null,
    @SerializedName("is_visible") val isVisible: Boolean? = null
)

data class SupabasePushTokenRequest(
    @SerializedName("user_id") val userId: String,
    val token: String,
    val platform: String = "android"
)

data class SupabaseProfileDto(
    val id: String,
    val email: String? = null,
    @SerializedName("display_name") val displayName: String? = null,
    val neighborhood: String? = null,
    @SerializedName("country_code") val countryCode: String? = null,
    val phone: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("secret_question") val secretQuestion: String? = null,
    @SerializedName("emergency_contact_ids") val emergencyContactIds: List<String>? = null,
    @SerializedName("emergency_message") val emergencyMessage: String? = null,
    @SerializedName("emergency_message_is_default") val emergencyMessageIsDefault: Boolean? = null
)

data class SupabaseProfileUpdateRequest(
    @SerializedName("display_name") val displayName: String,
    val neighborhood: String,
    @SerializedName("country_code") val countryCode: String,
    val phone: String,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("secret_question") val secretQuestion: String,
    @SerializedName("secret_answer") val secretAnswer: String?,
    @SerializedName("emergency_contact_ids") val emergencyContactIds: List<String>,
    @SerializedName("emergency_message") val emergencyMessage: String,
    @SerializedName("emergency_message_is_default") val emergencyMessageIsDefault: Boolean
)
