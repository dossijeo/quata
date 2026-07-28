package com.quata.feature.official.data

import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.core.session.IosRenewableAuthSession
import com.quata.core.data.toFoundationData
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialRepository
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
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.setValue
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Client-safe deployment values for the iOS Official read boundary. */
data class IosOfficialRuntimeConfiguration(
    val supabaseUrl: String,
    val supabasePublishableKey: String,
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
 * Authenticated, read-only PostgREST adapter for Official content.
 *
 * It deliberately has no mutation methods and uses a fresh Keychain-backed session for every
 * request. Therefore a refresh completed by the existing iOS auth boundary is used immediately.
 */
@OptIn(ExperimentalForeignApi::class)
class IosOfficialReadRepository(
    private val configuration: IosOfficialRuntimeConfiguration,
    private val authSession: IosRenewableAuthSession,
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
        val userId = authenticatedSession().userId.trim().takeIf(String::isNotEmpty)
            ?: return@runCatching null
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
        val posts = rows(
            table = "official_posts",
            query = buildMap {
                put("select", PostSelect)
                put("order", "published_at.desc")
                publishedBefore?.let { put("published_at", "lt.$it") }
                postId?.let { put("id", "eq.${it.requireOfficialPostgrestIdentifier()}") }
            },
            limit = limit,
        ).map(Map<*, *>::toOfficialRemotePost)
        if (posts.isEmpty()) return@runCatching emptyList()

        val postIds = posts.map(OfficialRemotePost::id)
        val comments = rows(
            table = "official_post_comments",
            query = mapOf(
                "select" to CommentSelect,
                "official_post_id" to postIds.toOfficialPostgrestInFilter(),
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
            currentUserId = authenticatedSession().userId,
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
        val session = authenticatedSession()
        val endpoint = "$baseUrl/rest/v1/$table?${query.entries.joinToString("&") { (key, value) ->
            "${key.iosQueryComponent()}=${value.iosQueryComponent()}"
        }}"
        val url = NSURL(string = endpoint)
            ?: throw IosOfficialReadException(IosOfficialReadFailureKind.Configuration, reason = "postgrest_url_invalid")
        return NSMutableURLRequest.requestWithURL(url).apply {
            setValue(publishableKey, "apikey")
            setValue("Bearer ${session.bearerToken}", "Authorization")
            setValue("application/json", "Accept")
        }.executeOfficialRead()
    }

    private suspend fun authenticatedSession() = authSession.currentSession()
        ?.takeIf { it.bearerToken.isNotBlank() }
        ?: throw IosOfficialReadException(IosOfficialReadFailureKind.Session, reason = "session_missing")

    private fun Collection<String>.toOfficialPostgrestInFilter(): String =
        "in.(${distinct().joinToString(",") { it.requireOfficialPostgrestIdentifier() }})"

    private fun String.requireOfficialPostgrestIdentifier(): String {
        require(matches(IosOfficialIdentifier)) { "ios_official_invalid_postgrest_identifier" }
        return this
    }

    private fun <T> unsupportedMutation(): Result<T> =
        Result.failure(UnsupportedOperationException("ios_official_mutation_not_implemented"))

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

/** Small iOS composition factory; host/UI ownership remains with the launcher. */
class IosOfficialRuntimeBootstrap(
    configuration: IosOfficialRuntimeConfiguration,
    authSession: IosRenewableAuthSession,
) {
    val repository: OfficialRepository = IosOfficialReadRepository(configuration, authSession)
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
            val kind = when (status) {
                401 -> IosOfficialReadFailureKind.Unauthorized
                403 -> IosOfficialReadFailureKind.RlsDenied
                null -> IosOfficialReadFailureKind.Network
                else -> IosOfficialReadFailureKind.Http
            }
            continuation.resumeWithException(
                IosOfficialReadException(kind, status, "postgrest_http_${status ?: "unknown"}"),
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
private val IosOfficialIdentifier = Regex("[A-Za-z0-9_-]+")
