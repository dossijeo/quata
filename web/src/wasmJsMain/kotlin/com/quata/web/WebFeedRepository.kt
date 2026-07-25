package com.quata.web

import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.feature.feed.data.FeedReadTransport
import com.quata.feature.feed.data.FeedRemoteComment
import com.quata.feature.feed.data.FeedRemoteLike
import com.quata.feature.feed.data.FeedRemotePost
import com.quata.feature.feed.data.FeedRemotePostRequest
import com.quata.feature.feed.data.FeedRemoteProfile
import com.quata.feature.feed.data.RemoteFeedReadRepository
import com.quata.feature.feed.data.feedRemoteCommentFromFields
import com.quata.feature.feed.data.feedRemoteLikeFromFields
import com.quata.feature.feed.data.feedRemotePostFromFields
import com.quata.feature.feed.data.feedRemoteProfileFromFields
import com.quata.feature.feed.domain.FeedReadRepository
import com.quata.feature.feed.domain.FeedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Browser Feed repository with the common read/polling/domain-mapping implementation.
 * PostgREST remains a Web-only [FeedReadTransport]; writes stay unavailable until reviewed.
 */
class WebFeedRepository(
    client: WebPostgrestClient,
    authRepository: WebAuthRepository,
    pollIntervalMillis: Long = DefaultPollIntervalMillis,
) : FeedRepository {
    private val readRepository: FeedReadRepository = RemoteFeedReadRepository(
        transport = WebFeedReadTransport(client, authRepository),
        pollIntervalMillis = pollIntervalMillis,
    )

    override fun observeFeed(): Flow<Result<List<Post>>> = readRepository.observeFeed()
    override suspend fun getFeed(): Result<List<Post>> = readRepository.getFeed()
    override suspend fun refreshFeed(): Result<List<Post>> = readRepository.refreshFeed()
    override suspend fun loadOlderFeedPage(beforeCreatedAt: String?, limit: Int): Result<List<Post>> =
        readRepository.loadOlderFeedPage(beforeCreatedAt, limit)
    override suspend fun refreshCurrentUser(): Result<User?> = readRepository.refreshCurrentUser()
    override suspend fun refreshAuthor(userId: String): Result<User?> = readRepository.refreshAuthor(userId)
    override suspend fun refreshPost(postId: String): Result<Post?> = readRepository.refreshPost(postId)

    override suspend fun toggleLike(postId: String): Result<Post?> = unsupportedMutation()
    override suspend fun reportPost(postId: String): Result<Post?> = unsupportedMutation()
    override suspend fun addComment(postId: String, comment: PostComment): Result<Post?> = unsupportedMutation()
    override suspend fun deletePost(postId: String): Result<Unit> = unsupportedMutation()

    private fun <T> unsupportedMutation(): Result<T> =
        Result.failure(UnsupportedOperationException("web_feed_mutation_not_implemented"))

    private companion object { const val DefaultPollIntervalMillis = 30_000L }
}

private class WebFeedReadTransport(
    private val client: WebPostgrestClient,
    private val authRepository: WebAuthRepository,
) : FeedReadTransport {
    override suspend fun fetchPosts(request: FeedRemotePostRequest): Result<List<FeedRemotePost>> = runCatching {
        val query = buildMap {
            put("select", PostSelect)
            put("order", "created_at.desc")
            request.beforeCreatedAt?.let { put("created_at", "lt.$it") }
            request.postId?.let { put("id", "eq.${it.requirePostgrestIdentifier()}") }
        }
        client.rows("community_posts", query, request.limit).map(JsonObject::toFeedRemotePost)
    }

    override suspend fun fetchComments(postIds: List<String>): Result<List<FeedRemoteComment>> = runCatching {
        if (postIds.isEmpty()) emptyList() else client.rows("community_comments", mapOf(
            "select" to CommentSelect, "post_id" to postIds.toPostgrestInFilter(), "order" to "created_at.asc",
        )).map(JsonObject::toFeedRemoteComment)
    }

    override suspend fun fetchLikes(postIds: List<String>): Result<List<FeedRemoteLike>> = runCatching {
        if (postIds.isEmpty()) emptyList() else client.rows("community_post_likes", mapOf(
            "select" to LikeSelect, "post_id" to postIds.toPostgrestInFilter(),
        )).map(JsonObject::toFeedRemoteLike)
    }

    override suspend fun fetchProfiles(profileIds: List<String>): Result<List<FeedRemoteProfile>> = runCatching {
        if (profileIds.isEmpty()) emptyList() else client.rows("community_profiles", mapOf(
            "select" to ProfileSelect, "id" to profileIds.toPostgrestInFilter(),
        )).map(JsonObject::toFeedRemoteProfile)
    }

    override suspend fun currentUserId(): Result<String?> =
        Result.success(authRepository.restoreLocalSession()?.userId)
}

private suspend fun WebPostgrestClient.rows(table: String, query: Map<String, String>, limit: Int? = null): List<JsonObject> =
    when (val result = get(table = table, query = query, limit = limit)) {
        is WebPostgrestResult.Success -> Json.parseToJsonElement(result.body).jsonArray.map { it.jsonObject }
        is WebPostgrestResult.Failure -> throw WebPostgrestReadException(result)
    }

private fun Collection<String>.toPostgrestInFilter(): String =
    "in.(${distinct().joinToString(",") { it.requirePostgrestIdentifier() }})"

private fun String.requirePostgrestIdentifier(): String {
    require(matches(PostgrestIdentifier)) { "web_feed_invalid_postgrest_identifier" }
    return this
}

/** Keeps the PostgREST/RLS cause available to the caller instead of flattening it to null. */
class WebPostgrestReadException(val failure: WebPostgrestResult.Failure) :
    IllegalStateException("web_postgrest_${failure.kind.name.lowercase()}:${failure.reason}")

private fun JsonObject.toFeedRemotePost() = feedRemotePostFromFields(
    field = { name -> stringOrNull(name) },
    missingIdError = { IllegalStateException("web_feed_response_missing_id") },
)

private fun JsonObject.toFeedRemoteComment() = feedRemoteCommentFromFields(
    field = { name -> stringOrNull(name) },
    missingIdError = { IllegalStateException("web_feed_response_missing_id") },
)

private fun JsonObject.toFeedRemoteLike() = feedRemoteLikeFromFields { name -> stringOrNull(name) }
private fun JsonObject.toFeedRemoteProfile() = feedRemoteProfileFromFields(
    field = { name -> stringOrNull(name) },
    booleanField = { name -> stringOrNull(name) == "true" },
    missingIdError = { IllegalStateException("web_feed_response_missing_id") },
)

private fun JsonObject.stringOrNull(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

private const val PostSelect = "id,wall_id,profile_id,body,image_url,video_url,created_at,community_id,author_id,content"
private const val CommentSelect = "id,post_id,profile_id,body,created_at"
private const val LikeSelect = "id,post_id,profile_id,created_at"
private const val ProfileSelect = "id,display_name,barrio,neighborhood,nombre,avatar_url,avatar,is_admin,is_official"
private val PostgrestIdentifier = Regex("[A-Za-z0-9_-]+")
