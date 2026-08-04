package com.quata.feature.neighborhoods.data

import com.quata.core.session.IosRenewableAuthSession
import com.quata.core.data.toFoundationData
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.feature.neighborhoods.domain.FollowUserResult
import com.quata.feature.neighborhoods.domain.NeighborhoodCommunity
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import com.quata.feature.neighborhoods.domain.ProfileAttachment
import com.quata.feature.neighborhoods.domain.distinctByCommunityIdentity
import com.quata.feature.neighborhoods.domain.isCommunityProfileCacheUsable
import com.quata.feature.feed.data.IosFeedReadTransport
import com.quata.feature.feed.data.IosFeedRuntimeConfiguration
import com.quata.feature.feed.data.IosAuthenticatedFeedRepository
import com.quata.feature.feed.data.RemoteFeedReadRepository
import com.quata.feature.feed.domain.ReadOnlyFeedRepository
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSJSONSerialization
import platform.Foundation.NSNull
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionTask
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Client-safe configuration injected by the UIKit composition root. */
data class IosNeighborhoodsRuntimeConfiguration(
    val supabaseUrl: String,
    val supabasePublishableKey: String,
)

/**
 * iOS read adapter for the common Communities directory.  An anonymous instance sends the
 * publishable key as its bearer and exposes only directory/profile reads; every write still
 * fails closed unless a restored session is present.
 *
 * It uses URLSession and the same renewable Keychain session as Auth/Feed/Chat. Directory and
 * profile reads are real PostgREST snapshots; there is deliberately no fabricated local data or
 * unverified realtime subscription. Authenticated mutations use the same deployed tables and
 * allowlisted RPCs as Android. Existing backend-policy findings remain documented separately and
 * are not replaced with a client-side fallback.
 */
@OptIn(ExperimentalForeignApi::class)
class IosNeighborhoodsReadRepository(
    private val configuration: IosNeighborhoodsRuntimeConfiguration,
    private val authSession: IosRenewableAuthSession?,
    private val chatRepository: ChatRepository,
) : NeighborhoodRepository {
    private val profileCache = mutableMapOf<String, IosCachedCommunityProfile>()
    private var wallsByKey = emptyMap<String, IosCommunityWallStats>()
    private val feedConfiguration = IosFeedRuntimeConfiguration(configuration.supabaseUrl, configuration.supabasePublishableKey)
    private val feedTransport = IosFeedReadTransport(feedConfiguration, authSession)

    override fun observeCommunities(): Flow<List<NeighborhoodCommunity>> = flow {
        emit(loadCommunities())
    }

    override suspend fun openNeighborhoodChat(neighborhood: String): Result<String> = runCatching {
        val cleanNeighborhood = neighborhood.trim().takeIf(String::isNotEmpty)
            ?: error("ios_communities_neighborhood_missing")
        val session = authenticatedSession()
        chatRepository.cachedCommunityConversationId(cleanNeighborhood)
            ?: resolveCommunityWall(cleanNeighborhood)?.let { wall ->
                chatRepository.openCommunityConversation(
                    communityId = wall.id,
                    title = wall.name ?: cleanNeighborhood,
                    participantIds = listOf(session.userId),
                ).getOrThrow()
            }
            ?: error("ios_communities_wall_not_found")
    }

    override suspend fun toggleFollowUser(userId: String): Result<FollowUserResult> = runCatching {
        val actorId = authenticatedSession().userId.requireIosNeighborhoodIdentifier()
        val targetId = userId.requireIosNeighborhoodIdentifier()
        require(actorId != targetId) { "ios_communities_follow_self" }
        val existing = loadFollows(actorId, targetId)
        val following = if (existing.isEmpty()) {
            feedTransport.mutate(
                table = "community_profile_follows",
                method = "POST",
                body = "{\"follower_profile_id\":${actorId.toIosNeighborhoodJsonString()},\"followed_profile_id\":${targetId.toIosNeighborhoodJsonString()}}",
            ).getOrThrow()
            true
        } else {
            feedTransport.mutate(
                table = "community_profile_follows",
                method = "DELETE",
                query = mapOf("follower_profile_id" to "eq.$actorId", "followed_profile_id" to "eq.$targetId"),
            ).getOrThrow()
            false
        }
        val actor = loadProfiles(listOf(actorId)).firstOrNull() ?: error("ios_communities_actor_profile_missing")
        FollowUserResult(targetId, following, actor)
    }

    override suspend fun reportPost(postId: String): Result<Unit> = runCatching {
        val actorId = authenticatedSession().userId.requireIosNeighborhoodIdentifier()
        val targetId = postId.requireIosNeighborhoodIdentifier()
        feedTransport.reportPostRpc(
            "{\"p_actor_profile_id\":${actorId.toIosNeighborhoodJsonString()},\"p_target_type\":\"community_post\",\"p_target_id\":${targetId.toIosNeighborhoodJsonString()},\"p_reason\":\"other\"}",
        ).getOrThrow()
    }

    override suspend fun addProfileComment(postId: String, comment: PostComment): Result<Post?> = runCatching {
        val actorId = authenticatedSession().userId.requireIosNeighborhoodIdentifier()
        val targetId = postId.requireIosNeighborhoodIdentifier()
        feedTransport.mutate(
            table = "community_comments",
            method = "POST",
            body = "{\"post_id\":${targetId.toIosNeighborhoodJsonString()},\"profile_id\":${actorId.toIosNeighborhoodJsonString()},\"body\":${comment.message.toIosNeighborhoodJsonString()}}",
        ).getOrThrow()
        RemoteFeedReadRepository(feedTransport).refreshPost(targetId).getOrThrow()
    }

    override suspend fun toggleProfilePostLike(postId: String): Result<Post?> =
        IosAuthenticatedFeedRepository(
            transport = feedTransport,
            read = ReadOnlyFeedRepository(RemoteFeedReadRepository(feedTransport)),
        ).toggleLike(postId)

    override suspend fun reportProfile(userId: String): Result<Unit> = runCatching {
        val actorId = authenticatedSession().userId.requireIosNeighborhoodIdentifier()
        val targetId = userId.requireIosNeighborhoodIdentifier()
        feedTransport.reportPostRpc(
            "{\"p_actor_profile_id\":${actorId.toIosNeighborhoodJsonString()},\"p_target_type\":\"profile\",\"p_target_id\":${targetId.toIosNeighborhoodJsonString()},\"p_reason\":\"other\"}",
        ).getOrThrow()
    }

    override suspend fun setProfileBlocked(userId: String, blocked: Boolean): Result<Boolean> = runCatching {
        val actorId = authenticatedSession().userId.requireIosNeighborhoodIdentifier()
        val targetId = userId.requireIosNeighborhoodIdentifier()
        require(actorId != targetId) { "ios_communities_block_self" }
        feedTransport.profileModerationRpc(
            blocked = blocked,
            body = "{\"p_actor_profile_id\":${actorId.toIosNeighborhoodJsonString()},\"p_profile_id\":${targetId.toIosNeighborhoodJsonString()}}",
        ).getOrThrow()
        blocked
    }

    override suspend fun openPrivateChat(userId: String): Result<String> = runCatching {
        require(userId.matches(IosNeighborhoodIdentifier)) { "ios_communities_profile_id_invalid" }
        authenticatedSession()
        chatRepository.cachedPrivateConversationId(userId)
            ?: chatRepository.openPrivateConversation(userId).getOrThrow()
    }

    override suspend fun isCurrentUserAdmin(): Boolean = runCatching {
        val session = authenticatedSession()
        loadProfiles(listOf(session.userId)).firstOrNull()?.isAdmin == true
    }.getOrDefault(false)

    override suspend fun setUserRoles(
        userId: String,
        isAdmin: Boolean,
        isOfficial: Boolean,
    ): Result<NeighborhoodUser> = runCatching {
        check(isCurrentUserAdmin()) { "ios_communities_admin_required" }
        val targetId = userId.requireIosNeighborhoodIdentifier()
        feedTransport.mutate(
            table = "community_profiles",
            method = "PATCH",
            query = mapOf("id" to "eq.$targetId"),
            body = "{\"is_admin\":$isAdmin,\"is_official\":$isOfficial}",
        ).getOrThrow()
        loadProfiles(listOf(targetId)).firstOrNull() ?: error("ios_communities_profile_not_found")
    }

    override suspend fun getCachedUserProfile(userId: String, maxAgeMillis: Long?): CommunityUserProfile? {
        val cached = profileCache[userId] ?: return null
        if (!isCommunityProfileCacheUsable(cached.cachedAtMillis, iosCommunityNowMillis(), maxAgeMillis)) return null
        return cached.profile
    }

    override suspend fun cacheUserProfile(profile: CommunityUserProfile) {
        profileCache[profile.user.id] = IosCachedCommunityProfile(profile, iosCommunityNowMillis())
    }

    override fun observeUserProfile(userId: String): Flow<Result<CommunityUserProfile>> = flow {
        emit(getUserProfile(userId))
    }

    override suspend fun getUserProfile(userId: String): Result<CommunityUserProfile> = runCatching {
        val targetId = userId.requireIosNeighborhoodIdentifier()
        val user = loadProfiles(listOf(targetId)).firstOrNull() ?: error("ios_communities_profile_not_found")
        val followers = loadFollows(followedId = targetId)
        val following = loadFollows(followerId = targetId)
        val relatedIds = (followers.map(IosCommunityFollow::followerId) + following.map(IosCommunityFollow::followedId)).distinct()
        val related = loadProfiles(relatedIds).associateBy(NeighborhoodUser::id)
        val actorFollowing = authSession?.currentSession()?.userId?.let { actorId ->
            loadFollows(followerId = actorId).map(IosCommunityFollow::followedId).toSet()
        }.orEmpty()
        val posts = RemoteFeedReadRepository(
            IosFeedReadTransport(feedConfiguration, authSession, targetId),
        ).loadOlderFeedPage(beforeCreatedAt = null, limit = ProfilePostLimit).getOrThrow()
        val actorId = authSession?.currentSession()?.userId
        val attachments = actorId?.let { loadSharedAttachments(it, targetId, user.displayName) }.orEmpty()
        val enriched = user.copy(
            isFollowing = targetId in actorFollowing,
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
            isBlockedByCurrentUser = authSession?.currentSession()?.userId?.let { actorId ->
                loadProfileBlocks(actorId, targetId).isNotEmpty()
            } ?: false,
        ).also { cacheUserProfile(it) }
    }

    private suspend fun loadCommunities(): List<NeighborhoodCommunity> {
        val walls = loadWalls()
        val grouped = loadProfiles()
            .filter { it.neighborhood.isNotBlank() }
            .groupBy { it.neighborhood.normalizedCommunityKey() }
        val keys = (grouped.keys + walls.flatMap(IosCommunityWallStats::communityKeys)).distinct()
        return keys.map { key ->
            val wall = wallsByKey[key]
            val users = grouped[key].orEmpty().distinctBy(NeighborhoodUser::id).sortedBy { it.displayName.lowercase() }
            val name = wall?.name?.takeIf(String::isNotBlank)
                ?: users.firstOrNull()?.neighborhood?.takeIf(String::isNotBlank)
                ?: key
            NeighborhoodCommunity(
                name = name,
                users = users,
                conversationId = chatRepository.cachedCommunityConversationId(name),
                lastMessagePreview = null,
                lastMessageAtMillis = null,
                messageCount = 0,
                wallId = wall?.id,
            )
        }.distinctByCommunityIdentity().sortedBy { it.name.lowercase() }
    }

    private suspend fun loadProfiles(ids: List<String>? = null): List<NeighborhoodUser> = rows(
        table = "community_profiles",
        query = buildMap {
            put("select", ProfileSelect)
            put("order", "display_name.asc")
            put("limit", DirectoryLimit.toString())
            ids?.takeIf { it.isNotEmpty() }?.let { put("id", it.toIosNeighborhoodInFilter()) }
        },
    ).map(Map<*, *>::toIosNeighborhoodUser)

    private suspend fun loadFollows(
        followerId: String? = null,
        followedId: String? = null,
    ): List<IosCommunityFollow> = rows(
        table = "community_profile_follows",
        query = buildMap {
            put("select", "id,follower_profile_id,followed_profile_id,created_at")
            followerId?.let { put("follower_profile_id", "eq.${it.requireIosNeighborhoodIdentifier()}") }
            followedId?.let { put("followed_profile_id", "eq.${it.requireIosNeighborhoodIdentifier()}") }
            put("limit", DirectoryLimit.toString())
        },
    ).map { row ->
        IosCommunityFollow(
            followerId = row.requiredIosNeighborhoodString("follower_profile_id"),
            followedId = row.requiredIosNeighborhoodString("followed_profile_id"),
        )
    }

    private suspend fun loadProfileBlocks(actorId: String, targetId: String): List<Map<*, *>> = rows(
        table = "chat_profile_blocks",
        query = mapOf(
            "select" to "id",
            "thread_id" to "is.null",
            "blocker_profile_id" to "eq.${actorId.requireIosNeighborhoodIdentifier()}",
            "blocked_profile_id" to "eq.${targetId.requireIosNeighborhoodIdentifier()}",
            "limit" to "1",
        ),
    )

    private suspend fun loadSharedAttachments(
        actorId: String,
        targetId: String,
        defaultSenderName: String,
    ): List<ProfileAttachment> {
        val payload = feedTransport.sharedAttachmentsRpc(
            "{\"p_actor_profile_id\":${actorId.requireIosNeighborhoodIdentifier().toIosNeighborhoodJsonString()}," +
                "\"p_peer_profile_id\":${targetId.requireIosNeighborhoodIdentifier().toIosNeighborhoodJsonString()}," +
                "\"p_limit\":120,\"p_offset\":0}",
        ).getOrThrow()
        return (payload["files"] as? List<*>).orEmpty()
            .mapNotNull { (it as? Map<*, *>)?.toProfileAttachment(defaultSenderName) }
            .distinctBy { "${it.uri.substringBefore('?').lowercase()}|${it.name.lowercase()}" }
            .sortedByDescending { it.sentAtMillis ?: 0L }
    }

    private suspend fun loadWalls(): List<IosCommunityWallStats> {
        val walls = rows(
            table = "community_walls_stats",
            query = mapOf(
                "select" to WallStatsSelect,
                "is_active" to "eq.true",
                "order" to "sort_order.asc,chat_last_at.desc,created_at.desc",
                "limit" to WallLimit.toString(),
            ),
        ).map(Map<*, *>::toIosCommunityWallStats)
        wallsByKey = walls.flatMap { wall -> wall.communityKeys().map { key -> key to wall } }.toMap()
        return walls
    }

    private suspend fun resolveCommunityWall(name: String): IosCommunityWallStats? {
        if (wallsByKey.isEmpty()) loadWalls()
        return wallsByKey[name.normalizedCommunityKey()]
    }

    private suspend fun rows(table: String, query: Map<String, String>): List<Map<*, *>> {
        val baseUrl = configuration.supabaseUrl.trim().trimEnd('/').takeIf(String::isNotEmpty)
            ?: error("ios_communities_supabase_url_missing")
        val publishableKey = configuration.supabasePublishableKey.trim().takeIf(String::isNotEmpty)
            ?: error("ios_communities_supabase_publishable_key_missing")
        val session = authSession?.currentSession()
        val url = NSURL(string = "$baseUrl/rest/v1/$table${query.toIosNeighborhoodQueryString()}")
            ?: error("ios_communities_url_invalid")
        val requestConfiguration = NSURLSessionConfiguration.ephemeralSessionConfiguration().apply {
            HTTPAdditionalHeaders = mapOf(
                "apikey" to publishableKey,
                "Authorization" to "Bearer ${session?.bearerToken?.takeIf(String::isNotBlank) ?: publishableKey}",
                "Accept" to "application/json",
            )
        }
        val data = requestConfiguration.iosNeighborhoodData(url)
        val root = NSJSONSerialization.JSONObjectWithData(data, options = 0u, error = null) as? List<*>
            ?: error("ios_communities_response_not_array")
        return root.mapIndexed { index, row ->
            row as? Map<*, *> ?: error("ios_communities_response_row_${index}_invalid")
        }
    }

    private suspend fun authenticatedSession() = authSession?.currentSession()
        ?.takeIf { it.bearerToken.isNotBlank() && it.userId.isNotBlank() }
        ?: error("ios_communities_session_missing")

    private companion object {
        const val DirectoryLimit = 500
        const val WallLimit = 250
        const val ProfilePostLimit = 200
        const val ProfileSelect = "id,display_name,phone,country_code,phone_local,barrio,neighborhood,telefono,nombre,avatar_url,avatar,followers_count,following_count,is_admin,is_official"
        const val WallStatsSelect = "id,slug,name,normalized_name"
    }
}

/** Small iOS composition factory; UIKit owns navigation and system-only affordances. */
class IosNeighborhoodsRuntimeBootstrap(
    configuration: IosNeighborhoodsRuntimeConfiguration,
    private val authSession: IosRenewableAuthSession?,
    chatRepository: ChatRepository,
) {
    val repository: NeighborhoodRepository = IosNeighborhoodsReadRepository(configuration, authSession, chatRepository)

    fun restoredCurrentUserId(): String? = authSession?.restoredSession()?.userId?.takeIf(String::isNotBlank)
}

/** Public composition has the same real PostgREST directory but no Keychain session. */
fun createIosPublicNeighborhoodsRuntimeBootstrap(
    configuration: IosNeighborhoodsRuntimeConfiguration,
    chatRepository: ChatRepository,
): IosNeighborhoodsRuntimeBootstrap = IosNeighborhoodsRuntimeBootstrap(
    configuration = configuration,
    authSession = null,
    chatRepository = chatRepository,
)

@OptIn(ExperimentalForeignApi::class)
private suspend fun NSURLSessionConfiguration.iosNeighborhoodData(url: NSURL): NSData = suspendCancellableCoroutine { continuation ->
    val delegate = IosNeighborhoodDataTaskDelegate(continuation)
    val session = NSURLSession.sessionWithConfiguration(this, delegate, null)
    val task = session.dataTaskWithRequest(NSURLRequest(url))
    continuation.invokeOnCancellation {
        task.cancel()
        session.invalidateAndCancel()
    }
    task.resume()
}

@OptIn(ExperimentalForeignApi::class)
private class IosNeighborhoodDataTaskDelegate(
    private val continuation: CancellableContinuation<NSData>,
) : NSObject(), NSURLSessionDataDelegateProtocol {
    private val chunks = mutableListOf<ByteArray>()

    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        if (continuation.isActive) chunks += didReceiveData.toIosNeighborhoodBytes()
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        session.finishTasksAndInvalidate()
        if (!continuation.isActive) return
        if (didCompleteWithError != null) {
            continuation.resumeWithException(IllegalStateException("ios_communities_network:${didCompleteWithError.localizedDescription}"))
            return
        }
        val status = (task.response as? NSHTTPURLResponse)?.statusCode?.toInt()
        if (status == null || status !in 200..299) {
            continuation.resumeWithException(IllegalStateException("ios_communities_http_${status ?: "unknown"}"))
            return
        }
        continuation.resume(chunks.toIosNeighborhoodData())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toIosNeighborhoodBytes(): ByteArray =
    if (length == 0uL) ByteArray(0) else bytes?.readBytes(length.toInt()) ?: ByteArray(0)

@OptIn(ExperimentalForeignApi::class)
private fun List<ByteArray>.toIosNeighborhoodData(): NSData {
    return toFoundationData()
}

private fun Map<*, *>.toIosNeighborhoodUser(): NeighborhoodUser = NeighborhoodUser(
    id = requiredIosNeighborhoodString("id"),
    displayName = iosNeighborhoodString("display_name") ?: iosNeighborhoodString("nombre") ?: "Quata user",
    email = iosNeighborhoodString("phone")
        ?: listOfNotNull(iosNeighborhoodString("country_code"), iosNeighborhoodString("phone_local"))
            .joinToString("").takeIf(String::isNotBlank).orEmpty(),
    neighborhood = iosNeighborhoodString("neighborhood") ?: iosNeighborhoodString("barrio").orEmpty(),
    avatarUrl = iosNeighborhoodString("avatar_url") ?: iosNeighborhoodString("avatar"),
    isAdmin = iosNeighborhoodBoolean("is_admin"),
    isOfficial = iosNeighborhoodBoolean("is_official"),
    followersCount = iosNeighborhoodInt("followers_count"),
    followingCount = iosNeighborhoodInt("following_count"),
)

private data class IosCommunityWallStats(
    val id: String,
    val slug: String?,
    val name: String?,
    val normalizedName: String?,
)

private data class IosCommunityFollow(val followerId: String, val followedId: String)

private fun Map<*, *>.toIosCommunityWallStats(): IosCommunityWallStats = IosCommunityWallStats(
    id = requiredIosNeighborhoodString("id"),
    slug = iosNeighborhoodString("slug"),
    name = iosNeighborhoodString("name"),
    normalizedName = iosNeighborhoodString("normalized_name"),
)

private fun IosCommunityWallStats.communityKeys(): Set<String> =
    listOf(slug, name, normalizedName)
        .mapNotNull { it?.normalizedCommunityKey()?.takeIf(String::isNotBlank) }
        .toSet()

private fun Map<*, *>.requiredIosNeighborhoodString(name: String): String =
    iosNeighborhoodString(name) ?: error("ios_communities_response_missing_$name")

private fun Map<*, *>.iosNeighborhoodString(name: String): String? = this[name]
    ?.takeUnless { it is NSNull }
    ?.toString()
    ?.takeIf(String::isNotBlank)

private fun Map<*, *>.iosNeighborhoodBoolean(name: String): Boolean = when (iosNeighborhoodString(name)?.lowercase()) {
    "true", "1", "yes" -> true
    else -> false
}

private fun Map<*, *>.iosNeighborhoodInt(name: String): Int = iosNeighborhoodString(name)?.toIntOrNull() ?: 0

private fun Map<*, *>.toProfileAttachment(defaultSenderName: String): ProfileAttachment? {
    val thumbnail = this["thumb"] as? Map<*, *>
    val uri = iosNeighborhoodString("url")
        ?: iosNeighborhoodString("file_url")
        ?: thumbnail?.iosNeighborhoodString("url")
        ?: iosNeighborhoodString("thumb")
        ?: return null
    val name = iosNeighborhoodString("name")
        ?: iosNeighborhoodString("file_name")
        ?: uri.substringBefore('?').substringAfterLast('/').ifBlank { "file" }
    return ProfileAttachment(
        id = "sb:${iosNeighborhoodString("id") ?: uri.hashCode()}",
        name = name,
        uri = uri,
        mimeType = iosNeighborhoodString("mime_type"),
        sentAtMillis = iosNeighborhoodString("created_at_millis")?.toLongOrNull(),
        senderName = iosNeighborhoodString("sender_name")?.takeIf(String::isNotBlank) ?: defaultSenderName,
    )
}

private fun Collection<String>.toIosNeighborhoodInFilter(): String = "in.(${distinct().joinToString(",") {
    require(it.matches(IosNeighborhoodIdentifier)) { "ios_communities_profile_id_invalid" }
    it
}})"

private fun Map<String, String>.toIosNeighborhoodQueryString(): String = entries.joinToString(prefix = "?", separator = "&") { (key, value) ->
    "${key.toIosNeighborhoodQueryComponent()}=${value.toIosNeighborhoodQueryComponent()}"
}

private fun String.toIosNeighborhoodQueryComponent(): String = encodeToByteArray().joinToString("") { byte ->
    val value = byte.toInt() and 0xff
    if ((value in 'a'.code..'z'.code) || (value in 'A'.code..'Z'.code) || (value in '0'.code..'9'.code) || value in intArrayOf('-'.code, '.'.code, '_'.code, '~'.code)) {
        value.toChar().toString()
    } else "%${value.toString(16).padStart(2, '0').uppercase()}"
}

private fun String.normalizedCommunityKey(): String = trim().lowercase().replace(Regex("\\s+"), " ")

private data class IosCachedCommunityProfile(
    val profile: CommunityUserProfile,
    val cachedAtMillis: Long,
)

@OptIn(kotlin.time.ExperimentalTime::class)
private fun iosCommunityNowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

private fun String.requireIosNeighborhoodIdentifier(): String {
    require(matches(IosNeighborhoodIdentifier)) { "ios_communities_identifier_invalid" }
    return this
}

private fun String.toIosNeighborhoodJsonString(): String = buildString {
    append('"')
    for (char in this@toIosNeighborhoodJsonString) append(if (char == '"' || char == '\\') "\\$char" else char)
    append('"')
}

private val IosNeighborhoodIdentifier = Regex("[A-Za-z0-9_-]+")
