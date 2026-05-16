@file:Suppress("PropertyName")

package com.quata.data.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class CommunityProfile(
    val id: String,
    val display_name: String? = null,
    val phone: String? = null,
    val pass_hash: String? = null,
    val created_at: String? = null,
    val last_login_at: String? = null,
    val phone_normalized: String? = null,
    val country_code: String? = null,
    val phone_local: String? = null,
    val phone_e164: String? = null,
    val barrio: String? = null,
    val barrio_normalized: String? = null,
    val home_community_id: Long? = null,
    val neighborhood: String? = null,
    val code: String? = null,
    val telefono: String? = null,
    val nombre: String? = null,
    val avatar_url: String? = null,
    val secret_question: String? = null,
    val secret_answer: String? = null,
    val pass_plain: String? = null,
    val avatar: String? = null,
    val followers_count: Int? = null,
    val following_count: Int? = null
)

@Serializable
data class CommunityProfileCreate(
    val display_name: String,
    val phone: String? = null,
    val pass_hash: String,
    val phone_normalized: String? = null,
    val country_code: String? = null,
    val phone_local: String? = null,
    val phone_e164: String? = null,
    val barrio: String? = null,
    val neighborhood: String? = null,
    val code: String? = null,
    val telefono: String? = null,
    val nombre: String? = null,
    val secret_question: String? = null,
    val secret_answer: String? = null,
    val pass_plain: String? = null
)

@Serializable
data class CommunityWall(
    val id: String,
    val slug: String? = null,
    val name: String? = null,
    val city: String? = null,
    val description: String? = null,
    val sort_order: Int? = null,
    val is_active: Boolean? = null,
    val created_at: String? = null,
    val normalized_name: String? = null
)

@Serializable
data class CommunityWallStats(
    val id: String,
    val slug: String? = null,
    val name: String? = null,
    val normalized_name: String? = null,
    val city: String? = null,
    val description: String? = null,
    val sort_order: Int? = null,
    val is_active: Boolean? = null,
    val created_at: String? = null,
    val user_count: Int? = null,
    val post_count: Int? = null,
    val chat_count: Int? = null,
    val chat_last_at: String? = null
)

@Serializable
data class CommunityMember(
    val wall_id: String,
    val profile_id: String,
    val created_at: String? = null
)

@Serializable
data class CommunityPost(
    val id: String,
    val wall_id: String? = null,
    val profile_id: String? = null,
    val body: String? = null,
    val image_url: String? = null,
    val video_url: String? = null,
    val created_at: String? = null,
    val community_id: Long? = null,
    val author_id: String? = null,
    val content: String? = null
)

@Serializable
data class CommunityPostCreate(
    val wall_id: String,
    val profile_id: String,
    val body: String? = null,
    val image_url: String? = null,
    val video_url: String? = null,
    val author_id: String? = profile_id,
    val content: String? = body
)

@Serializable
data class CommunityComment(
    val id: String,
    val post_id: String? = null,
    val profile_id: String? = null,
    val body: String? = null,
    val created_at: String? = null
)

@Serializable
data class CommunityCommentCreate(
    val post_id: String,
    val profile_id: String,
    val body: String
)

@Serializable
data class CommunityPostLike(
    val id: String,
    val post_id: String? = null,
    val profile_id: String? = null,
    val created_at: String? = null
)

@Serializable
data class CommunityPostLikeCreate(
    val post_id: String,
    val profile_id: String
)

@Serializable
data class CommunityPostReaction(
    val id: String,
    val post_id: String? = null,
    val profile_id: String? = null,
    val reaction_type: String? = null,
    val created_at: String? = null
)

@Serializable
data class CommunityPostReactionCreate(
    val post_id: String,
    val profile_id: String,
    val reaction_type: String
)

@Serializable
data class CommunityMessage(
    val id: String,
    val wall_id: String? = null,
    val profile_id: String? = null,
    val body: String? = null,
    val created_at: String? = null
)

@Serializable
data class CommunityMessageCreate(
    val wall_id: String,
    val profile_id: String,
    val body: String
)

@Serializable
data class CommunityNotification(
    val id: String,
    val recipient_profile_id: String? = null,
    val actor_profile_id: String? = null,
    val wall_id: String? = null,
    val post_id: String? = null,
    val comment_id: String? = null,
    val message_id: String? = null,
    val type: String? = null,
    val emoji: String? = null,
    val message: String? = null,
    val is_read: Boolean? = null,
    val created_at: String? = null
)

@Serializable
data class CommunityPrivateChat(
    val id: String,
    val user_low_id: String? = null,
    val user_high_id: String? = null,
    val created_by: String? = null,
    val last_message_at: String? = null,
    val last_message_preview: String? = null,
    val created_at: String? = null,
    val status: String? = null,
    val requested_by: String? = null,
    val responded_at: String? = null,
    val accepted_at: String? = null,
    val rejected_at: String? = null,
    val requester_profile_id: String? = null,
    val target_profile_id: String? = null
)

@Serializable
data class CommunityPrivateMessage(
    val id: String,
    val chat_id: String? = null,
    val sender_profile_id: String? = null,
    val recipient_profile_id: String? = null,
    val body: String? = null,
    val read_at: String? = null,
    val created_at: String? = null,
    val attachment_url: String? = null,
    val attachment_name: String? = null,
    val attachment_type: String? = null
)

@Serializable
data class CommunityPrivateMessageCreate(
    val chat_id: String,
    val sender_profile_id: String,
    val recipient_profile_id: String,
    val body: String? = null,
    val attachment_url: String? = null,
    val attachment_name: String? = null,
    val attachment_type: String? = null
)

@Serializable
data class CommunityWallFollow(
    val id: String,
    val wall_id: String? = null,
    val profile_id: String? = null,
    val created_at: String? = null
)

@Serializable
data class CommunityWallFollowCreate(val wall_id: String, val profile_id: String)

@Serializable
data class CommunityProfileFollow(
    val id: String,
    val follower_profile_id: String? = null,
    val followed_profile_id: String? = null,
    val created_at: String? = null
)

@Serializable
data class CommunityProfileFollowCreate(val follower_profile_id: String, val followed_profile_id: String)

@Serializable
data class CommunityEmergencyContact(
    val id: String,
    val profile_id: String? = null,
    val contact_profile_id: String? = null,
    val position: Int? = null,
    val created_at: String? = null
)

@Serializable
data class CommunityEmergencyContactCreate(
    val profile_id: String,
    val contact_profile_id: String,
    val position: Int = 1
)

@Serializable
data class CommunitySosEvent(
    val id: String,
    val profile_id: String? = null,
    val message: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy_m: Double? = null,
    val sent_count: Int? = null,
    val created_at: String? = null
)

@Serializable
data class EmergencyAlert(
    val id: String,
    val sos_event_id: String? = null,
    val from_profile_id: String? = null,
    val to_profile_id: String? = null,
    val message: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val created_at: String? = null
)

@Serializable
data class ToggleResult(
    val active: Boolean? = null,
    val id: String? = null,
    val count: Int? = null
)

@Serializable
data class LoginResult(
    val profile: CommunityProfile,
    val passwordMatches: Boolean
)

@Serializable
data class RpcCreateOrGetPrivateChatRequest(val user1: String, val user2: String)
@Serializable
data class RpcPrivateChatDecisionRequest(val chat_uuid: String)
@Serializable
data class RpcSendSosRequest(
    val p_profile_id: String,
    val p_message: String,
    val p_lat: Double? = null,
    val p_lng: Double? = null,
    val p_accuracy: Double? = null
)

@Serializable
data class StorageUploadResult(
    @SerialName("Key") val key: String? = null,
    val publicUrl: String? = null,
    val raw: JsonElement? = null
)
