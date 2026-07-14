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
    val following_count: Int? = null,
    val is_admin: Boolean? = null,
    val is_official: Boolean? = null
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
    val author_id: String? = null,
    val content: String? = null
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
    val id: String? = null,
    val profile_id: String? = null,
    val contact_profile_id: String? = null,
    val position: Int? = null,
    val created_at: String? = null
)

@Serializable
data class CommunityEmergencyContactCreate(
    val profile_id: String,
    val contact_profile_id: String,
    val position: Int
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
data class SupabaseAuthBridgeRequest(
    val country_code: String,
    val phone: String,
    val password: String
)

@Serializable
data class SupabaseAuthBridgeResponse(
    val profile: SupabaseAuthBridgeProfile,
    val session: SupabaseAuthSession,
    val user: SupabaseAuthUser
)

@Serializable
data class SupabaseAuthBridgeProfile(
    val id: String,
    val auth_user_id: String? = null,
    val display_name: String? = null,
    val phone_local: String? = null,
    val country_code: String? = null,
    val avatar_url: String? = null,
    val neighborhood: String? = null
)

@Serializable
data class SupabaseAuthSession(
    val access_token: String,
    val refresh_token: String,
    val expires_at: Long? = null,
    val expires_in: Long? = null,
    val token_type: String? = null
)

@Serializable
data class SupabaseAuthUser(
    val id: String,
    val email: String? = null
)

@Serializable
data class SupabaseRefreshTokenRequest(
    val refresh_token: String
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
data class QuataChatInboxRequest(val p_actor_profile_id: String, val p_limit: Int = 100)

@Serializable
data class QuataRegisterPushTokenRequest(
    val p_profile_id: String,
    val p_token: String,
    val p_platform: String = "android"
)

@Serializable
data class QuataUnregisterPushTokenRequest(
    val p_profile_id: String,
    val p_token: String
)

@Serializable
data class QuataChatThreadRequest(
    val p_actor_profile_id: String,
    val p_thread_id: Long,
    val p_known_message_ids: List<Long> = emptyList(),
    val p_limit: Int = 250
)

@Serializable
data class QuataChatPrivateThreadRequest(val p_actor_profile_id: String, val p_peer_profile_id: String)

@Serializable
data class QuataChatConversationCandidatesRequest(
    val p_actor_profile_id: String,
    val p_query: String = "",
    val p_limit: Int = 30,
    val p_offset: Int = 0
)

@Serializable
data class QuataChatStartThreadRequest(
    val p_actor_profile_id: String,
    val p_recipient_profile_ids: List<String> = emptyList(),
    val p_subject: String? = null,
    val p_type: String = "group",
    val p_message: String = "",
    val p_unique_key: String? = null,
    val p_community_id: String? = null
)

@Serializable
data class QuataChatOpenCommunityThreadRequest(
    val p_actor_profile_id: String,
    val p_community_id: String,
    val p_title: String
)

@Serializable
data class QuataChatRegisterAttachmentRequest(
    val p_actor_profile_id: String,
    val p_thread_id: Long,
    val p_file_url: String,
    val p_storage_bucket: String = "chat-attachments",
    val p_storage_path: String? = null,
    val p_mime_type: String = "application/octet-stream",
    val p_name: String? = null,
    val p_size_bytes: Long? = null,
    val p_ext: String? = null,
    val p_thumb: JsonElement? = null
)

@Serializable
data class QuataChatSendMessageRequest(
    val p_actor_profile_id: String,
    val p_thread_id: Long,
    val p_message: String = "",
    val p_file_ids: List<Long> = emptyList(),
    val p_reply_to_message_id: Long? = null,
    val p_client_message_id: String? = null
)

@Serializable
data class QuataChatListAttachmentsRequest(
    val p_actor_profile_id: String,
    val p_thread_id: Long,
    val p_page: Int = 1,
    val p_per_page: Int = 50,
    val p_type: String? = null
)

@Serializable
data class QuataChatSharedAttachmentsRequest(
    val p_actor_profile_id: String,
    val p_peer_profile_id: String? = null,
    val p_thread_id: Long? = null,
    val p_community_id: String? = null,
    val p_limit: Int = 100,
    val p_offset: Int = 0,
    val p_kind: String? = null
)

@Serializable
data class QuataChatFavoriteRequest(
    val p_actor_profile_id: String,
    val p_thread_id: Long,
    val p_message_id: Long,
    val p_favorite: Boolean
)

@Serializable
data class QuataChatFavoritesRequest(val p_actor_profile_id: String, val p_limit: Int = 250)

@Serializable
data class QuataChatEditMessageRequest(val p_actor_profile_id: String, val p_thread_id: Long, val p_message_id: Long, val p_message: String)

@Serializable
data class QuataChatDeleteMessagesRequest(val p_actor_profile_id: String, val p_thread_id: Long, val p_message_ids: List<Long>)

@Serializable
data class QuataChatForwardMessageRequest(
    val p_actor_profile_id: String,
    val p_message_id: Long,
    val p_thread_ids: List<Long>
)

@Serializable
data class QuataChatThreadActionRequest(val p_actor_profile_id: String, val p_thread_id: Long)

@Serializable
data class QuataChatMessagesStateRequest(
    val p_actor_profile_id: String,
    val p_message_ids: List<Long>,
    val p_status: String,
    val p_source: String = "client"
)

@Serializable
data class QuataChatMutedRequest(val p_actor_profile_id: String, val p_thread_id: Long, val p_muted: Boolean)

@Serializable
data class QuataChatInvitesRequest(val p_actor_profile_id: String, val p_thread_id: Long, val p_enabled: Boolean)

@Serializable
data class QuataChatParticipantsRequest(
    val p_actor_profile_id: String,
    val p_thread_id: Long,
    val p_participant_profile_ids: List<String>
)

@Serializable
data class QuataChatParticipantRequest(
    val p_actor_profile_id: String,
    val p_thread_id: Long,
    val p_profile_id: String
)

@Serializable
data class QuataChatSosRequest(
    val p_actor_profile_id: String,
    val p_contact_profile_ids: List<String> = emptyList(),
    val p_message: String = "",
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

@Serializable
data class OfficialPost(
    val id: String,
    val profile_id: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val post_type: String? = null,
    val content_html: String? = null,
    val read_more_label: String? = null,
    val language: String? = null,
    val translation_group_id: String? = null,
    val media_url: String? = null,
    val media_type: String? = null,
    val link_url: String? = null,
    val is_live: Boolean? = null,
    val is_published: Boolean? = null,
    val published_at: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
    val deleted_at: String? = null
)

@Serializable
data class OfficialPostCreate(
    val profile_id: String,
    val title: String? = null,
    val summary: String? = null,
    val post_type: String = "announcement",
    val content_html: String,
    val read_more_label: String? = null,
    val language: String = "es",
    val translation_group_id: String? = null,
    val media_url: String? = null,
    val media_type: String? = null,
    val link_url: String? = null,
    val is_live: Boolean = false
)

@Serializable
data class OfficialPostUpdate(
    val deleted_at: String? = null
)

@Serializable
data class OfficialPostLike(
    val id: String,
    val official_post_id: String? = null,
    val profile_id: String? = null,
    val created_at: String? = null
)

@Serializable
data class OfficialPostLikeCreate(
    val official_post_id: String,
    val profile_id: String
)

@Serializable
data class OfficialPostComment(
    val id: String,
    val official_post_id: String? = null,
    val profile_id: String? = null,
    val body: String? = null,
    val created_at: String? = null
)

@Serializable
data class OfficialPostCommentCreate(
    val official_post_id: String,
    val profile_id: String,
    val body: String
)
