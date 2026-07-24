package com.quata.web

import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.feature.feed.data.FeedRemoteComment
import com.quata.feature.feed.data.FeedRemoteLike
import com.quata.feature.feed.data.FeedRemotePost
import com.quata.feature.feed.data.FeedRemoteProfile
import com.quata.feature.feed.data.buildFeedDomainPosts
import com.quata.feature.feed.data.feedRemoteProfileIds
import com.quata.feature.feed.data.toFeedDomainUser
import com.quata.feature.feed.domain.FeedRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Browser FeedRepository backed by the existing RLS-protected PostgREST tables.
 *
 * It uses polling rather than Supabase Realtime intentionally: callers receive an initial result
 * followed by periodic GET snapshots. Writes stay unavailable until the browser mutation flows
 * have their own reviewed API and UX.
 */
class WebFeedRepository(
    private val client: WebPostgrestClient,
    private val authRepository: WebAuthRepository,
    private val pollIntervalMillis: Long = DefaultPollIntervalMillis,
) : FeedRepository {
    override fun observeFeed(): Flow<Result<List<Post>>> = flow {
        while (currentCoroutineContext().isActive) {
            emit(loadFeed(limit = FeedPageSize))
            delay(pollIntervalMillis.coerceAtLeast(MinimumPollIntervalMillis))
        }
    }

    override suspend fun getFeed(): Result<List<Post>> = loadFeed(limit = FeedPageSize)

    /** PostgREST GET has no browser cache layer, so refresh is an immediate fresh GET snapshot. */
    override suspend fun refreshFeed(): Result<List<Post>> = loadFeed(limit = FeedPageSize)

    override suspend fun loadOlderFeedPage(beforeCreatedAt: String?, limit: Int): Result<List<Post>> =
        loadFeed(
            limit = limit.coerceAtLeast(1),
            createdBefore = beforeCreatedAt?.takeIf(String::isNotBlank),
        )

    override suspend fun refreshCurrentUser(): Result<User?> = runCatching {
        val userId = authRepository.restoreLocalSession()?.userId ?: return@runCatching null
        loadProfiles(listOf(userId)).firstOrNull()?.toFeedDomainUser()
    }

    override suspend fun refreshAuthor(userId: String): Result<User?> = runCatching {
        if (userId.isBlank()) return@runCatching null
        loadProfiles(listOf(userId)).firstOrNull()?.toFeedDomainUser()
    }

    override suspend fun refreshPost(postId: String): Result<Post?> = runCatching {
        if (postId.isBlank()) return@runCatching null
        loadFeed(limit = 1, postId = postId).getOrThrow().firstOrNull()
    }

    override suspend fun toggleLike(postId: String): Result<Post?> = unsupportedMutation()

    override suspend fun reportPost(postId: String): Result<Post?> = unsupportedMutation()

    override suspend fun addComment(postId: String, comment: PostComment): Result<Post?> = unsupportedMutation()

    override suspend fun deletePost(postId: String): Result<Unit> = unsupportedMutation()

    private suspend fun loadFeed(
        limit: Int,
        createdBefore: String? = null,
        postId: String? = null,
    ): Result<List<Post>> = runCatching {
        val postQuery = buildMap {
            put("select", PostSelect)
            put("order", "created_at.desc")
            createdBefore?.let { put("created_at", "lt.$it") }
            postId?.let { put("id", "eq.${it.requirePostgrestIdentifier()}") }
        }
        val posts = client.rows("community_posts", postQuery, limit).map(JsonObject::toFeedRemotePost)
        if (posts.isEmpty()) return@runCatching emptyList()

        val postIds = posts.map(FeedRemotePost::id)
        val comments = client.rows(
            table = "community_comments",
            query = mapOf(
                "select" to CommentSelect,
                "post_id" to postIds.toPostgrestInFilter(),
                "order" to "created_at.asc",
            ),
        ).map(JsonObject::toFeedRemoteComment)
        val likes = client.rows(
            table = "community_post_likes",
            query = mapOf(
                "select" to LikeSelect,
                "post_id" to postIds.toPostgrestInFilter(),
            ),
        ).map(JsonObject::toFeedRemoteLike)
        val profiles = loadProfiles(feedRemoteProfileIds(posts, comments, likes))
        buildFeedDomainPosts(
            posts = posts,
            comments = comments,
            likes = likes,
            profiles = profiles,
            currentUserId = authRepository.restoreLocalSession()?.userId,
        )
    }

    private suspend fun loadProfiles(ids: Collection<String>): List<FeedRemoteProfile> {
        if (ids.isEmpty()) return emptyList()
        return client.rows(
            table = "community_profiles",
            query = mapOf(
                "select" to ProfileSelect,
                "id" to ids.toPostgrestInFilter(),
            ),
        ).map(JsonObject::toFeedRemoteProfile)
    }

    private suspend fun WebPostgrestClient.rows(
        table: String,
        query: Map<String, String>,
        limit: Int? = null,
    ): List<JsonObject> = when (val result = get(table = table, query = query, limit = limit)) {
        is WebPostgrestResult.Success -> Json.parseToJsonElement(result.body).jsonArray.map { it.jsonObject }
        is WebPostgrestResult.Failure -> throw WebPostgrestReadException(result)
    }

    private fun Collection<String>.toPostgrestInFilter(): String =
        "in.(${distinct().joinToString(",") { it.requirePostgrestIdentifier() }})"

    private fun String.requirePostgrestIdentifier(): String {
        require(matches(PostgrestIdentifier)) { "web_feed_invalid_postgrest_identifier" }
        return this
    }

    private fun <T> unsupportedMutation(): Result<T> =
        Result.failure(UnsupportedOperationException("web_feed_mutation_not_implemented"))

    private companion object {
        const val FeedPageSize = 50
        const val DefaultPollIntervalMillis = 30_000L
        const val MinimumPollIntervalMillis = 5_000L
        const val PostSelect = "id,wall_id,profile_id,body,image_url,video_url,created_at,community_id,author_id,content"
        const val CommentSelect = "id,post_id,profile_id,body,created_at"
        const val LikeSelect = "id,post_id,profile_id,created_at"
        // Feed cards need identity/display fields only; do not fetch phone or relationship data.
        const val ProfileSelect = "id,display_name,barrio,neighborhood,nombre,avatar_url,avatar,is_admin,is_official"
        val PostgrestIdentifier = Regex("[A-Za-z0-9_-]+")
    }
}

/** Keeps the PostgREST/RLS cause available to the caller instead of flattening it to null. */
class WebPostgrestReadException(val failure: WebPostgrestResult.Failure) :
    IllegalStateException("web_postgrest_${failure.kind.name.lowercase()}:${failure.reason}")

private fun JsonObject.toFeedRemotePost(): FeedRemotePost = FeedRemotePost(
    id = requiredString("id"),
    profileId = stringOrNull("profile_id"),
    authorId = stringOrNull("author_id"),
    body = stringOrNull("body"),
    content = stringOrNull("content"),
    imageUrl = stringOrNull("image_url"),
    videoUrl = stringOrNull("video_url"),
    createdAt = stringOrNull("created_at"),
)

private fun JsonObject.toFeedRemoteComment(): FeedRemoteComment = FeedRemoteComment(
    id = requiredString("id"),
    postId = stringOrNull("post_id"),
    profileId = stringOrNull("profile_id"),
    body = stringOrNull("body"),
    createdAt = stringOrNull("created_at"),
)

private fun JsonObject.toFeedRemoteLike(): FeedRemoteLike = FeedRemoteLike(
    postId = stringOrNull("post_id"),
    profileId = stringOrNull("profile_id"),
)

private fun JsonObject.toFeedRemoteProfile(): FeedRemoteProfile = FeedRemoteProfile(
    id = requiredString("id"),
    displayName = stringOrNull("display_name"),
    fallbackName = stringOrNull("nombre"),
    countryCode = stringOrNull("country_code"),
    phoneLocal = stringOrNull("phone_local"),
    neighborhood = stringOrNull("neighborhood"),
    barrio = stringOrNull("barrio"),
    avatarUrl = stringOrNull("avatar_url"),
    avatar = stringOrNull("avatar"),
    isAdmin = stringOrNull("is_admin") == "true",
    isOfficial = stringOrNull("is_official") == "true",
)

private fun JsonObject.requiredString(name: String): String =
    stringOrNull(name) ?: error("web_feed_response_missing_$name")

private fun JsonObject.stringOrNull(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
