package com.quata.web

import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.feature.official.data.OfficialRemoteComment
import com.quata.feature.official.data.OfficialRemoteLike
import com.quata.feature.official.data.OfficialRemotePost
import com.quata.feature.official.data.OfficialRemoteProfile
import com.quata.feature.official.data.buildOfficialDomainPosts
import com.quata.feature.official.data.officialRemoteProfileIds
import com.quata.feature.official.data.toOfficialDomainUser
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialRepository
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
 * Browser implementation of the read-only Official feed contract.
 *
 * Every request uses the user's Supabase bearer token through [WebPostgrestClient]. Authentication
 * and RLS failures remain a [WebPostgrestReadException], rather than being presented as an empty
 * official feed. Browser write flows need a separately reviewed API and therefore fail explicitly.
 */
class WebOfficialRepository(
    private val client: WebPostgrestClient,
    private val authRepository: WebAuthRepository,
    private val pollIntervalMillis: Long = DefaultPollIntervalMillis,
) : OfficialRepository {
    override fun observeOfficialFeed(): Flow<Result<List<OfficialPostItem>>> = flow {
        while (currentCoroutineContext().isActive) {
            emit(loadFeed(limit = FeedPageSize))
            delay(pollIntervalMillis.coerceAtLeast(MinimumPollIntervalMillis))
        }
    }

    override suspend fun getOfficialFeed(): Result<List<OfficialPostItem>> = loadFeed(limit = FeedPageSize)

    /** Browser PostgREST requests are fresh snapshots, with no app-level cache to invalidate. */
    override suspend fun refreshOfficialFeed(): Result<List<OfficialPostItem>> = loadFeed(limit = FeedPageSize)

    override suspend fun loadOlderOfficialFeedPage(
        beforePublishedAt: String?,
        limit: Int,
    ): Result<List<OfficialPostItem>> = loadFeed(
        limit = limit.coerceAtLeast(1),
        publishedBefore = beforePublishedAt?.takeIf(String::isNotBlank),
    )

    override suspend fun getOfficialPost(postId: String): Result<OfficialPostItem?> = runCatching {
        if (postId.isBlank()) return@runCatching null
        loadFeed(limit = 1, postId = postId).getOrThrow().firstOrNull()
    }

    override suspend fun refreshCurrentUser(): Result<User?> = runCatching {
        val userId = authRepository.restoreLocalSession()?.userId ?: return@runCatching null
        loadProfiles(listOf(userId)).firstOrNull()?.toOfficialDomainUser()
    }

    override suspend fun createPost(draft: OfficialPostDraft): Result<OfficialPostItem?> = unsupportedMutation()

    override suspend fun createPosts(drafts: List<OfficialPostDraft>): Result<OfficialPostItem?> = unsupportedMutation()

    override suspend fun deletePost(postId: String): Result<Unit> = unsupportedMutation()

    override suspend fun toggleLike(postId: String): Result<OfficialPostItem?> = unsupportedMutation()

    override suspend fun addComment(postId: String, comment: PostComment): Result<OfficialPostItem?> = unsupportedMutation()

    private suspend fun loadFeed(
        limit: Int,
        publishedBefore: String? = null,
        postId: String? = null,
    ): Result<List<OfficialPostItem>> = runCatching {
        val posts = client.rows(
            table = "official_posts",
            query = buildMap {
                put("select", PostSelect)
                put("order", "published_at.desc")
                publishedBefore?.let { put("published_at", "lt.$it") }
                postId?.let { put("id", "eq.${it.requireOfficialPostgrestIdentifier()}") }
            },
            limit = limit,
        ).map(JsonObject::toOfficialRemotePost)
        if (posts.isEmpty()) return@runCatching emptyList()

        val postIds = posts.map(OfficialRemotePost::id)
        val comments = client.rows(
            table = "official_post_comments",
            query = mapOf(
                "select" to CommentSelect,
                "official_post_id" to postIds.toOfficialPostgrestInFilter(),
                "order" to "created_at.asc",
            ),
        ).map(JsonObject::toOfficialRemoteComment)
        val likes = client.rows(
            table = "official_post_likes",
            query = mapOf(
                "select" to LikeSelect,
                "official_post_id" to postIds.toOfficialPostgrestInFilter(),
            ),
        ).map(JsonObject::toOfficialRemoteLike)
        val profiles = loadProfiles(officialRemoteProfileIds(posts, comments, likes))
        buildOfficialDomainPosts(
            posts = posts,
            comments = comments,
            likes = likes,
            profiles = profiles,
            currentUserId = authRepository.restoreLocalSession()?.userId,
            defaultTitle = DefaultTitle,
            defaultCommentAuthor = DefaultCommentAuthor,
        )
    }

    private suspend fun loadProfiles(ids: Collection<String>): List<OfficialRemoteProfile> {
        if (ids.isEmpty()) return emptyList()
        return client.rows(
            table = "community_profiles",
            query = mapOf(
                "select" to ProfileSelect,
                "id" to ids.toOfficialPostgrestInFilter(),
            ),
        ).map(JsonObject::toOfficialRemoteProfile)
    }

    private suspend fun WebPostgrestClient.rows(
        table: String,
        query: Map<String, String>,
        limit: Int? = null,
    ): List<JsonObject> = when (val result = get(table = table, query = query, limit = limit)) {
        is WebPostgrestResult.Success -> Json.parseToJsonElement(result.body).jsonArray.map { it.jsonObject }
        is WebPostgrestResult.Failure -> throw WebPostgrestReadException(result)
    }

    private fun Collection<String>.toOfficialPostgrestInFilter(): String =
        "in.(${distinct().joinToString(",") { it.requireOfficialPostgrestIdentifier() }})"

    private fun String.requireOfficialPostgrestIdentifier(): String {
        require(matches(PostgrestIdentifier)) { "web_official_invalid_postgrest_identifier" }
        return this
    }

    private fun <T> unsupportedMutation(): Result<T> =
        Result.failure(UnsupportedOperationException("web_official_mutation_not_implemented"))

    private companion object {
        const val FeedPageSize = 50
        const val DefaultPollIntervalMillis = 30_000L
        const val MinimumPollIntervalMillis = 5_000L
        const val DefaultTitle = "Cuenta oficial"
        const val DefaultCommentAuthor = "Usuario"
        const val PostSelect = "id,profile_id,title,summary,post_type,content_html,read_more_label,language,translation_group_id,media_url,media_type,link_url,is_live,published_at,created_at"
        const val CommentSelect = "id,official_post_id,profile_id,body,created_at"
        const val LikeSelect = "id,official_post_id,profile_id,created_at"
        const val ProfileSelect = "id,display_name,barrio,neighborhood,nombre,avatar_url,avatar,is_admin,is_official"
        val PostgrestIdentifier = Regex("[A-Za-z0-9_-]+")
    }
}

private fun JsonObject.toOfficialRemotePost(): OfficialRemotePost = OfficialRemotePost(
    id = requiredOfficialString("id"),
    profileId = officialStringOrNull("profile_id"),
    title = officialStringOrNull("title"),
    summary = officialStringOrNull("summary"),
    postType = officialStringOrNull("post_type"),
    contentHtml = officialStringOrNull("content_html"),
    readMoreLabel = officialStringOrNull("read_more_label"),
    language = officialStringOrNull("language"),
    translationGroupId = officialStringOrNull("translation_group_id"),
    mediaUrl = officialStringOrNull("media_url"),
    mediaType = officialStringOrNull("media_type"),
    linkUrl = officialStringOrNull("link_url"),
    isLive = officialStringOrNull("is_live") == "true",
    publishedAt = officialStringOrNull("published_at"),
    createdAt = officialStringOrNull("created_at"),
)

private fun JsonObject.toOfficialRemoteComment(): OfficialRemoteComment = OfficialRemoteComment(
    id = requiredOfficialString("id"),
    postId = officialStringOrNull("official_post_id"),
    profileId = officialStringOrNull("profile_id"),
    body = officialStringOrNull("body"),
    createdAt = officialStringOrNull("created_at"),
)

private fun JsonObject.toOfficialRemoteLike(): OfficialRemoteLike = OfficialRemoteLike(
    postId = officialStringOrNull("official_post_id"),
    profileId = officialStringOrNull("profile_id"),
)

private fun JsonObject.toOfficialRemoteProfile(): OfficialRemoteProfile = OfficialRemoteProfile(
    id = requiredOfficialString("id"),
    displayName = officialStringOrNull("display_name"),
    fallbackName = officialStringOrNull("nombre"),
    neighborhood = officialStringOrNull("neighborhood"),
    barrio = officialStringOrNull("barrio"),
    avatarUrl = officialStringOrNull("avatar_url"),
    avatar = officialStringOrNull("avatar"),
    isAdmin = officialStringOrNull("is_admin") == "true",
    isOfficial = officialStringOrNull("is_official") == "true",
)

private fun JsonObject.requiredOfficialString(name: String): String =
    officialStringOrNull(name) ?: error("web_official_response_missing_$name")

private fun JsonObject.officialStringOrNull(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
