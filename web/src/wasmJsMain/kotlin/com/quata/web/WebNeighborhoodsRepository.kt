@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.feature.neighborhoods.domain.FollowUserResult
import com.quata.feature.neighborhoods.domain.NeighborhoodCommunity
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import com.quata.feature.neighborhoods.domain.ProfileAttachment
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal enum class WebNeighborhoodsReadOperation { Directory, CurrentUserAdmin, UserProfile }
internal fun webNeighborhoodsReadAuthMode(operation: WebNeighborhoodsReadOperation): WebPostgrestAuthMode = when (operation) {
    WebNeighborhoodsReadOperation.Directory,
    WebNeighborhoodsReadOperation.UserProfile -> WebPostgrestAuthMode.Public
    WebNeighborhoodsReadOperation.CurrentUserAdmin -> WebPostgrestAuthMode.SessionRequired
}

/**
 * Browser read adapter for the Communities directory.
 *
 * Public directory reads use the configured publishable key with [WebPostgrestAuthMode.Public] and
 * omit Authorization. Administrative reads and every mutation require the restored session.
 * Community and private chat actions delegate to the same portable chat repository used by
 * Conversations. Existing backend-policy findings remain documented separately and are not
 * treated as a reason to replace Android-equivalent behavior with a client-side fallback.
 */
class WebNeighborhoodsRepository(
    private val client: WebPostgrestClient,
    private val authRepository: WebAuthRepository,
    private val chatRepository: ChatRepository,
    private val feedRepository: WebFeedRepository,
    private val pollIntervalMillis: Long = DefaultPollIntervalMillis,
) : NeighborhoodRepository {
    private val profileCache = mutableMapOf<String, CommunityUserProfile>()
    private var wallsByKey = emptyMap<String, WebCommunityWallStats>()

    override fun observeCommunities(): Flow<List<NeighborhoodCommunity>> = flow {
        while (currentCoroutineContext().isActive) {
            emit(loadCommunities())
            delay(pollIntervalMillis.coerceAtLeast(MinimumPollIntervalMillis))
        }
    }

    override suspend fun openNeighborhoodChat(neighborhood: String): Result<String> = runCatching {
        authenticatedUserId()
        openWebNeighborhoodConversation(
            neighborhood = neighborhood,
            communityIdForName = { name -> resolveCommunityWall(name)?.id },
            cachedConversationId = chatRepository::cachedCommunityConversationId,
            openConversation = { communityId, title ->
                chatRepository.openCommunityConversation(
                    communityId = communityId,
                    title = title,
                    participantIds = emptyList(),
                )
            },
        ).getOrThrow()
    }

    override suspend fun toggleFollowUser(userId: String): Result<FollowUserResult> = runCatching {
        val actorId = authenticatedUserId().requireWebCommunityIdentifier()
        val targetId = userId.requireWebCommunityIdentifier()
        require(actorId != targetId) { "web_community_follow_self" }
        val existing = loadFollows(actorId, targetId, WebPostgrestAuthMode.SessionRequired)
        val following = if (existing.isEmpty()) {
            client.post(
                "community_profile_follows",
                "{\"follower_profile_id\":${actorId.webCommunityJsonString()},\"followed_profile_id\":${targetId.webCommunityJsonString()}}",
            ).requireWebCommunitySuccess()
            true
        } else {
            client.delete(
                "community_profile_follows",
                mapOf("follower_profile_id" to "eq.$actorId", "followed_profile_id" to "eq.$targetId"),
            ).requireWebCommunitySuccess()
            false
        }
        val actor = loadProfiles(listOf(actorId), WebPostgrestAuthMode.SessionRequired).firstOrNull()
            ?: error("web_community_actor_profile_missing")
        FollowUserResult(targetId, following, actor)
    }

    override suspend fun reportPost(postId: String): Result<Unit> = runCatching {
        val actorId = authenticatedUserId().requireWebCommunityIdentifier()
        val targetId = postId.requireWebCommunityIdentifier()
        client.rpc(
            "quata_ugc_report",
            "{\"p_reporter_id\":${actorId.webCommunityJsonString()},\"p_target_type\":\"community_post\",\"p_target_id\":${targetId.webCommunityJsonString()},\"p_reason\":\"other\"}",
        ).requireWebCommunitySuccess()
    }

    override suspend fun addProfileComment(postId: String, comment: PostComment): Result<Post?> =
        feedRepository.addComment(postId, comment)

    override suspend fun reportProfile(userId: String): Result<Unit> = runCatching {
        val actorId = authenticatedUserId().requireWebCommunityIdentifier()
        val targetId = userId.requireWebCommunityIdentifier()
        client.rpc(
            "quata_ugc_report",
            "{\"p_reporter_id\":${actorId.webCommunityJsonString()},\"p_target_type\":\"profile\",\"p_target_id\":${targetId.webCommunityJsonString()},\"p_reason\":\"other\"}",
        ).requireWebCommunitySuccess()
    }

    override suspend fun setProfileBlocked(userId: String, blocked: Boolean): Result<Boolean> = runCatching {
        val actorId = authenticatedUserId().requireWebCommunityIdentifier()
        val targetId = userId.requireWebCommunityIdentifier()
        require(actorId != targetId) { "web_community_block_self" }
        client.rpc(
            if (blocked) "quata_profile_block" else "quata_profile_unblock",
            "{\"p_actor_profile_id\":${actorId.webCommunityJsonString()},\"p_profile_id\":${targetId.webCommunityJsonString()}}",
        ).requireWebCommunitySuccess()
        blocked
    }

    override suspend fun openPrivateChat(userId: String): Result<String> = runCatching {
        authenticatedUserId()
        openWebPrivateConversation(
            userId = userId,
            cachedConversationId = chatRepository::cachedPrivateConversationId,
            openConversation = chatRepository::openPrivateConversation,
        ).getOrThrow()
    }

    override suspend fun isCurrentUserAdmin(): Boolean = runCatching {
        val userId = authenticatedUserId()
        loadProfiles(ids = listOf(userId), authMode = webNeighborhoodsReadAuthMode(WebNeighborhoodsReadOperation.CurrentUserAdmin)).firstOrNull()?.isAdmin == true
    }.getOrDefault(false)

    override suspend fun setUserRoles(userId: String, isAdmin: Boolean, isOfficial: Boolean): Result<NeighborhoodUser> = runCatching {
        check(isCurrentUserAdmin()) { "web_community_admin_required" }
        val targetId = userId.requireWebCommunityIdentifier()
        client.patch(
            "community_profiles",
            mapOf("id" to "eq.$targetId"),
            "{\"is_admin\":$isAdmin,\"is_official\":$isOfficial}",
        ).requireWebCommunitySuccess()
        loadProfiles(listOf(targetId), WebPostgrestAuthMode.SessionRequired).firstOrNull()
            ?: error("web_community_profile_not_found")
    }

    override suspend fun getCachedUserProfile(userId: String, maxAgeMillis: Long?): CommunityUserProfile? = profileCache[userId]

    override suspend fun cacheUserProfile(profile: CommunityUserProfile) {
        profileCache[profile.user.id] = profile
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
        val followers = loadFollows(followedId = userId, authMode = WebPostgrestAuthMode.Public)
        val following = loadFollows(followerId = userId, authMode = WebPostgrestAuthMode.Public)
        val relatedIds = (followers.map(WebCommunityFollow::followerId) + following.map(WebCommunityFollow::followedId)).distinct()
        val related = loadProfiles(relatedIds, WebPostgrestAuthMode.Public).associateBy(NeighborhoodUser::id)
        val actorFollowing = authRepository.sessionForAuthenticatedRequest()?.userId?.let { actorId ->
            loadFollows(followerId = actorId, authMode = WebPostgrestAuthMode.SessionRequired)
                .map(WebCommunityFollow::followedId)
                .toSet()
        }.orEmpty()
        val posts = feedRepository.getProfilePosts(userId).getOrThrow()
        val actorId = authRepository.sessionForAuthenticatedRequest()?.userId
        val attachments = actorId?.let { loadSharedAttachments(it, userId, profile.displayName) }.orEmpty()
        val enriched = profile.copy(
            isFollowing = userId in actorFollowing,
            followersCount = followers.size,
            followingCount = following.size,
            postsCount = posts.size,
        )
        CommunityUserProfile(
            user = enriched,
            posts = posts,
            attachments = attachments,
            followers = followers.mapNotNull { related[it.followerId]?.copy(isFollowing = it.followerId in actorFollowing) },
            following = following.mapNotNull { related[it.followedId]?.copy(isFollowing = it.followedId in actorFollowing) },
            isBlockedByCurrentUser = authRepository.sessionForAuthenticatedRequest()?.userId?.let { actorId ->
                loadProfileBlocks(actorId, userId).isNotEmpty()
            } ?: false,
        ).also { profileCache[userId] = it }
    }

    private suspend fun loadCommunities(): List<NeighborhoodCommunity> {
        val profiles = loadProfiles(authMode = webNeighborhoodsReadAuthMode(WebNeighborhoodsReadOperation.Directory))
        val walls = loadWalls()
        val profilesByNeighborhood = profiles
            .filter { it.neighborhood.isNotBlank() }
            .groupBy { it.neighborhood.normalizedCommunityKey() }
        return webCommunityDirectoryKeys(
            profileKeys = profilesByNeighborhood.keys,
            activeWallKeys = walls.flatMap(WebCommunityWallStats::communityKeys),
        )
            .map { key ->
                val wall = wallsByKey[key]
                val users = profilesByNeighborhood[key].orEmpty().sortedBy { it.displayName.lowercase() }
                NeighborhoodCommunity(
                    name = wall?.name?.takeIf(String::isNotBlank)
                        ?: users.firstOrNull()?.neighborhood
                        ?: key,
                    users = users,
                    conversationId = null,
                    lastMessagePreview = null,
                    lastMessageAtMillis = wall?.chatLastAtMillis,
                    messageCount = wall?.chatCount ?: 0,
                )
            }
            .sortedWith(compareByDescending<NeighborhoodCommunity> { it.lastMessageAtMillis ?: 0L }.thenBy { it.name.lowercase() })
    }

    private suspend fun loadWalls(): List<WebCommunityWallStats> {
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
        wallsByKey = walls.flatMap { wall -> wall.communityKeys().map { key -> key to wall } }.toMap()
        return walls
    }

    private suspend fun resolveCommunityWall(name: String): WebCommunityWallStats? {
        if (wallsByKey.isEmpty()) loadWalls()
        return wallsByKey[name.normalizedCommunityKey()]
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

    private suspend fun loadFollows(
        followerId: String? = null,
        followedId: String? = null,
        authMode: WebPostgrestAuthMode,
    ): List<WebCommunityFollow> = client.rows(
        table = "community_profile_follows",
        query = buildMap {
            put("select", "id,follower_profile_id,followed_profile_id,created_at")
            followerId?.let { put("follower_profile_id", "eq.${it.requireWebCommunityIdentifier()}") }
            followedId?.let { put("followed_profile_id", "eq.${it.requireWebCommunityIdentifier()}") }
        },
        limit = DirectoryLimit,
        authMode = authMode,
    ).map { row ->
        WebCommunityFollow(
            followerId = row.webCommunityString("follower_profile_id") ?: error("web_community_follow_follower_missing"),
            followedId = row.webCommunityString("followed_profile_id") ?: error("web_community_follow_followed_missing"),
        )
    }

    private suspend fun loadProfileBlocks(actorId: String, targetId: String): List<JsonObject> = client.rows(
        table = "chat_profile_blocks",
        query = mapOf(
            "select" to "id",
            "thread_id" to "is.null",
            "blocker_profile_id" to "eq.${actorId.requireWebCommunityIdentifier()}",
            "blocked_profile_id" to "eq.${targetId.requireWebCommunityIdentifier()}",
        ),
        limit = 1,
        authMode = WebPostgrestAuthMode.SessionRequired,
    )

    private suspend fun loadSharedAttachments(
        actorId: String,
        targetId: String,
        defaultSenderName: String,
    ): List<ProfileAttachment> {
        val body = "{\"p_actor_profile_id\":${actorId.requireWebCommunityIdentifier().webCommunityJsonString()}," +
            "\"p_peer_profile_id\":${targetId.requireWebCommunityIdentifier().webCommunityJsonString()}," +
            "\"p_limit\":120,\"p_offset\":0}"
        val payload = when (val result = client.rpc("quata_chat_list_shared_attachments", body)) {
            is WebPostgrestResult.Success -> Json.parseToJsonElement(result.body).jsonObject
            is WebPostgrestResult.Failure -> throw WebPostgrestReadException(result)
        }
        return payload["files"]?.jsonArray.orEmpty()
            .mapNotNull { it.jsonObject.toProfileAttachment(defaultSenderName) }
            .distinctBy { "${it.uri.substringBefore('?').lowercase()}|${it.name.lowercase()}" }
            .sortedByDescending { it.sentAtMillis ?: 0L }
    }

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

    private companion object {
        const val DirectoryLimit = 500
        const val DefaultPollIntervalMillis = 30_000L
        const val MinimumPollIntervalMillis = 5_000L
        const val ProfileSelect = "id,display_name,phone,country_code,phone_local,barrio,neighborhood,telefono,nombre,avatar_url,avatar,followers_count,following_count,is_admin,is_official"
        const val WallStatsSelect = "id,slug,name,normalized_name,chat_count,chat_last_at"
        val PostgrestIdentifier = Regex("[A-Za-z0-9_-]+")
    }
}

internal suspend fun openWebNeighborhoodConversation(
    neighborhood: String,
    communityIdForName: suspend (String) -> String?,
    cachedConversationId: suspend (String) -> String?,
    openConversation: suspend (communityId: String, title: String) -> Result<String>,
): Result<String> = runCatching {
    val title = neighborhood.trim().takeIf(String::isNotEmpty)
        ?: error("web_community_neighborhood_missing")
    cachedConversationId(title)
        ?: communityIdForName(title)
            ?.takeIf { it.matches(PostgrestUuid) }
            ?.let { openConversation(it, title).getOrThrow() }
        ?: error("web_community_wall_not_found")
}

/** Active walls enrich profile-backed communities but never create empty directory cards. */
internal fun webCommunityDirectoryKeys(
    profileKeys: Collection<String>,
    activeWallKeys: Collection<String>,
): List<String> {
    val profiles = profileKeys.filter(String::isNotBlank).distinct()
    return (profiles + activeWallKeys.filter(profiles::contains)).distinct()
}

internal suspend fun openWebPrivateConversation(
    userId: String,
    cachedConversationId: suspend (String) -> String?,
    openConversation: suspend (String) -> Result<String>,
): Result<String> = runCatching {
    val peerId = userId.trim().takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) }
        ?: error("web_community_profile_id_invalid")
    cachedConversationId(peerId) ?: openConversation(peerId).getOrThrow()
}

private data class WebCommunityWallStats(
    val id: String,
    val slug: String?,
    val name: String?,
    val normalizedName: String?,
    val chatCount: Int?,
    val chatLastAtMillis: Long?,
)

private data class WebCommunityFollow(val followerId: String, val followedId: String)

private fun String.requireWebCommunityIdentifier(): String {
    require(matches(Regex("[A-Za-z0-9_-]+"))) { "web_community_identifier_invalid" }
    return this
}

private fun WebPostgrestResult.requireWebCommunitySuccess() {
    if (this is WebPostgrestResult.Failure) error("web_postgrest_$reason")
}

private fun String.webCommunityJsonString(): String = buildString {
    append('"')
    for (char in this@webCommunityJsonString) append(if (char == '"' || char == '\\') "\\$char" else char)
    append('"')
}

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

private fun JsonObject.toWallStats(): WebCommunityWallStats = WebCommunityWallStats(
    id = webCommunityString("id") ?: error("web_community_response_missing_wall_id"),
    slug = webCommunityString("slug"),
    name = webCommunityString("name"),
    normalizedName = webCommunityString("normalized_name")?.normalizedCommunityKey(),
    chatCount = webCommunityInt("chat_count"),
    chatLastAtMillis = webCommunityString("chat_last_at")?.toWebCommunityEpochMillis(),
)

private fun WebCommunityWallStats.communityKeys(): Set<String> =
    listOf(slug, name, normalizedName)
        .mapNotNull { it?.normalizedCommunityKey()?.takeIf(String::isNotBlank) }
        .toSet()

private fun JsonObject.webCommunityString(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

private fun JsonObject.webCommunityInt(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull

private fun JsonObject.toProfileAttachment(defaultSenderName: String): ProfileAttachment? {
    val thumbnail = this["thumb"] as? JsonObject
    val uri = webCommunityString("url")
        ?: webCommunityString("file_url")
        ?: thumbnail?.webCommunityString("url")
        ?: webCommunityString("thumb")
        ?: return null
    val name = webCommunityString("name")
        ?: webCommunityString("file_name")
        ?: uri.substringBefore('?').substringAfterLast('/').ifBlank { "file" }
    return ProfileAttachment(
        id = "sb:${this["id"]?.jsonPrimitive?.contentOrNull ?: uri.hashCode()}",
        name = name,
        uri = uri,
        mimeType = webCommunityString("mime_type"),
        sentAtMillis = this["created_at_millis"]?.jsonPrimitive?.longOrNull
            ?: webCommunityString("created_at")?.toWebCommunityEpochMillis(),
        senderName = webCommunityString("sender_name")?.takeIf(String::isNotBlank) ?: defaultSenderName,
    )
}

private fun String.normalizedCommunityKey(): String = trim().lowercase().replace(Regex("\\s+"), " ")

private val PostgrestUuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")

private fun List<String>.toPostgrestInFilter(): String = "in.(${joinToString(",")})"

private fun String.toWebCommunityEpochMillis(): Long? = browserDateParse(this)
    ?.takeIf { !it.isNaN() }
    ?.toLong()

private fun browserDateParse(value: String): Double? = js("Date.parse(value)")
