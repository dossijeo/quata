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
    @SerializedName("updated_at") val updatedAt: String?
)

data class SupabaseMessageDto(
    val id: String,
    @SerializedName("conversation_id") val conversationId: String,
    @SerializedName("sender_id") val senderId: String,
    @SerializedName("sender_name") val senderName: String?,
    val text: String,
    @SerializedName("created_at") val createdAt: String?
)

data class SupabaseSendMessageRequest(
    @SerializedName("conversation_id") val conversationId: String,
    @SerializedName("sender_id") val senderId: String,
    @SerializedName("sender_name") val senderName: String,
    val text: String
)

data class SupabasePushTokenRequest(
    @SerializedName("user_id") val userId: String,
    val token: String,
    val platform: String = "android"
)
