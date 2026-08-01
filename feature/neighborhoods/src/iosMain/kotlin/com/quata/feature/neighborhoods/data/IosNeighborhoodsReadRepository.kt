@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.quata.feature.neighborhoods.data

import com.quata.core.session.IosRenewableAuthSession
import com.quata.core.data.toFoundationData
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.feature.neighborhoods.domain.FollowUserResult
import com.quata.feature.neighborhoods.domain.NeighborhoodCommunity
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import com.quata.feature.neighborhoods.domain.NeighborhoodWallSnapshot
import com.quata.feature.neighborhoods.domain.ProfileAttachment
import com.quata.feature.neighborhoods.domain.mergeNeighborhoodDirectory
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
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURLRequestUseProtocolCachePolicy
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
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
 * iOS adapter for the common Communities directory.
 *
 * Directory/member reads use the publishable key anonymously. Private actions require the same
 * renewable Keychain session and chat/table contracts as Android; RLS-001 is operational debt,
 * never a reason for a client-side fake success or a disabled signed-in action.
 */
@OptIn(ExperimentalForeignApi::class)
class IosNeighborhoodsReadRepository(
    private val configuration: IosNeighborhoodsRuntimeConfiguration,
    private val authSession: IosRenewableAuthSession,
    private val chatRepository: ChatRepository,
) : NeighborhoodRepository {
    private val profileCache = mutableMapOf<String, Pair<CommunityUserProfile, Long>>()

    override fun observeCommunities(): Flow<List<NeighborhoodCommunity>> = flow {
        emit(loadCommunities())
    }

    override suspend fun openNeighborhoodChat(neighborhood: String): Result<String> = runCatching {
        val cleanNeighborhood = neighborhood.trim().takeIf(String::isNotEmpty)
            ?: error("ios_communities_neighborhood_missing")
        val session = authenticatedSession()
        chatRepository.cachedCommunityConversationId(cleanNeighborhood)
            ?: chatRepository.openCommunityConversation(
                communityId = loadWalls().firstOrNull { it.name.normalizedCommunityKey() == cleanNeighborhood.normalizedCommunityKey() || it.normalizedName?.normalizedCommunityKey() == cleanNeighborhood.normalizedCommunityKey() }?.id
                    ?: cleanNeighborhood.normalizedCommunityKey(),
                title = cleanNeighborhood,
                participantIds = listOf(session.userId),
            ).getOrThrow()
    }

    override suspend fun toggleFollowUser(userId: String): Result<FollowUserResult> = runCatching {
        require(userId.matches(IosNeighborhoodIdentifier)) { "ios_communities_profile_id_invalid" }
        val session = authenticatedSession()
        require(userId != session.userId) { "ios_communities_cannot_follow_self" }
        val existing = rows(
            table = "community_profile_follows",
            query = mapOf("select" to "id", "follower_profile_id" to "eq.${session.userId}", "followed_profile_id" to "eq.$userId", "limit" to "1"),
            requireSession = true,
        ).firstOrNull()
        val following = if (existing == null) {
            mutate("POST", "community_profile_follows", "{\"follower_profile_id\":\"${session.userId}\",\"followed_profile_id\":\"$userId\"}")
            true
        } else {
            mutate("DELETE", "community_profile_follows?id=eq.${existing.requiredIosNeighborhoodString("id")}", null)
            false
        }
        val current = loadProfiles(listOf(session.userId), requireSession = true).firstOrNull()
            ?: NeighborhoodUser(session.userId, "Usuario", "", "")
        FollowUserResult(userId, following, current)
    }

    override suspend fun reportPost(postId: String): Result<Unit> = reportUgc("community_post", postId)

    override suspend fun reportProfile(profileId: String): Result<Unit> = reportUgc("profile", profileId)

    override suspend fun blockProfile(profileId: String): Result<Unit> = runCatching {
        require(profileId.matches(IosNeighborhoodIdentifier)) { "ios_communities_profile_id_invalid" }
        val actor = authenticatedSession().userId
        mutate("POST", "rpc/quata_profile_block", iosNeighborhoodBlockPayload(actor, profileId))
        Unit
    }

    private suspend fun reportUgc(targetType: String, targetId: String): Result<Unit> = runCatching {
        require(targetId.matches(IosNeighborhoodIdentifier)) { "ios_communities_target_id_invalid" }
        val actor = authenticatedSession().userId
        mutate("POST", "rpc/quata_ugc_report", iosNeighborhoodReportPayload(actor, targetType, targetId))
        Unit
    }

    override suspend fun openPrivateChat(userId: String): Result<String> = runCatching {
        require(userId.matches(IosNeighborhoodIdentifier)) { "ios_communities_profile_id_invalid" }
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
        require(isCurrentUserAdmin()) { "ios_communities_admin_required" }
        mutate("PATCH", "community_profiles?id=eq.$userId", "{\"is_admin\":$isAdmin,\"is_official\":$isOfficial}")
        loadProfiles(listOf(userId), requireSession = true).firstOrNull() ?: error("ios_communities_profile_not_found")
    }

    override suspend fun getCachedUserProfile(userId: String, maxAgeMillis: Long?): CommunityUserProfile? =
        profileCache[userId]?.takeIf { cached -> maxAgeMillis == null || kotlin.time.Clock.System.now().toEpochMilliseconds() - cached.second <= maxAgeMillis }?.first

    override suspend fun cacheUserProfile(profile: CommunityUserProfile) {
        profileCache[profile.user.id] = profile to kotlin.time.Clock.System.now().toEpochMilliseconds()
    }

    override fun observeUserProfile(userId: String): Flow<Result<CommunityUserProfile>> = flow {
        emit(getUserProfile(userId))
    }

    override suspend fun getUserProfile(userId: String): Result<CommunityUserProfile> = runCatching {
        require(userId.matches(IosNeighborhoodIdentifier)) { "ios_communities_profile_id_invalid" }
        val user = loadProfiles(listOf(userId)).firstOrNull() ?: error("ios_communities_profile_not_found")
        val basePosts = rows(
            table = "community_posts",
            query = mapOf("select" to PostSelect, "profile_id" to "eq.$userId", "order" to "created_at.desc", "limit" to ProfilePostsLimit.toString()),
        ).map { row -> row.toIosCommunityProfilePost(user) }
        val postIds = basePosts.map { it.id }
        val comments = if (postIds.isEmpty()) emptyList() else rows("community_comments", mapOf("select" to "id,post_id,profile_id,body,created_at", "post_id" to postIds.toIosNeighborhoodInFilter(), "limit" to "500"))
        val likes = if (postIds.isEmpty()) emptyList() else rows("community_post_likes", mapOf("select" to "id,post_id,profile_id", "post_id" to postIds.toIosNeighborhoodInFilter(), "limit" to "1000"))
        val interactionIds = (comments + likes).mapNotNull { it.iosNeighborhoodString("profile_id") }.distinct()
        val interactionProfiles = if (interactionIds.isEmpty()) emptyMap() else loadProfiles(interactionIds).associateBy { it.id }
        val signedInId = authSession.currentSession()?.userId
        val posts = basePosts.map { post ->
            val postComments = comments.filter { it.iosNeighborhoodString("post_id") == post.id }.map { row ->
                val authorId = row.iosNeighborhoodString("profile_id")
                PostComment(row.iosNeighborhoodString("id") ?: "comment:${post.id}", interactionProfiles[authorId]?.displayName ?: "Usuario", row.iosNeighborhoodString("body").orEmpty(), row.iosNeighborhoodString("created_at").orEmpty(), authorId = authorId)
            }
            val postLikes = likes.filter { it.iosNeighborhoodString("post_id") == post.id }
            post.copy(comments = postComments, likesCount = postLikes.size, isLikedByCurrentUser = signedInId != null && postLikes.any { it.iosNeighborhoodString("profile_id") == signedInId })
        }
        val followers = profileRelations("followed_profile_id", userId)
        val following = profileRelations("follower_profile_id", userId)
        val relatedIds = (followers.mapNotNull { it.iosNeighborhoodString("follower_profile_id") } +
            following.mapNotNull { it.iosNeighborhoodString("followed_profile_id") }).distinct()
        val related = if (relatedIds.isEmpty()) emptyMap() else loadProfiles(relatedIds).associateBy { it.id }
        // Relationship lookup remains a real request. Anonymous profile reads never invent a
        // follow state when the keychain has no valid bearer.
        val currentFollowing = authSession.currentSession()?.userId?.let { currentId ->
            profileRelations("follower_profile_id", currentId).mapNotNull { it.iosNeighborhoodString("followed_profile_id") }.toSet()
        }.orEmpty()
        val sharedAttachments = authSession.currentSession()?.userId?.let { loadSharedAttachments(it, userId, user.displayName) }.orEmpty()
        val profile = CommunityUserProfile(
            user = user.copy(
                isFollowing = userId in currentFollowing,
                followersCount = followers.size,
                followingCount = following.size,
                postsCount = posts.size,
            ),
            posts = posts,
            attachments = sharedAttachments,
            followers = followers.mapNotNull { related[it.iosNeighborhoodString("follower_profile_id")] }.map { it.copy(isFollowing = it.id in currentFollowing) },
            following = following.mapNotNull { related[it.iosNeighborhoodString("followed_profile_id")] }.map { it.copy(isFollowing = it.id in currentFollowing) },
        )
        profile.also { profileCache[userId] = it to kotlin.time.Clock.System.now().toEpochMilliseconds() }
    }

    private suspend fun loadSharedAttachments(actorId: String, peerId: String, senderName: String): List<ProfileAttachment> {
        val data = mutate("POST", "rpc/quata_chat_list_shared_attachments", iosNeighborhoodSharedAttachmentsPayload(actorId, peerId))
        val root = NSJSONSerialization.JSONObjectWithData(data, options = 0u, error = null) as? Map<*, *> ?: return emptyList()
        return (root["files"] as? List<*>).orEmpty().mapNotNull { raw ->
            val row = raw as? Map<*, *> ?: return@mapNotNull null
            val uri = row.iosNeighborhoodString("url") ?: row.iosNeighborhoodString("file_url") ?: return@mapNotNull null
            ProfileAttachment(
                id = "ios:${row.iosNeighborhoodString("id") ?: uri}",
                name = row.iosNeighborhoodString("name") ?: row.iosNeighborhoodString("file_name") ?: "attachment",
                uri = uri,
                mimeType = row.iosNeighborhoodString("mime_type"),
                sentAtMillis = row.iosNeighborhoodString("created_at")?.toIosNeighborhoodEpochMillis(),
                senderName = row.iosNeighborhoodString("sender_name") ?: senderName,
            )
        }
    }

    private suspend fun profileRelations(column: String, profileId: String): List<Map<*, *>> = rows(
        table = "community_profile_follows",
        query = mapOf("select" to "follower_profile_id,followed_profile_id", column to "eq.$profileId", "limit" to DirectoryLimit.toString()),
    )

    private suspend fun loadCommunities(): List<NeighborhoodCommunity> {
        val profiles = loadProfiles()
        val walls = loadWalls()
        return mergeNeighborhoodDirectory(profiles, walls.map { NeighborhoodWallSnapshot(it.id, it.name, it.normalizedName, it.chatCount, it.chatLastAtMillis) })
            .map { community -> community.copy(conversationId = chatRepository.cachedCommunityConversationId(community.name) ?: community.conversationId) }
    }

    private suspend fun loadWalls(): List<IosCommunityWall> = rows(
        table = "community_walls_stats",
        query = mapOf("select" to WallStatsSelect, "is_active" to "eq.true", "order" to "sort_order.asc,chat_last_at.desc,created_at.desc", "limit" to DirectoryLimit.toString()),
    ).map(Map<*, *>::toIosCommunityWall)

    private suspend fun loadProfiles(ids: List<String>? = null, requireSession: Boolean = false): List<NeighborhoodUser> = rows(
        query = buildMap {
            put("select", ProfileSelect)
            put("order", "display_name.asc")
            put("limit", DirectoryLimit.toString())
            ids?.takeIf { it.isNotEmpty() }?.let { put("id", it.toIosNeighborhoodInFilter()) }
        },
        requireSession = requireSession,
    ).map(Map<*, *>::toIosNeighborhoodUser)

    private suspend fun rows(table: String = "community_profiles", query: Map<String, String>, requireSession: Boolean = false): List<Map<*, *>> {
        val baseUrl = configuration.supabaseUrl.trim().trimEnd('/').takeIf(String::isNotEmpty)
            ?: error("ios_communities_supabase_url_missing")
        val publishableKey = configuration.supabasePublishableKey.trim().takeIf(String::isNotEmpty)
            ?: error("ios_communities_supabase_publishable_key_missing")
        val session = if (requireSession) authenticatedSession() else null
        val url = NSURL(string = "$baseUrl/rest/v1/$table${query.toIosNeighborhoodQueryString()}")
            ?: error("ios_communities_url_invalid")
        val requestConfiguration = NSURLSessionConfiguration.ephemeralSessionConfiguration().apply {
            HTTPAdditionalHeaders = buildMap {
                put("apikey", publishableKey)
                session?.let { put("Authorization", "Bearer ${it.bearerToken}") }
                put("Accept", "application/json")
            }
        }
        val data = requestConfiguration.iosNeighborhoodData(url)
        val root = NSJSONSerialization.JSONObjectWithData(data, options = 0u, error = null) as? List<*>
            ?: error("ios_communities_response_not_array")
        return root.mapIndexed { index, row ->
            row as? Map<*, *> ?: error("ios_communities_response_row_${index}_invalid")
        }
    }

    private suspend fun mutate(method: String, path: String, body: String?): NSData {
        val baseUrl = configuration.supabaseUrl.trim().trimEnd('/').takeIf(String::isNotEmpty) ?: error("ios_communities_supabase_url_missing")
        val key = configuration.supabasePublishableKey.trim().takeIf(String::isNotEmpty) ?: error("ios_communities_supabase_publishable_key_missing")
        val session = authenticatedSession()
        val request = NSMutableURLRequest.requestWithURL(NSURL(string = "$baseUrl/rest/v1/$path") ?: error("ios_communities_url_invalid")).apply {
            setHTTPMethod(method)
            setValue(key, "apikey")
            setValue("Bearer ${session.bearerToken}", "Authorization")
            setValue("application/json", "Content-Type")
            setValue("return=minimal", "Prefer")
            body?.let { setHTTPBody(it.encodeToByteArray().toFoundationData()) }
        }
        return NSURLSessionConfiguration.ephemeralSessionConfiguration().iosNeighborhoodData(request)
    }

    private suspend fun authenticatedSession() = authSession.currentSession()
        ?.takeIf { it.bearerToken.isNotBlank() && it.userId.isNotBlank() }
        ?: error("ios_communities_session_missing")

    private companion object {
        const val DirectoryLimit = 500
        const val ProfileSelect = "id,display_name,phone,country_code,phone_local,barrio,neighborhood,telefono,nombre,avatar_url,avatar,followers_count,following_count,is_admin,is_official"
        const val PostSelect = "id,profile_id,body,content,image_url,video_url,created_at"
        const val ProfilePostsLimit = 120
        const val WallStatsSelect = "id,slug,name,normalized_name,chat_count,chat_last_at"
    }
}

private data class IosCommunityWall(val id: String?, val name: String, val normalizedName: String?, val chatCount: Int, val chatLastAtMillis: Long?)

private fun Map<*, *>.toIosCommunityWall(): IosCommunityWall = IosCommunityWall(
    id = iosNeighborhoodString("id") ?: iosNeighborhoodString("slug"),
    name = iosNeighborhoodString("name") ?: iosNeighborhoodString("slug") ?: error("ios_communities_wall_name_missing"),
    normalizedName = iosNeighborhoodString("normalized_name"),
    chatCount = iosNeighborhoodInt("chat_count"),
    chatLastAtMillis = iosNeighborhoodString("chat_last_at")?.let { runCatching { kotlin.time.Instant.parse(it).toEpochMilliseconds() }.getOrNull() },
)

/** Small iOS composition factory; UIKit owns navigation and system-only affordances. */
class IosNeighborhoodsRuntimeBootstrap(
    configuration: IosNeighborhoodsRuntimeConfiguration,
    private val authSession: IosRenewableAuthSession,
    chatRepository: ChatRepository,
) {
    val repository: NeighborhoodRepository = IosNeighborhoodsReadRepository(configuration, authSession, chatRepository)

    fun restoredCurrentUserId(): String? = authSession.restoredSession()?.userId?.takeIf(String::isNotBlank)
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun NSURLSessionConfiguration.iosNeighborhoodData(url: NSURL): NSData =
    iosNeighborhoodData(NSURLRequest(url))

@OptIn(ExperimentalForeignApi::class)
private suspend fun NSURLSessionConfiguration.iosNeighborhoodData(request: NSURLRequest): NSData = suspendCancellableCoroutine { continuation ->
    val delegate = IosNeighborhoodDataTaskDelegate(continuation)
    val session = NSURLSession.sessionWithConfiguration(this, delegate, null)
    val task = session.dataTaskWithRequest(request)
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

internal fun Map<*, *>.toIosCommunityProfilePost(author: NeighborhoodUser): Post = Post(
    id = requiredIosNeighborhoodString("id"),
    author = User(author.id, author.email, author.displayName, author.neighborhood, author.avatarUrl, author.isAdmin, author.isOfficial),
    text = iosNeighborhoodString("body") ?: iosNeighborhoodString("content").orEmpty(),
    imageUrl = iosNeighborhoodString("image_url"),
    videoUrl = iosNeighborhoodString("video_url"),
    createdAt = iosNeighborhoodString("created_at").orEmpty(),
)

internal fun Post.toIosProfileAttachments(): List<ProfileAttachment> = listOfNotNull(
    imageUrl?.let { ProfileAttachment("post:$id:image", "imagen", it, "image/*", createdAt.toIosNeighborhoodEpochMillis(), author.displayName) },
    videoUrl?.let { ProfileAttachment("post:$id:video", "vídeo", it, "video/*", createdAt.toIosNeighborhoodEpochMillis(), author.displayName) },
)

private fun String.toIosNeighborhoodEpochMillis(): Long? = runCatching { kotlin.time.Instant.parse(this).toEpochMilliseconds() }.getOrNull()

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

private val IosNeighborhoodIdentifier = Regex("[A-Za-z0-9_-]+")

internal fun iosNeighborhoodReportPayload(actor: String, type: String, target: String): String =
    "{\"p_actor_profile_id\":\"$actor\",\"p_target_type\":\"$type\",\"p_target_id\":\"$target\",\"p_reason\":\"other\"}"

internal fun iosNeighborhoodBlockPayload(actor: String, profile: String): String =
    "{\"p_actor_profile_id\":\"$actor\",\"p_profile_id\":\"$profile\"}"

internal fun iosNeighborhoodSharedAttachmentsPayload(actor: String, peer: String): String =
    "{\"p_actor_profile_id\":\"$actor\",\"p_peer_profile_id\":\"$peer\",\"p_limit\":120,\"p_offset\":0}"
