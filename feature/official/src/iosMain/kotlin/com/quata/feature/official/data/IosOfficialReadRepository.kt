package com.quata.feature.official.data

import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.core.session.IosRenewableAuthSession
import com.quata.core.data.toFoundationData
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialRepository
import com.quata.feature.official.data.selectOfficialTranslations
import com.quata.feature.official.data.officialTranslationReadPlan
import com.quata.feature.official.data.officialLikeLookupPlan
import com.quata.feature.official.data.officialLikeInsertPlan
import com.quata.feature.official.data.officialLikeDeletePlan
import com.quata.feature.official.data.officialCommentPlan
import com.quata.feature.official.data.officialSoftDeletePlan
import com.quata.feature.official.data.officialPostInsertPlan
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSJSONSerialization
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSLocale
import platform.Foundation.setValue
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Client-safe deployment values for the iOS Official read boundary. */
data class IosOfficialRuntimeConfiguration(
    val supabaseUrl: String,
    val supabasePublishableKey: String,
    /** Public Android/Web-compatible endpoint used for the WordPress video transport. */
    val wordpressBaseUrl: String = "https://egquata.com/",
)

/**
 * Explicit failure returned by the iOS PostgREST reader.
 *
 * In particular, authorization and RLS failures must not be turned into an empty Official feed:
 * callers receive [kind] together with the HTTP status so the host can expose a genuine retry or
 * sign-in state.
 */
class IosOfficialReadException(
    val kind: IosOfficialReadFailureKind,
    val statusCode: Int? = null,
    reason: String,
) : IllegalStateException("ios_official_${kind.name.lowercase()}:$reason")

enum class IosOfficialReadFailureKind {
    Configuration,
    Session,
    Unauthorized,
    RlsDenied,
    Network,
    Http,
    Response,
}

/**
 * Public-read and authenticated-interaction PostgREST adapter for Official content.
 *
 * Public Official reads use only the Supabase
 * publishable key, just like the iOS Feed reader: a missing, expired or restored user session
 * must neither prevent an anonymous visitor from reading Official nor become a bearer header on
 * that visitor's requests.  An optional session is used exclusively by [refreshCurrentUser] to
 * enrich the local UI identity when an authenticated host explicitly chooses to provide one.
 * That authenticated host may delete, like, comment and report through reviewed RLS paths;
 * createPost/createPosts remain explicit unsupported operations until publishing is shipped.
 */
@OptIn(ExperimentalForeignApi::class)
class IosOfficialReadRepository(
    private val configuration: IosOfficialRuntimeConfiguration,
    private val authSession: IosRenewableAuthSession? = null,
    private val preferredLanguageTag: String? = null,
) : OfficialRepository {
    override fun observeOfficialFeed(): Flow<Result<List<OfficialPostItem>>> = flow {
        // This transport has no verified Realtime contract. Emit a network snapshot and let the
        // shared ViewModel request an explicit refresh instead of presenting polling as a stream.
        emit(loadFeed(limit = FeedPageSize))
    }

    override suspend fun getOfficialFeed(): Result<List<OfficialPostItem>> = loadFeed(limit = FeedPageSize)

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
        // Identity enrichment is intentionally the only Official operation allowed to consult a
        // session.  Read requests remain strictly anonymous even after a login was restored.
        val userId = authSession?.currentSession()?.userId?.trim()?.takeIf(String::isNotEmpty)
            ?: return@runCatching null
        loadProfiles(listOf(userId)).firstOrNull()?.toOfficialDomainUser()
    }

    override suspend fun createPost(draft: OfficialPostDraft): Result<OfficialPostItem?> = createPosts(listOf(draft))

    override suspend fun createPosts(drafts: List<OfficialPostDraft>): Result<OfficialPostItem?> = runCatching {
        require(drafts.isNotEmpty()) { "ios_official_drafts_empty" }
        val profileId = authenticatedUserId().requireOfficialPostgrestIdentifier()
        val profile = refreshCurrentUser().getOrThrow()
        check(profile?.isOfficial == true || profile?.isAdmin == true) { "ios_official_publish_forbidden" }
        val insertedIds = mutableListOf<String>()
        try {
            drafts.forEach { draft ->
                require(draft.title.isNotBlank() && draft.contentHtml.isNotBlank()) { "ios_official_draft_invalid" }
                require(draft.mediaUrl.isNullOrBlank() || draft.mediaUrl.startsWith("https://")) { "ios_official_media_not_uploaded" }
                val response = authenticatedRequest("official_posts", "POST", emptyMap(), officialPostInsertPlan(profileId, draft).body!!)
                val rows = NSJSONSerialization.JSONObjectWithData(response, options = 0u, error = null) as? List<*>
                val createdId = ((rows?.singleOrNull() as? Map<*, *>)?.get("id") as? String)
                    ?.takeIf(String::isNotBlank) ?: error("ios_official_created_id_missing")
                insertedIds += createdId
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                insertedIds.asReversed().forEach { id ->
                    runCatching { mutate("official_posts", "PATCH", mapOf("id" to "eq.$id"), "{\"deleted_at\":${iosJsonString(currentOfficialTimestamp())}}") }
                        .exceptionOrNull()?.let(failure::addSuppressed)
                }
            }
            throw failure
        }
        val createdId = insertedIds.firstOrNull() ?: error("ios_official_created_id_missing")
        getOfficialPost(createdId).getOrThrow() ?: error("ios_official_created_post_missing")
    }

    override suspend fun deletePost(postId: String): Result<Unit> = runCatching {
        val userId = authenticatedUserId()
        val post = getOfficialPost(postId).getOrThrow() ?: error("ios_official_post_missing")
        val currentUser = refreshCurrentUser().getOrThrow()
        check(post.author.id == userId || currentUser?.isAdmin == true) { "ios_official_delete_forbidden" }
        officialSoftDeletePlan(postId.requireOfficialPostgrestIdentifier(), post.translationGroupId, currentOfficialTimestamp()).let { mutate(it.table, it.method, it.filter, it.body) }
    }

    override suspend fun toggleLike(postId: String): Result<OfficialPostItem?> = runCatching {
        val userId = authenticatedUserId()
        val safePostId = postId.requireOfficialPostgrestIdentifier()
        val safeUserId = userId.requireOfficialPostgrestIdentifier()
        val lookup = officialLikeLookupPlan(safePostId, safeUserId)
        val existing = authenticatedRows(lookup.table, lookup.filter)
        if (existing.isEmpty()) officialLikeInsertPlan(safePostId, safeUserId).let { mutate(it.table, it.method, it.filter, it.body) }
        else officialLikeDeletePlan(safePostId, safeUserId).let { mutate(it.table, it.method, it.filter, it.body) }
        getOfficialPost(postId).getOrThrow()
    }

    override suspend fun addComment(postId: String, comment: PostComment): Result<OfficialPostItem?> = runCatching {
        val userId = authenticatedUserId()
        val safePostId = postId.requireOfficialPostgrestIdentifier()
        val safeUserId = userId.requireOfficialPostgrestIdentifier()
        officialCommentPlan(safePostId, safeUserId, comment).let { mutate(it.table, it.method, it.filter, it.body) }
        getOfficialPost(postId).getOrThrow()
    }

    override suspend fun reportComment(commentId: String): Result<Unit> = runCatching {
        val userId = authenticatedUserId().requireOfficialPostgrestIdentifier()
        val targetId = commentId.requireOfficialPostgrestIdentifier()
        authenticatedRequest(
            table = "rpc/quata_ugc_report",
            method = "POST",
            query = emptyMap(),
            body = officialCommentReportPayload(userId, targetId),
        )
        Unit
    }

    private suspend fun loadFeed(
        limit: Int,
        publishedBefore: String? = null,
        postId: String? = null,
    ): Result<List<OfficialPostItem>> = runCatching {
        val translation = officialTranslationReadPlan(preferredLanguageTag, limit, postId)
        val posts = rows(
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
        ).map(Map<*, *>::toOfficialRemotePost).selectOfficialTranslations(preferredLanguageTag)
        if (posts.isEmpty()) return@runCatching emptyList()

        val postIds = posts.map(OfficialRemotePost::id)
        val comments = rows(
            table = "official_post_comments",
            query = mapOf(
                "select" to CommentSelect,
                "official_post_id" to postIds.toOfficialPostgrestInFilter(),
                "deleted_at" to "is.null",
                "order" to "created_at.asc",
            ),
        ).map(Map<*, *>::toOfficialRemoteComment)
        val likes = rows(
            table = "official_post_likes",
            query = mapOf(
                "select" to LikeSelect,
                "official_post_id" to postIds.toOfficialPostgrestInFilter(),
            ),
        ).map(Map<*, *>::toOfficialRemoteLike)
        val profiles = loadProfiles(officialRemoteProfileIds(posts, comments, likes))
        buildOfficialDomainPosts(
            posts = posts,
            comments = comments,
            likes = likes,
            profiles = profiles,
            // This is identity-only metadata for the already-public response. It never changes
            // the bearer-free GET transport used above.
            currentUserId = authSession?.currentSession()?.userId,
            defaultTitle = DefaultTitle,
            defaultCommentAuthor = DefaultCommentAuthor,
        )
    }

    private suspend fun loadProfiles(ids: Collection<String>): List<OfficialRemoteProfile> {
        if (ids.isEmpty()) return emptyList()
        return rows(
            table = "community_profiles",
            query = mapOf(
                "select" to ProfileSelect,
                "id" to ids.toOfficialPostgrestInFilter(),
            ),
        ).map(Map<*, *>::toOfficialRemoteProfile)
    }

    private suspend fun rows(table: String, query: Map<String, String>, limit: Int? = null): List<Map<*, *>> {
        require(table.matches(IosPostgrestTableName)) { "ios_official_table_invalid" }
        val result = request(table, query + listOfNotNull(limit?.let { "limit" to it.toString() }))
        val root = NSJSONSerialization.JSONObjectWithData(result, options = 0u, error = null)
            as? List<*> ?: throw IosOfficialReadException(
                kind = IosOfficialReadFailureKind.Response,
                reason = "postgrest_response_not_array",
            )
        return root.mapIndexed { index, row ->
            row as? Map<*, *> ?: throw IosOfficialReadException(
                kind = IosOfficialReadFailureKind.Response,
                reason = "postgrest_row_${index}_not_object",
            )
        }
    }

    private suspend fun request(table: String, query: Map<String, String>): NSData {
        val baseUrl = configuration.supabaseUrl.trim().trimEnd('/').takeIf(String::isNotEmpty)
            ?: throw IosOfficialReadException(IosOfficialReadFailureKind.Configuration, reason = "supabase_url_missing")
        val publishableKey = configuration.supabasePublishableKey.trim().takeIf(String::isNotEmpty)
            ?: throw IosOfficialReadException(IosOfficialReadFailureKind.Configuration, reason = "supabase_publishable_key_missing")
        val publicRequest = iosPublicOfficialRequest(
            baseUrl = baseUrl,
            publishableKey = publishableKey,
            table = table,
            query = query,
        )
        val url = NSURL(string = publicRequest.url)
            ?: throw IosOfficialReadException(IosOfficialReadFailureKind.Configuration, reason = "postgrest_url_invalid")
        return NSMutableURLRequest.requestWithURL(url).apply {
            publicRequest.headers.forEach { (name, value) -> setValue(value, name) }
        }.executeOfficialRead()
    }

    private suspend fun authenticatedRows(table: String, query: Map<String, String>): List<Map<*, *>> {
        val result = authenticatedRequest(table, "GET", query, null)
        val root = NSJSONSerialization.JSONObjectWithData(result, options = 0u, error = null) as? List<*> ?: error("ios_official_response_not_array")
        return root.map { it as? Map<*, *> ?: error("ios_official_response_not_object") }
    }

    private suspend fun mutate(table: String, method: String, query: Map<String, String>, body: String?) {
        authenticatedRequest(table, method, query, body)
    }

    private suspend fun authenticatedRequest(table: String, method: String, query: Map<String, String>, body: String?): NSData {
        require(table.matches(IosPostgrestTableName) || table.matches(IosPostgrestRpcPath)) { "ios_official_table_invalid" }
        val session = authSession?.currentSession()?.takeIf { it.bearerToken.isNotBlank() } ?: error("ios_official_session_missing")
        val baseUrl = configuration.supabaseUrl.trim().trimEnd('/').takeIf(String::isNotEmpty) ?: error("ios_official_supabase_url_missing")
        val key = configuration.supabasePublishableKey.trim().takeIf(String::isNotEmpty) ?: error("ios_official_publishable_key_missing")
        val encodedQuery = query.entries.joinToString("&") { (name, value) -> "${name.iosQueryComponent()}=${value.iosQueryComponent()}" }
        val url = NSURL(string = "$baseUrl/rest/v1/$table${if (encodedQuery.isEmpty()) "" else "?$encodedQuery"}") ?: error("ios_official_postgrest_url_invalid")
        return NSMutableURLRequest.requestWithURL(url).apply {
            setHTTPMethod(method)
            setValue(key, "apikey")
            setValue("Bearer ${session.bearerToken}", "Authorization")
            setValue("application/json", "Accept")
            setValue("return=representation", "Prefer")
            if (body != null) { setValue("application/json", "Content-Type"); setHTTPBody(body.encodeToByteArray().toFoundationData()) }
        }.executeOfficialRead()
    }

    private fun Collection<String>.toOfficialPostgrestInFilter(): String =
        "in.(${distinct().joinToString(",") { it.requireOfficialPostgrestIdentifier() }})"

    private fun String.requireOfficialPostgrestIdentifier(): String {
        require(matches(IosOfficialIdentifier)) { "ios_official_invalid_postgrest_identifier" }
        return this
    }

    private suspend fun authenticatedUserId(): String {
        val sessionProvider = authSession
            ?: throw UnsupportedOperationException("ios_official_mutation_not_implemented")
        return sessionProvider.currentSession()?.userId
            ?.takeIf(String::isNotBlank) ?: error("ios_official_session_missing")
    }

    private companion object {
        const val FeedPageSize = 50
        const val DefaultTitle = "Cuenta oficial"
        const val DefaultCommentAuthor = "Usuario"
        const val PostSelect = "id,profile_id,title,summary,post_type,content_html,read_more_label,language,translation_group_id,media_url,media_type,link_url,is_live,published_at,created_at"
        const val CommentSelect = "id,official_post_id,profile_id,body,created_at"
        const val LikeSelect = "id,official_post_id,profile_id,created_at"
        const val ProfileSelect = "id,display_name,barrio,neighborhood,nombre,avatar_url,avatar,is_admin,is_official"
    }
}

private fun iosJsonString(value: String): String = buildString { append('"'); value.forEach { append(if (it == '"' || it == '\\') "\\$it" else it) }; append('"') }
@OptIn(kotlin.time.ExperimentalTime::class)
private fun currentOfficialTimestamp(): String = kotlin.time.Clock.System.now().toString()

/** Small iOS composition factory; host/UI ownership remains with the launcher. */
class IosOfficialRuntimeBootstrap(
    configuration: IosOfficialRuntimeConfiguration,
    authSession: IosRenewableAuthSession? = null,
    preferredLanguageTag: String? = null,
) {
    val repository: OfficialRepository = IosOfficialReadRepository(configuration, authSession, preferredLanguageTag)
}

/**
 * Pure request plan for every anonymous Official read endpoint.
 *
 * This is deliberately kept outside URLSession so Kotlin/Native tests can prove URL encoding
 * and the absence of an Authorization slot without a deployment, a Keychain entry or network.
 */
internal data class IosPublicOfficialRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
)

/** Anonymous Official reads are authenticated only with the client-safe publishable key. */
internal fun iosOfficialPublicHeaders(publishableKey: String): Map<String, String> = mapOf(
    "apikey" to publishableKey.trim(),
    "Accept" to "application/json",
)

internal fun iosPublicOfficialRequest(
    baseUrl: String,
    publishableKey: String,
    table: String,
    query: Map<String, String>,
): IosPublicOfficialRequest {
    require(table.matches(IosPostgrestTableName)) { "ios_official_table_invalid" }
    val encodedQuery = query.entries.joinToString("&") { (key, value) ->
        "${key.iosQueryComponent()}=${value.iosQueryComponent()}"
    }
    return IosPublicOfficialRequest(
        method = "GET",
        url = "${baseUrl.trim().trimEnd('/')}/rest/v1/$table?$encodedQuery",
        headers = iosOfficialPublicHeaders(publishableKey),
    )
}

/** Pure HTTP-status policy so every native transport outcome is covered without URLSession. */
internal fun iosOfficialReadFailureKind(statusCode: Int?): IosOfficialReadFailureKind = when (statusCode) {
    401 -> IosOfficialReadFailureKind.Unauthorized
    403 -> IosOfficialReadFailureKind.RlsDenied
    null -> IosOfficialReadFailureKind.Network
    else -> IosOfficialReadFailureKind.Http
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun NSMutableURLRequest.executeOfficialRead(): NSData = suspendCancellableCoroutine { continuation ->
    val delegate = IosOfficialDataTaskDelegate(continuation)
    val session = NSURLSession.sessionWithConfiguration(
        NSURLSessionConfiguration.ephemeralSessionConfiguration(), delegate, null,
    )
    val task = session.dataTaskWithRequest(this)
    continuation.invokeOnCancellation {
        task.cancel()
        session.invalidateAndCancel()
    }
    task.resume()
}

@OptIn(ExperimentalForeignApi::class)
private class IosOfficialDataTaskDelegate(
    private val continuation: CancellableContinuation<NSData>,
) : NSObject(), NSURLSessionDataDelegateProtocol {
    private val chunks = mutableListOf<ByteArray>()

    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        if (continuation.isActive) chunks += didReceiveData.toIosBytes()
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        session.finishTasksAndInvalidate()
        if (!continuation.isActive) return
        if (didCompleteWithError != null) {
            continuation.resumeWithException(
                IosOfficialReadException(IosOfficialReadFailureKind.Network, reason = didCompleteWithError.localizedDescription),
            )
            return
        }
        val status = (task.response as? NSHTTPURLResponse)?.statusCode?.toInt()
        if (status == null || status !in 200..299) {
            continuation.resumeWithException(
                IosOfficialReadException(iosOfficialReadFailureKind(status), status, "postgrest_http_${status ?: "unknown"}"),
            )
            return
        }
        continuation.resume(chunks.toIosData())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toIosBytes(): ByteArray =
    if (length == 0uL) ByteArray(0) else bytes?.readBytes(length.toInt()) ?: ByteArray(0)

@OptIn(ExperimentalForeignApi::class)
private fun List<ByteArray>.toIosData(): NSData {
    return toFoundationData()
}

private fun Map<*, *>.toOfficialRemotePost() = officialRemotePostFromWire(
    id = requiredOfficialString("id"),
    fields = officialWireFields(OfficialRemoteWireSchema.postScalarKeys),
    isLive = officialBoolean("is_live"),
)

private fun Map<*, *>.toOfficialRemoteComment() = officialRemoteCommentFromWire(
    id = requiredOfficialString("id"),
    fields = officialWireFields(OfficialRemoteWireSchema.commentScalarKeys),
)

private fun Map<*, *>.toOfficialRemoteLike() =
    officialRemoteLikeFromWire(officialWireFields(OfficialRemoteWireSchema.likeScalarKeys))

private fun Map<*, *>.toOfficialRemoteProfile() = officialRemoteProfileFromWire(
    id = requiredOfficialString("id"),
    fields = officialWireFields(OfficialRemoteWireSchema.profileScalarKeys),
    isAdmin = officialBoolean("is_admin"),
    isOfficial = officialBoolean("is_official"),
)

private fun Map<*, *>.officialWireFields(keys: Set<String>): OfficialRemoteWireFields =
    OfficialRemoteWireFields.from(keys) { key -> officialStringOrNull(key) }

private fun Map<*, *>.requiredOfficialString(name: String): String = officialStringOrNull(name)
    ?: throw IosOfficialReadException(IosOfficialReadFailureKind.Response, reason = "response_missing_$name")

private fun Map<*, *>.officialStringOrNull(name: String): String? = this[name]?.toString()?.takeIf(String::isNotBlank)

private fun Map<*, *>.officialBoolean(name: String): Boolean = when (officialStringOrNull(name)?.lowercase()) {
    "true", "1", "yes" -> true
    else -> false
}

private fun String.iosQueryComponent(): String = encodeToByteArray().joinToString("") { byte ->
    val value = byte.toInt() and 0xff
    if ((value in 'a'.code..'z'.code) || (value in 'A'.code..'Z'.code) || (value in '0'.code..'9'.code) || value in intArrayOf('-'.code, '.'.code, '_'.code, '~'.code)) {
        value.toChar().toString()
    } else "%${value.toString(16).padStart(2, '0').uppercase()}"
}

private val IosPostgrestTableName = Regex("[A-Za-z_][A-Za-z0-9_]*")
private val IosPostgrestRpcPath = Regex("rpc/[A-Za-z_][A-Za-z0-9_]*")
private val IosOfficialIdentifier = Regex("[A-Za-z0-9_-]+")
