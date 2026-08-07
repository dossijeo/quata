package com.quata.web

import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.feature.official.data.OfficialRemoteComment
import com.quata.feature.official.data.OfficialRemoteLike
import com.quata.feature.official.data.OfficialRemotePost
import com.quata.feature.official.data.OfficialRemoteProfile
import com.quata.feature.official.data.buildOfficialDomainPosts
import com.quata.feature.official.data.officialRemoteCommentFromWire
import com.quata.feature.official.data.officialRemoteLikeFromWire
import com.quata.feature.official.data.officialRemotePostFromWire
import com.quata.feature.official.data.officialRemoteProfileFromWire
import com.quata.feature.official.data.OfficialRemoteWireFields
import com.quata.feature.official.data.OfficialRemoteWireSchema
import com.quata.feature.official.data.officialRemoteProfileIds
import com.quata.feature.official.data.toOfficialDomainUser
import com.quata.feature.official.data.selectOfficialTranslations
import com.quata.feature.official.data.officialTranslationReadPlan
import com.quata.feature.official.data.officialCommentReportPayload
import com.quata.feature.official.data.officialLikeLookupPlan
import com.quata.feature.official.data.officialLikeInsertPlan
import com.quata.feature.official.data.officialLikeDeletePlan
import com.quata.feature.official.data.officialCommentPlan
import com.quata.feature.official.data.officialSoftDeletePlan
import com.quata.feature.official.data.officialPostCreatePlans
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

internal enum class WebOfficialReadOperation { Feed, CurrentUser }
internal fun webOfficialReadAuthMode(operation: WebOfficialReadOperation): WebPostgrestAuthMode = when (operation) {
    WebOfficialReadOperation.Feed -> WebPostgrestAuthMode.Public
    WebOfficialReadOperation.CurrentUser -> WebPostgrestAuthMode.SessionRequired
}

/**
 * Browser implementation of the public-read and authenticated-interaction Official contract.
 *
 * Its public feed reads use the configured publishable key with [WebPostgrestAuthMode.Public] and
 * deliberately omit Authorization. Private/admin reads remain [WebPostgrestAuthMode.SessionRequired]
 * and fail closed without a session. Reviewed publish/delete/like/comment/report interactions use
 * that renewable session and shared PostgREST mutation plans.
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
        loadProfiles(listOf(userId), webOfficialReadAuthMode(WebOfficialReadOperation.CurrentUser)).firstOrNull()?.toOfficialDomainUser()
    }

    override suspend fun createPost(draft: OfficialPostDraft): Result<OfficialPostItem?> = createPosts(listOf(draft))

    override suspend fun createPosts(drafts: List<OfficialPostDraft>): Result<OfficialPostItem?> = runCatching {
        val userId = authenticatedUserId().requireOfficialPostgrestIdentifier()
        val currentUser = refreshCurrentUser().getOrThrow()
        check(currentUser?.isOfficial == true) { "web_official_create_forbidden" }
        val groupId = drafts.firstNotNullOfOrNull { it.translationGroupId?.takeIf(String::isNotBlank) }
            ?: webOfficialRandomUuid()
        val plans = officialPostCreatePlans(
            profileId = userId,
            drafts = drafts,
            translationGroupId = groupId.requireOfficialPostgrestIdentifier(),
            defaultTitle = DefaultTitle,
            publishedAt = currentOfficialTimestamp(),
        )
        if (plans.isEmpty()) return@runCatching null
        val createdIds = plans.mapNotNull { plan ->
            val body = client.post(plan.table, requireNotNull(plan.body)).requireWebOfficialBody()
            Json.parseToJsonElement(body).jsonArray.firstOrNull()?.jsonObject?.requiredOfficialString("id")
        }
        createdIds.firstOrNull()?.let { getOfficialPost(it).getOrThrow() }
    }

    override suspend fun deletePost(postId: String): Result<Unit> = runCatching {
        val userId = authenticatedUserId()
        val post = getOfficialPost(postId).getOrThrow() ?: error("web_official_post_missing")
        val currentUser = refreshCurrentUser().getOrThrow()
        check(post.author.id == userId || currentUser?.isAdmin == true) { "web_official_delete_forbidden" }
        val plan = officialSoftDeletePlan(postId.requireOfficialPostgrestIdentifier(), post.translationGroupId?.requireOfficialPostgrestIdentifier(), currentOfficialTimestamp())
        client.patch(plan.table, plan.filter, plan.body!!).requireWebOfficialSuccess()
    }

    override suspend fun toggleLike(postId: String): Result<OfficialPostItem?> = runCatching {
        val userId = authenticatedUserId()
        val safePostId = postId.requireOfficialPostgrestIdentifier()
        val safeUserId = userId.requireOfficialPostgrestIdentifier()
        when (val existing = client.get(officialLikeLookupPlan(safePostId, safeUserId).table, officialLikeLookupPlan(safePostId, safeUserId).filter)) {
            is WebPostgrestResult.Success -> if (existing.body.trim() == "[]") {
                officialLikeInsertPlan(safePostId, safeUserId).let { client.post(it.table, it.body!!).requireWebOfficialSuccess() }
            } else {
                officialLikeDeletePlan(safePostId, safeUserId).let { client.delete(it.table, it.filter).requireWebOfficialSuccess() }
            }
            is WebPostgrestResult.Failure -> error("web_official_postgrest_${existing.reason}")
        }
        getOfficialPost(postId).getOrThrow()
    }

    override suspend fun addComment(postId: String, comment: PostComment): Result<OfficialPostItem?> = runCatching {
        val userId = authenticatedUserId()
        val safePostId = postId.requireOfficialPostgrestIdentifier()
        val safeUserId = userId.requireOfficialPostgrestIdentifier()
        officialCommentPlan(safePostId, safeUserId, comment).let { client.post(it.table, it.body!!).requireWebOfficialSuccess() }
        getOfficialPost(postId).getOrThrow()
    }

    override suspend fun reportComment(commentId: String): Result<Unit> = runCatching {
        val userId = authenticatedUserId().requireOfficialPostgrestIdentifier()
        val targetId = commentId.requireOfficialPostgrestIdentifier()
        client.rpc("quata_ugc_report", officialCommentReportPayload(userId, targetId)).requireWebOfficialSuccess()
    }

    private suspend fun loadFeed(
        limit: Int,
        publishedBefore: String? = null,
        postId: String? = null,
    ): Result<List<OfficialPostItem>> = runCatching {
        val translation = officialTranslationReadPlan(currentWebOfficialLanguage(), limit, postId)
        val posts = client.rows(
            table = "official_posts",
            query = buildMap {
                put("select", PostSelect)
                put("is_published", "eq.true")
                put("deleted_at", "is.null")
                put("order", "published_at.desc,created_at.desc")
                putAll(translation.filters)
                publishedBefore?.let { put("published_at", "lt.$it") }
                postId?.let { put("id", "eq.${it.requireOfficialPostgrestIdentifier()}") }
            },
            limit = translation.fetchLimit,
        ).map(JsonObject::toOfficialRemotePost).selectOfficialTranslations(currentWebOfficialLanguage())
        if (posts.isEmpty()) return@runCatching emptyList()

        val postIds = posts.map(OfficialRemotePost::id)
        val comments = client.rows(
            table = "official_post_comments",
            query = mapOf(
                "select" to CommentSelect,
                "official_post_id" to postIds.toOfficialPostgrestInFilter(),
                "deleted_at" to "is.null",
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
        val profiles = loadProfiles(officialRemoteProfileIds(posts, comments, likes), webOfficialReadAuthMode(WebOfficialReadOperation.Feed))
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

    private suspend fun loadProfiles(
        ids: Collection<String>,
        authMode: WebPostgrestAuthMode,
    ): List<OfficialRemoteProfile> {
        if (ids.isEmpty()) return emptyList()
        return client.rows(
            table = "community_profiles",
            query = mapOf(
                "select" to ProfileSelect,
                "id" to ids.toOfficialPostgrestInFilter(),
            ),
            authMode = authMode,
        ).map(JsonObject::toOfficialRemoteProfile)
    }

    private suspend fun WebPostgrestClient.rows(
        table: String,
        query: Map<String, String>,
        limit: Int? = null,
        authMode: WebPostgrestAuthMode = WebPostgrestAuthMode.Public,
    ): List<JsonObject> = when (val result = get(table = table, query = query, limit = limit, authMode = authMode)) {
        is WebPostgrestResult.Success -> Json.parseToJsonElement(result.body).jsonArray.map { it.jsonObject }
        is WebPostgrestResult.Failure -> throw WebPostgrestReadException(result)
    }

    private fun Collection<String>.toOfficialPostgrestInFilter(): String =
        "in.(${distinct().joinToString(",") { it.requireOfficialPostgrestIdentifier() }})"

    private fun String.requireOfficialPostgrestIdentifier(): String {
        require(matches(PostgrestIdentifier)) { "web_official_invalid_postgrest_identifier" }
        return this
    }

    private suspend fun authenticatedUserId(): String = authRepository.restoreLocalSession()?.userId
        ?.takeIf(String::isNotBlank)
        ?: error("web_official_session_missing")

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

private fun WebPostgrestResult.requireWebOfficialSuccess() {
    if (this is WebPostgrestResult.Failure) error("web_official_postgrest_${reason}")
}

private fun WebPostgrestResult.requireWebOfficialBody(): String = when (this) {
    is WebPostgrestResult.Success -> body
    is WebPostgrestResult.Failure -> error("web_official_postgrest_$reason")
}

private fun String.webJsonString(): String = buildString {
    append('"')
    for (char in this@webJsonString) append(if (char == '"' || char == '\\') "\\$char" else char)
    append('"')
}

private fun currentOfficialTimestamp(): String = js("new Date().toISOString()")
private fun webOfficialRandomUuid(): String = js("globalThis.crypto?.randomUUID?.() || String(Date.now())")

private fun JsonObject.toOfficialRemotePost(): OfficialRemotePost = officialRemotePostFromWire(
    id = requiredOfficialString("id"),
    fields = officialWireFields(OfficialRemoteWireSchema.postScalarKeys),
    isLive = officialStringOrNull("is_live") == "true",
)

private fun JsonObject.toOfficialRemoteComment(): OfficialRemoteComment = officialRemoteCommentFromWire(
    id = requiredOfficialString("id"),
    fields = officialWireFields(OfficialRemoteWireSchema.commentScalarKeys),
)

private fun JsonObject.toOfficialRemoteLike(): OfficialRemoteLike =
    officialRemoteLikeFromWire(officialWireFields(OfficialRemoteWireSchema.likeScalarKeys))

private fun JsonObject.toOfficialRemoteProfile(): OfficialRemoteProfile = officialRemoteProfileFromWire(
    id = requiredOfficialString("id"),
    fields = officialWireFields(OfficialRemoteWireSchema.profileScalarKeys),
    isAdmin = officialStringOrNull("is_admin") == "true",
    isOfficial = officialStringOrNull("is_official") == "true",
)

private fun JsonObject.officialWireFields(keys: Set<String>): OfficialRemoteWireFields =
    OfficialRemoteWireFields.from(keys) { key -> officialStringOrNull(key) }

private fun JsonObject.requiredOfficialString(name: String): String =
    officialStringOrNull(name) ?: error("web_official_response_missing_$name")

private fun JsonObject.officialStringOrNull(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

private fun currentWebOfficialLanguage(): String? = js("globalThis.navigator?.language || 'es'")
