@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.feature.neighborhoods.domain.ProfileAttachment
import com.quata.feature.neighborhoods.domain.profileAttachmentAvailability
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.neighborhoods.domain.FollowUserResult
import com.quata.feature.neighborhoods.domain.NeighborhoodCommunity
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import com.quata.feature.neighborhoods.domain.NeighborhoodWallSnapshot
import com.quata.feature.neighborhoods.domain.mergeNeighborhoodDirectory
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal enum class WebNeighborhoodsReadOperation { Directory, CurrentUserAdmin, UserProfile }
internal fun webNeighborhoodsReadAuthMode(operation: WebNeighborhoodsReadOperation): WebPostgrestAuthMode = when (operation) {
    WebNeighborhoodsReadOperation.Directory -> WebPostgrestAuthMode.Public
    WebNeighborhoodsReadOperation.CurrentUserAdmin -> WebPostgrestAuthMode.SessionRequired
    // A profile is a public route.  The postgrest client still uses the publishable key here;
    // private action requests below explicitly require the current bearer session.
    WebNeighborhoodsReadOperation.UserProfile -> WebPostgrestAuthMode.Public
}
internal const val WebNeighborhoodsPrivateActionsRequireSession = true

/**
 * Browser read adapter for the Communities directory.
 *
 * Public directory reads use the configured publishable key and omit Authorization. Directory
 * chat/follow actions use authenticated production repositories and tables. Report, roles and
 * profile timeline work remain reserved for the separate CommunityProfile surface.
 */
class WebNeighborhoodsRepository(
    private val client: WebPostgrestClient,
    private val authRepository: WebAuthRepository,
    private val chatRepository: ChatRepository,
    private val pollIntervalMillis: Long = DefaultPollIntervalMillis,
) : NeighborhoodRepository {
    private val profileCache = mutableMapOf<String, Pair<CommunityUserProfile, Double>>()

    override fun observeCommunities(): Flow<List<NeighborhoodCommunity>> = flow {
        while (currentCoroutineContext().isActive) {
            emit(loadCommunities())
            delay(pollIntervalMillis.coerceAtLeast(MinimumPollIntervalMillis))
        }
    }

    override suspend fun openNeighborhoodChat(neighborhood: String): Result<String> = runCatching {
        val name = neighborhood.trim().takeIf(String::isNotBlank) ?: error("web_community_neighborhood_missing")
        val userId = authenticatedUserId()
        chatRepository.cachedCommunityConversationId(name)
            ?: chatRepository.openCommunityConversation(
                loadCommunities().firstOrNull { it.name.normalizedCommunityKey() == name.normalizedCommunityKey() }
                    ?.conversationId?.removePrefix("wall:") ?: name.normalizedCommunityKey(),
                name,
                listOf(userId),
            ).getOrThrow()
    }

    override suspend fun toggleFollowUser(userId: String): Result<FollowUserResult> = runCatching {
        require(userId.matches(PostgrestIdentifier)) { "web_community_invalid_profile_id" }
        val currentUserId = authenticatedUserId()
        require(userId != currentUserId) { "web_community_cannot_follow_self" }
        val existing = client.rows("community_profile_follows", webNeighborhoodFollowLookup(currentUserId, userId), 1, WebPostgrestAuthMode.SessionRequired).firstOrNull()
        val following = if (existing == null) {
            client.requireSuccess("community_profile_follows", client.post("community_profile_follows", buildJsonObject { put("follower_profile_id", currentUserId); put("followed_profile_id", userId) }.toString()))
            true
        } else {
            client.requireSuccess("community_profile_follows", client.delete("community_profile_follows", mapOf("id" to "eq.${existing.webCommunityString("id") ?: error("web_community_follow_id_missing")}")))
            false
        }
        val current = loadProfiles(listOf(currentUserId), WebPostgrestAuthMode.SessionRequired).firstOrNull()
            ?: NeighborhoodUser(id = currentUserId, displayName = "Usuario", email = "", neighborhood = "")
        FollowUserResult(userId, following, current)
    }

    override suspend fun reportPost(postId: String): Result<Unit> = reportUgc("community_post", postId)

    override suspend fun addPostComment(postId: String, body: String): Result<Unit> = runCatching {
        require(postId.matches(PostgrestIdentifier)) { "web_community_invalid_post_id" }
        val userId = authenticatedUserId()
        val cleanBody = body.trim().takeIf(String::isNotEmpty) ?: error("web_community_comment_empty")
        client.requireSuccess(
            "community_comments",
            client.post(
                "community_comments",
                webNeighborhoodCommentPayload(postId, userId, cleanBody),
            ),
        )
    }

    override suspend fun togglePostLike(postId: String): Result<Unit> = runCatching {
        require(postId.matches(PostgrestIdentifier)) { "web_community_invalid_post_id" }
        val userId = authenticatedUserId()
        val existing = client.rows(
            "community_post_likes",
            mapOf("select" to "id", "post_id" to "eq.$postId", "profile_id" to "eq.$userId"),
            1,
            WebPostgrestAuthMode.SessionRequired,
        ).firstOrNull()
        if (existing == null) {
            client.requireSuccess("community_post_likes", client.post("community_post_likes", buildJsonObject { put("post_id", postId); put("profile_id", userId) }.toString()))
        } else {
            val likeId = existing.webCommunityString("id") ?: error("web_community_like_id_missing")
            client.requireSuccess("community_post_likes", client.delete("community_post_likes", mapOf("id" to "eq.$likeId")))
        }
    }

    override suspend fun reportProfile(profileId: String): Result<Unit> = reportUgc("profile", profileId)

    override suspend fun blockProfile(profileId: String): Result<Unit> = runCatching {
        require(profileId.matches(PostgrestIdentifier)) { "web_community_invalid_profile_id" }
        val actor = authenticatedUserId()
        client.requireSuccess("quata_profile_block", client.rpc("quata_profile_block", webNeighborhoodBlockPayload(actor, profileId)))
    }

    private suspend fun reportUgc(targetType: String, targetId: String): Result<Unit> = runCatching {
        require(targetId.matches(PostgrestIdentifier)) { "web_community_invalid_target_id" }
        val actor = authenticatedUserId()
        client.requireSuccess("quata_ugc_report", client.rpc("quata_ugc_report", webNeighborhoodReportPayload(actor, targetType, targetId)))
    }

    override suspend fun openPrivateChat(userId: String): Result<String> = runCatching {
        require(userId.matches(PostgrestIdentifier)) { "web_community_invalid_profile_id" }
        authenticatedUserId()
        chatRepository.cachedPrivateConversationId(userId)
            ?: chatRepository.openPrivateConversation(userId).getOrThrow()
    }

    override suspend fun isCurrentUserAdmin(): Boolean = runCatching {
        val userId = authenticatedUserId()
        loadProfiles(ids = listOf(userId), authMode = webNeighborhoodsReadAuthMode(WebNeighborhoodsReadOperation.CurrentUserAdmin)).firstOrNull()?.isAdmin == true
    }.getOrDefault(false)

    override suspend fun setUserRoles(userId: String, isAdmin: Boolean, isOfficial: Boolean): Result<NeighborhoodUser> = runCatching {
        require(isCurrentUserAdmin()) { "web_community_admin_required" }
        client.requireSuccess("community_profiles", client.patch("community_profiles", mapOf("id" to "eq.$userId"), "{\"is_admin\":$isAdmin,\"is_official\":$isOfficial}"))
        loadProfiles(listOf(userId), WebPostgrestAuthMode.SessionRequired).firstOrNull() ?: error("web_community_profile_not_found")
    }

    override suspend fun getCachedUserProfile(userId: String, maxAgeMillis: Long?): CommunityUserProfile? =
        profileCache[userId]?.takeIf { cached -> maxAgeMillis == null || browserNowMillis() - cached.second <= maxAgeMillis.toDouble() }?.first

    override suspend fun cacheUserProfile(profile: CommunityUserProfile) {
        profileCache[profile.user.id] = profile to browserNowMillis()
    }

    override fun observeUserProfile(userId: String): Flow<Result<CommunityUserProfile>> = flow {
        while (currentCoroutineContext().isActive) {
            emit(getUserProfile(userId))
            delay(pollIntervalMillis.coerceAtLeast(MinimumPollIntervalMillis))
        }
    }

    override suspend fun getUserProfile(userId: String): Result<CommunityUserProfile> = runCatching {
        require(userId.matches(PostgrestIdentifier)) { "web_community_invalid_profile_id" }
        val profile = loadProfiles(ids = listOf(userId), authMode = webNeighborhoodsReadAuthMode(WebNeighborhoodsReadOperation.UserProfile)).firstOrNull()
            ?: error("web_community_profile_not_found")
        val basePosts = client.rows(
            table = "community_posts",
            query = mapOf("select" to PostSelect, "profile_id" to "eq.$userId", "order" to "created_at.desc"),
            limit = ProfilePostsLimit,
            authMode = WebPostgrestAuthMode.Public,
        ).map { it.toCommunityProfilePost(profile) }
        val postIds = basePosts.map { it.id }
        val commentRows = if (postIds.isEmpty()) emptyList() else client.rows("community_comments", mapOf("select" to "id,post_id,profile_id,body,created_at", "post_id" to postIds.toPostgrestInFilter()), 500, WebPostgrestAuthMode.Public)
        val likeRows = if (postIds.isEmpty()) emptyList() else client.rows("community_post_likes", mapOf("select" to "id,post_id,profile_id", "post_id" to postIds.toPostgrestInFilter()), 1000, WebPostgrestAuthMode.Public)
        val interactionIds = (commentRows + likeRows).mapNotNull { it.webCommunityString("profile_id") }.distinct()
        val interactionProfiles = if (interactionIds.isEmpty()) emptyMap() else loadProfiles(interactionIds, WebPostgrestAuthMode.Public).associateBy { it.id }
        val signedInId = authRepository.sessionForAuthenticatedRequest()?.userId
        val posts = basePosts.map { post ->
            val comments = commentRows.filter { it.webCommunityString("post_id") == post.id }.map { row ->
                val authorId = row.webCommunityString("profile_id")
                PostComment(row.webCommunityString("id") ?: "comment:${post.id}", interactionProfiles[authorId]?.displayName ?: "Usuario", row.webCommunityString("body").orEmpty(), row.webCommunityString("created_at").orEmpty(), authorId = authorId)
            }
            val likes = likeRows.filter { it.webCommunityString("post_id") == post.id }
            post.copy(comments = comments, likesCount = likes.size, isLikedByCurrentUser = signedInId != null && likes.any { it.webCommunityString("profile_id") == signedInId })
        }
        val followers = profileRelations("followed_profile_id", userId)
        val following = profileRelations("follower_profile_id", userId)
        val relatedIds = (followers.mapNotNull { it.webCommunityString("follower_profile_id") } +
            following.mapNotNull { it.webCommunityString("followed_profile_id") }).distinct()
        val related = if (relatedIds.isEmpty()) emptyMap() else loadProfiles(relatedIds, WebPostgrestAuthMode.Public).associateBy { it.id }
        val currentId = signedInId
        val attachmentResult = currentId?.let { loadSharedAttachments(it, userId, profile.displayName) }
        val currentFollowing = currentId?.let { profileRelations("follower_profile_id", it).mapNotNull { row -> row.webCommunityString("followed_profile_id") }.toSet() }.orEmpty()
        val followerUsers = followers.mapNotNull { related[it.webCommunityString("follower_profile_id")] }
            .map { it.copy(isFollowing = it.id in currentFollowing) }
        val followingUsers = following.mapNotNull { related[it.webCommunityString("followed_profile_id")] }
            .map { it.copy(isFollowing = it.id in currentFollowing) }
        val enriched = profile.copy(
            isFollowing = userId in currentFollowing,
            followersCount = followers.size,
            followingCount = following.size,
            postsCount = posts.size,
        )
        CommunityUserProfile(
            user = enriched,
            posts = posts,
            attachments = attachmentResult?.getOrNull().orEmpty(),
            attachmentAvailability = profileAttachmentAvailability(
                hasAuthenticatedSession = currentId != null,
                loadSucceeded = attachmentResult?.isSuccess == true,
            ),
            followers = followerUsers,
            following = followingUsers,
        ).also { profileCache[userId] = it to browserNowMillis() }
    }

    private suspend fun loadSharedAttachments(actorId: String, peerId: String, senderName: String): Result<List<ProfileAttachment>> = runCatching {
        val body = webNeighborhoodSharedAttachmentsPayload(actorId, peerId)
        val result = client.rpc("quata_chat_list_shared_attachments", body)
        val root = (result as? WebPostgrestResult.Success)?.body?.let { Json.parseToJsonElement(it).jsonObject }
            ?: error("web_community_shared_attachments_unavailable")
        root["files"]?.jsonArray.orEmpty().mapNotNull { element ->
            val row = element.jsonObject
            val uri = row.webCommunityString("url") ?: row.webCommunityString("file_url") ?: return@mapNotNull null
            ProfileAttachment(
                id = "web:${row.webCommunityString("id") ?: uri}",
                name = row.webCommunityString("name") ?: row.webCommunityString("file_name") ?: "attachment",
                uri = uri,
                mimeType = row.webCommunityString("mime_type"),
                sentAtMillis = row.webCommunityString("created_at")?.toWebCommunityEpochMillis(),
                senderName = row.webCommunityString("sender_name") ?: senderName,
            )
        }
    }

    private suspend fun profileRelations(column: String, profileId: String): List<JsonObject> = client.rows(
        table = "community_profile_follows",
        query = mapOf("select" to "follower_profile_id,followed_profile_id", column to "eq.$profileId"),
        limit = DirectoryLimit,
        authMode = WebPostgrestAuthMode.Public,
    )

    private suspend fun loadCommunities(): List<NeighborhoodCommunity> {
        val profiles = loadProfiles(authMode = webNeighborhoodsReadAuthMode(WebNeighborhoodsReadOperation.Directory))
        val walls = client.rows(
            table = "community_walls_stats",
            query = mapOf(
                "select" to WallStatsSelect,
                "is_active" to "eq.true",
                "order" to "sort_order.asc,chat_last_at.desc,created_at.desc",
            ),
            limit = DirectoryLimit,
            authMode = webNeighborhoodsReadAuthMode(WebNeighborhoodsReadOperation.Directory),
        ).map(JsonObject::toWallStats)
        return mergeNeighborhoodDirectory(profiles, walls.map { NeighborhoodWallSnapshot(it.id, it.name.orEmpty(), it.normalizedName, it.chatCount ?: 0, it.chatLastAtMillis) })
    }

    private suspend fun loadProfiles(
        ids: List<String>? = null,
        authMode: WebPostgrestAuthMode,
    ): List<NeighborhoodUser> = client.rows(
        table = "community_profiles",
        query = buildMap {
            put("select", ProfileSelect)
            ids?.takeIf { it.isNotEmpty() }?.let { put("id", it.toPostgrestInFilter()) }
        },
        limit = DirectoryLimit,
        authMode = authMode,
    ).map(JsonObject::toNeighborhoodUser)

    private suspend fun authenticatedUserId(): String = authRepository.sessionForAuthenticatedRequest()?.userId
        ?: error("web_community_session_missing")

    private suspend fun WebPostgrestClient.rows(
        table: String,
        query: Map<String, String>,
        limit: Int,
        authMode: WebPostgrestAuthMode,
    ): List<JsonObject> = when (val result = get(table, query, limit, authMode = authMode)) {
        is WebPostgrestResult.Success -> Json.parseToJsonElement(result.body).jsonArray.map { it.jsonObject }
        is WebPostgrestResult.Failure -> throw WebPostgrestReadException(result)
    }

    private fun WebPostgrestClient.requireSuccess(table: String, result: WebPostgrestResult) {
        when (result) {
            is WebPostgrestResult.Success -> Unit
            is WebPostgrestResult.Failure -> throw WebPostgrestReadException(result)
        }
    }

    private companion object {
        const val DirectoryLimit = 500
        const val DefaultPollIntervalMillis = 30_000L
        const val MinimumPollIntervalMillis = 5_000L
        const val ProfileSelect = "id,display_name,phone,country_code,phone_local,barrio,neighborhood,telefono,nombre,avatar_url,avatar,followers_count,following_count,is_admin,is_official"
        const val PostSelect = "id,profile_id,body,content,image_url,video_url,created_at"
        const val ProfilePostsLimit = 120
        const val WallStatsSelect = "id,slug,name,normalized_name,chat_count,chat_last_at"
        val PostgrestIdentifier = Regex("[A-Za-z0-9_-]+")
    }
}

private data class WebCommunityWallStats(
    val id: String?,
    val name: String?,
    val normalizedName: String?,
    val chatCount: Int?,
    val chatLastAtMillis: Long?,
)

private fun JsonObject.toNeighborhoodUser(): NeighborhoodUser {
    val id = webCommunityString("id") ?: error("web_community_response_missing_id")
    val neighborhood = webCommunityString("neighborhood") ?: webCommunityString("barrio").orEmpty()
    return NeighborhoodUser(
        id = id,
        displayName = webCommunityString("display_name") ?: webCommunityString("nombre") ?: "Usuario",
        email = webCommunityString("phone")
            ?: listOfNotNull(webCommunityString("country_code"), webCommunityString("phone_local")).joinToString("")
                .takeIf(String::isNotBlank).orEmpty(),
        neighborhood = neighborhood,
        avatarUrl = webCommunityString("avatar_url") ?: webCommunityString("avatar"),
        isAdmin = webCommunityString("is_admin") == "true",
        isOfficial = webCommunityString("is_official") == "true",
        followersCount = webCommunityInt("followers_count") ?: 0,
        followingCount = webCommunityInt("following_count") ?: 0,
    )
}

private fun JsonObject.toCommunityProfilePost(author: NeighborhoodUser): Post = Post(
    id = webCommunityString("id") ?: error("web_community_post_missing_id"),
    author = User(author.id, author.email, author.displayName, author.neighborhood, author.avatarUrl, author.isAdmin, author.isOfficial),
    text = webCommunityString("body") ?: webCommunityString("content").orEmpty(),
    imageUrl = webCommunityString("image_url"),
    videoUrl = webCommunityString("video_url"),
    createdAt = webCommunityString("created_at").orEmpty(),
)

private fun Post.toProfileAttachments(): List<ProfileAttachment> = listOfNotNull(
    imageUrl?.let { uri -> ProfileAttachment("post:$id:image", "imagen", uri, "image/*", createdAt.toWebCommunityEpochMillis(), author.displayName) },
    videoUrl?.let { uri -> ProfileAttachment("post:$id:video", "vídeo", uri, "video/*", createdAt.toWebCommunityEpochMillis(), author.displayName) },
)

private fun JsonObject.toWallStats(): WebCommunityWallStats = WebCommunityWallStats(
    id = webCommunityString("id") ?: webCommunityString("slug"),
    name = webCommunityString("name"),
    normalizedName = webCommunityString("normalized_name")?.normalizedCommunityKey(),
    chatCount = webCommunityInt("chat_count"),
    chatLastAtMillis = webCommunityString("chat_last_at")?.toWebCommunityEpochMillis(),
)

private fun JsonObject.webCommunityString(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

private fun JsonObject.webCommunityInt(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull

private fun String.normalizedCommunityKey(): String = trim().lowercase().replace(Regex("\\s+"), " ")

internal fun webNeighborhoodFollowLookup(currentUserId: String, followedUserId: String): Map<String, String> = mapOf(
    "select" to "id",
    "follower_profile_id" to "eq.$currentUserId",
    "followed_profile_id" to "eq.$followedUserId",
)

private fun List<String>.toPostgrestInFilter(): String = "in.(${joinToString(",")})"

private fun String.toWebCommunityEpochMillis(): Long? = browserDateParse(this)
    ?.takeIf { !it.isNaN() }
    ?.toLong()

private fun browserDateParse(value: String): Double? = js("Date.parse(value)")
private fun browserNowMillis(): Double = js("Date.now()")

internal fun webNeighborhoodReportPayload(actor: String, type: String, target: String): String =
    "{\"p_actor_profile_id\":\"$actor\",\"p_target_type\":\"$type\",\"p_target_id\":\"$target\",\"p_reason\":\"other\"}"

internal fun webNeighborhoodBlockPayload(actor: String, profile: String): String =
    "{\"p_actor_profile_id\":\"$actor\",\"p_profile_id\":\"$profile\"}"

internal fun webNeighborhoodSharedAttachmentsPayload(actor: String, peer: String): String =
    "{\"p_actor_profile_id\":\"$actor\",\"p_peer_profile_id\":\"$peer\",\"p_limit\":120,\"p_offset\":0}"

internal fun webNeighborhoodCommentPayload(postId: String, profileId: String, body: String): String =
    buildJsonObject { put("post_id", postId); put("profile_id", profileId); put("body", body) }.toString()
