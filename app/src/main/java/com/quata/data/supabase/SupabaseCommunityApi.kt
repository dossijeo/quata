package com.quata.data.supabase

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.security.MessageDigest
import java.time.Instant

class SupabaseCommunityApi(private val client: SupabaseHttpClient) {

    suspend fun getActiveWallsStats(limit: Int = 250): List<CommunityWallStats> = client.getList(
        "community_walls_stats",
        mapOf(
            "select" to "*",
            "is_active" to "eq.true",
            "order" to "sort_order.asc,chat_last_at.desc,created_at.desc",
            "limit" to limit.toString()
        )
    )

    suspend fun getWalls(ids: Collection<String>? = null, limit: Int = 500): List<CommunityWall> = client.getList(
        "community_walls",
        mapOf(
            "select" to "*",
            "id" to ids?.takeIf { it.isNotEmpty() }?.joinToString(prefix = "in.(", postfix = ")"),
            "order" to "sort_order.asc,created_at.desc",
            "limit" to limit.toString()
        )
    )

    suspend fun getMembers(profileId: String? = null, wallId: String? = null, limit: Int = 5000): List<CommunityMember> = client.getList(
        "community_members",
        mapOf(
            "select" to "wall_id,profile_id,created_at",
            "profile_id" to profileId?.let { "eq.$it" },
            "wall_id" to wallId?.let { "eq.$it" },
            "limit" to limit.toString()
        )
    )

    suspend fun getProfiles(ids: Collection<String>? = null, limit: Int = 5000): List<CommunityProfile> = client.getList(
        "community_profiles",
        mapOf(
            "select" to "*",
            "id" to ids?.takeIf { it.isNotEmpty() }?.joinToString(prefix = "in.(", postfix = ")"),
            "limit" to limit.toString()
        )
    )

    suspend fun getProfileByPhoneLocal(phoneLocal: String): CommunityProfile? = client.getSingleOrNull(
        "community_profiles",
        mapOf("select" to "*", "phone_local" to "eq.${digitsOnly(phoneLocal)}")
    )

    suspend fun loginByPhoneLocal(phoneLocal: String, passwordPlain: String, updateLastLogin: Boolean = true): LoginResult? {
        val profile = getProfileByPhoneLocal(phoneLocal) ?: return null
        val sha = sha256(passwordPlain)
        val matches = profile.pass_hash.equals(sha, ignoreCase = true) || profile.pass_plain == passwordPlain
        if (matches && updateLastLogin) touchLastLogin(profile.id)
        return LoginResult(profile, matches)
    }

    suspend fun touchLastLogin(profileId: String, atIso: String = Instant.now().toString()): CommunityProfile? =
        client.patch<CommunityProfile, Map<String, String>>("community_profiles", mapOf("id" to "eq.$profileId"), mapOf("last_login_at" to atIso)).firstOrNull()

    suspend fun createProfile(profile: CommunityProfileCreate): CommunityProfile? =
        client.post<CommunityProfile, CommunityProfileCreate>("community_profiles", profile)

    suspend fun updateProfile(profileId: String, patch: Map<String, String?>): CommunityProfile? =
        client.patch<CommunityProfile, Map<String, String?>>("community_profiles", mapOf("id" to "eq.$profileId"), patch).firstOrNull()

    suspend fun getFeedPosts(limit: Int = 15, offset: Int = 0, wallId: String? = null, profileId: String? = null): List<CommunityPost> = client.getList(
        "community_posts",
        mapOf(
            "select" to "id,wall_id,profile_id,body,image_url,video_url,created_at,community_id,author_id,content",
            "wall_id" to wallId?.let { "eq.$it" },
            "profile_id" to profileId?.let { "eq.$it" },
            "order" to "created_at.desc",
            "limit" to limit.toString(),
            "offset" to offset.toString()
        )
    )

    suspend fun createPost(wallId: String, profileId: String, body: String? = null, imageUrl: String? = null, videoUrl: String? = null): CommunityPost? =
        client.post<CommunityPost, CommunityPostCreate>("community_posts", CommunityPostCreate(wallId, profileId, body, imageUrl, videoUrl))

    suspend fun deletePost(postId: String) = client.delete("community_posts", mapOf("id" to "eq.$postId"))

    suspend fun getComments(postIds: Collection<String>): List<CommunityComment> {
        if (postIds.isEmpty()) return emptyList()
        return client.getList("community_comments", mapOf("select" to "*", "post_id" to postIds.joinToString(prefix = "in.(", postfix = ")"), "order" to "created_at.asc"))
    }

    suspend fun addComment(postId: String, profileId: String, body: String): CommunityComment? =
        client.post<CommunityComment, CommunityCommentCreate>("community_comments", CommunityCommentCreate(postId, profileId, body))

    suspend fun deleteComment(commentId: String) = client.delete("community_comments", mapOf("id" to "eq.$commentId"))

    suspend fun getLikes(postIds: Collection<String>): List<CommunityPostLike> {
        if (postIds.isEmpty()) return emptyList()
        return client.getList("community_post_likes", mapOf("select" to "id,post_id,profile_id,created_at", "post_id" to postIds.joinToString(prefix = "in.(", postfix = ")")))
    }

    suspend fun toggleLike(postId: String, profileId: String): ToggleResult {
        val existing = client.getSingleOrNull<CommunityPostLike>("community_post_likes", mapOf("select" to "id", "post_id" to "eq.$postId", "profile_id" to "eq.$profileId"))
        return if (existing != null) {
            client.delete("community_post_likes", mapOf("id" to "eq.${existing.id}"))
            ToggleResult(active = false, id = existing.id)
        } else {
            val created = client.post<CommunityPostLike, CommunityPostLikeCreate>("community_post_likes", CommunityPostLikeCreate(postId, profileId))
            ToggleResult(active = true, id = created?.id)
        }
    }

    suspend fun getReactions(postIds: Collection<String>): List<CommunityPostReaction> {
        if (postIds.isEmpty()) return emptyList()
        return client.getList("community_post_reactions", mapOf("select" to "*", "post_id" to postIds.joinToString(prefix = "in.(", postfix = ")")))
    }

    suspend fun toggleReaction(postId: String, profileId: String, reactionType: String): ToggleResult {
        val existing = client.getSingleOrNull<CommunityPostReaction>(
            "community_post_reactions",
            mapOf("select" to "id", "post_id" to "eq.$postId", "profile_id" to "eq.$profileId", "reaction_type" to "eq.$reactionType")
        )
        return if (existing != null) {
            client.delete("community_post_reactions", mapOf("id" to "eq.${existing.id}"))
            ToggleResult(active = false, id = existing.id)
        } else {
            val created = client.post<CommunityPostReaction, CommunityPostReactionCreate>("community_post_reactions", CommunityPostReactionCreate(postId, profileId, reactionType))
            ToggleResult(active = true, id = created?.id)
        }
    }

    suspend fun getCommunityMessages(wallId: String, limit: Int = 250): List<CommunityMessage> = client.getList(
        "community_messages",
        mapOf("select" to "id,wall_id,profile_id,body,created_at", "wall_id" to "eq.$wallId", "order" to "created_at.desc", "limit" to limit.toString())
    )

    suspend fun sendCommunityMessage(wallId: String, profileId: String, body: String): CommunityMessage? =
        client.post<CommunityMessage, CommunityMessageCreate>("community_messages", CommunityMessageCreate(wallId, profileId, body))

    suspend fun sendCommunityImageMessage(wallId: String, profileId: String, imageUrl: String, fileName: String, mimeType: String = "image/jpeg", text: String = ""): CommunityMessage? =
        sendCommunityMessage(wallId, profileId, QuataChatPayloadCodec.encode(QuataChatAttachmentPayload("image", text, imageUrl, fileName, mimeType)))

    suspend fun getNotifications(profileId: String, unreadOnly: Boolean = false, limit: Int = 500): List<CommunityNotification> = client.getList(
        "community_notifications",
        mapOf(
            "select" to "*",
            "recipient_profile_id" to "eq.$profileId",
            "is_read" to if (unreadOnly) "eq.false" else null,
            "order" to "created_at.desc",
            "limit" to limit.toString()
        )
    )

    suspend fun markNotificationsRead(profileId: String, type: String? = null, wallId: String? = null): List<CommunityNotification> =
        client.patch("community_notifications", mapOfNotNull("recipient_profile_id" to "eq.$profileId", "type" to type?.let { "eq.$it" }, "wall_id" to wallId?.let { "eq.$it" }, "is_read" to "eq.false"), mapOf("is_read" to true))

    suspend fun createOrGetPrivateChat(user1: String, user2: String): String =
        client.rpc<RpcCreateOrGetPrivateChatRequest, String>("create_or_get_private_chat", RpcCreateOrGetPrivateChatRequest(user1, user2))

    suspend fun acceptPrivateChat(chatId: String) = client.rpcUnit("accept_private_chat", RpcPrivateChatDecisionRequest(chatId))

    suspend fun rejectPrivateChat(chatId: String) = client.rpcUnit("reject_private_chat", RpcPrivateChatDecisionRequest(chatId))

    suspend fun getPrivateChats(profileId: String, limit: Int = 100): List<CommunityPrivateChat> = client.getList(
        "community_private_chats",
        mapOf("select" to "*", "or" to "(user_low_id.eq.$profileId,user_high_id.eq.$profileId)", "order" to "last_message_at.desc", "limit" to limit.toString())
    )

    suspend fun getPrivateMessages(chatId: String, limit: Int = 250): List<CommunityPrivateMessage> = client.getList(
        "community_private_messages",
        mapOf("select" to "*", "chat_id" to "eq.$chatId", "order" to "created_at.asc", "limit" to limit.toString())
    )

    suspend fun sendPrivateMessage(chatId: String, senderProfileId: String, recipientProfileId: String, body: String? = null, attachmentUrl: String? = null, attachmentName: String? = null, attachmentType: String? = null): CommunityPrivateMessage? =
        client.post<CommunityPrivateMessage, CommunityPrivateMessageCreate>(
            "community_private_messages",
            CommunityPrivateMessageCreate(chatId, senderProfileId, recipientProfileId, body, attachmentUrl, attachmentName, attachmentType)
        )

    suspend fun markPrivateMessagesRead(chatId: String, recipientProfileId: String, readAtIso: String = Instant.now().toString()): List<CommunityPrivateMessage> =
        client.patch("community_private_messages", mapOf("chat_id" to "eq.$chatId", "recipient_profile_id" to "eq.$recipientProfileId", "read_at" to "is.null"), mapOf("read_at" to readAtIso))

    suspend fun getWallFollows(profileId: String? = null, wallId: String? = null): List<CommunityWallFollow> = client.getList(
        "community_wall_follows",
        mapOf("select" to "*", "profile_id" to profileId?.let { "eq.$it" }, "wall_id" to wallId?.let { "eq.$it" })
    )

    suspend fun toggleWallFollow(wallId: String, profileId: String): ToggleResult {
        val existing = client.getSingleOrNull<CommunityWallFollow>("community_wall_follows", mapOf("select" to "id", "wall_id" to "eq.$wallId", "profile_id" to "eq.$profileId"))
        return if (existing != null) {
            client.delete("community_wall_follows", mapOf("id" to "eq.${existing.id}"))
            ToggleResult(active = false, id = existing.id)
        } else {
            val created = client.post<CommunityWallFollow, CommunityWallFollowCreate>("community_wall_follows", CommunityWallFollowCreate(wallId, profileId))
            ToggleResult(active = true, id = created?.id)
        }
    }

    suspend fun toggleProfileFollow(followerProfileId: String, followedProfileId: String): ToggleResult {
        val existing = client.getSingleOrNull<CommunityProfileFollow>("community_profile_follows", mapOf("select" to "id", "follower_profile_id" to "eq.$followerProfileId", "followed_profile_id" to "eq.$followedProfileId"))
        return if (existing != null) {
            client.delete("community_profile_follows", mapOf("id" to "eq.${existing.id}"))
            ToggleResult(active = false, id = existing.id)
        } else {
            val created = client.post<CommunityProfileFollow, CommunityProfileFollowCreate>("community_profile_follows", CommunityProfileFollowCreate(followerProfileId, followedProfileId))
            ToggleResult(active = true, id = created?.id)
        }
    }

    suspend fun getEmergencyContacts(profileId: String): List<CommunityEmergencyContact> = client.getList(
        "community_emergency_contacts",
        mapOf("select" to "*", "profile_id" to "eq.$profileId", "order" to "position.asc,created_at.asc")
    )

    suspend fun addEmergencyContact(profileId: String, contactProfileId: String, position: Int): CommunityEmergencyContact? =
        client.post<CommunityEmergencyContact, CommunityEmergencyContactCreate>("community_emergency_contacts", CommunityEmergencyContactCreate(profileId, contactProfileId, position))

    suspend fun removeEmergencyContact(id: String) = client.delete("community_emergency_contacts", mapOf("id" to "eq.$id"))

    suspend fun sendSos(profileId: String, message: String, lat: Double? = null, lng: Double? = null, accuracy: Double? = null): JsonElement =
        client.rpc<RpcSendSosRequest, JsonElement>("quata_send_sos", RpcSendSosRequest(profileId, message, lat, lng, accuracy))

    suspend fun uploadPostImage(profileId: String, bytes: ByteArray, extension: String = "jpg", mimeType: String = "image/jpeg"): StorageUploadResult {
        val path = "$profileId/img-${System.currentTimeMillis()}-${randomToken()}.$extension"
        return client.uploadObject(path, bytes, mimeType, upsert = true)
    }

    suspend fun uploadAvatar(profileId: String, bytes: ByteArray, extension: String = "jpg", mimeType: String = "image/jpeg"): StorageUploadResult {
        val path = "avatars/$profileId/${System.currentTimeMillis()}-${randomToken()}.$extension"
        return client.uploadObject(path, bytes, mimeType, upsert = true)
    }

    suspend fun uploadCommunityChatImage(profileId: String, bytes: ByteArray, extension: String = "jpg", mimeType: String = "image/jpeg"): StorageUploadResult {
        val path = "$profileId/chat-community/${System.currentTimeMillis()}-${randomToken()}.$extension"
        return client.uploadObject(path, bytes, mimeType, upsert = true)
    }

    private fun digitsOnly(value: String): String = value.filter(Char::isDigit)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun randomToken(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(7)

    private fun mapOfNotNull(vararg pairs: Pair<String, String?>): Map<String, String> = pairs.mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
}
