package com.quata.feature.feed.data

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.cinterop.ExperimentalForeignApi
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
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSMutableData
import platform.darwin.NSObject

/** Client-safe deployment settings; never provide a service-role key through this boundary. */
data class IosFeedRuntimeConfiguration(
    val supabaseUrl: String,
    val supabasePublishableKey: String,
)

/** A refreshed authenticated session supplied by the iOS Auth/composition root. */
data class IosFeedSession(
    val accessToken: String,
    val userId: String,
)

/**
 * The host owns persistence, refresh and logout. This transport asks for a session per request so
 * it never retains a stale token and cannot invent an unauthenticated Feed.
 */
fun interface IosFeedSessionProvider {
    suspend fun currentSession(): IosFeedSession?
}

/**
 * URLSession implementation of the shared Feed read protocol. It mirrors the authenticated Web
 * PostgREST table/select/filter contract while leaving polling and domain mapping in commonMain.
 */
@OptIn(ExperimentalForeignApi::class)
class IosFeedReadTransport(
    private val configuration: IosFeedRuntimeConfiguration,
    private val sessionProvider: IosFeedSessionProvider,
) : FeedReadTransport {
    override suspend fun fetchPosts(request: FeedRemotePostRequest): Result<List<FeedRemotePost>> = runCatching {
        val query = buildMap {
            put("select", PostSelect)
            put("order", "created_at.desc")
            put("limit", request.limit.coerceAtLeast(1).toString())
            request.beforeCreatedAt?.takeIf(String::isNotBlank)?.let { put("created_at", "lt.$it") }
            request.postId?.takeIf(String::isNotBlank)?.let { put("id", "eq.${it.requireIosPostgrestIdentifier()}") }
        }
        getRows("community_posts", query).map { it.toFeedRemotePost() }
    }

    override suspend fun fetchComments(postIds: List<String>): Result<List<FeedRemoteComment>> = runCatching {
        if (postIds.isEmpty()) emptyList() else getRows(
            table = "community_comments",
            query = mapOf(
                "select" to CommentSelect,
                "post_id" to postIds.toIosPostgrestInFilter(),
                "order" to "created_at.asc",
            ),
        ).map { it.toFeedRemoteComment() }
    }

    override suspend fun fetchLikes(postIds: List<String>): Result<List<FeedRemoteLike>> = runCatching {
        if (postIds.isEmpty()) emptyList() else getRows(
            table = "community_post_likes",
            query = mapOf("select" to LikeSelect, "post_id" to postIds.toIosPostgrestInFilter()),
        ).map { it.toFeedRemoteLike() }
    }

    override suspend fun fetchProfiles(profileIds: List<String>): Result<List<FeedRemoteProfile>> = runCatching {
        if (profileIds.isEmpty()) emptyList() else getRows(
            table = "community_profiles",
            query = mapOf("select" to ProfileSelect, "id" to profileIds.toIosPostgrestInFilter()),
        ).map { it.toFeedRemoteProfile() }
    }

    override suspend fun currentUserId(): Result<String?> = runCatching {
        sessionProvider.currentSession()?.userId?.takeIf(String::isNotBlank)
    }

    private suspend fun getRows(table: String, query: Map<String, String>): List<Map<*, *>> {
        require(table.matches(IosPostgrestTableName)) { "ios_feed_postgrest_table_invalid" }
        val baseUrl = configuration.supabaseUrl.trim().trimEnd('/')
            .takeIf(String::isNotEmpty)
            ?: error("ios_feed_supabase_url_missing")
        val publishableKey = configuration.supabasePublishableKey.trim().takeIf(String::isNotEmpty)
            ?: error("ios_feed_supabase_publishable_key_missing")
        val session = sessionProvider.currentSession()
            ?.takeIf { it.accessToken.isNotBlank() }
            ?: error("ios_feed_session_missing")
        val url = NSURL(string = "$baseUrl/rest/v1/$table${query.toIosQueryString()}")
            ?: error("ios_feed_url_invalid")
        val requestConfiguration = NSURLSessionConfiguration.ephemeralSessionConfiguration().apply {
            HTTPAdditionalHeaders = mapOf(
                "apikey" to publishableKey,
                "Authorization" to "Bearer ${session.accessToken}",
                "Accept" to "application/json",
            )
        }
        return requestConfiguration.iosData(url).toIosJsonRows()
    }
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun NSURLSessionConfiguration.iosData(url: NSURL): NSData = suspendCancellableCoroutine { continuation ->
    val delegate = IosFeedDataTaskDelegate(continuation)
    val session = NSURLSession.sessionWithConfiguration(this, delegate, null)
    val task = session.dataTaskWithRequest(NSURLRequest(URL = url))
    continuation.invokeOnCancellation {
        task.cancel()
        session.invalidateAndCancel()
    }
    task.resume()
}

/**
 * Kotlin/Native does not expose the convenient completion-handler overload reliably across the
 * currently supported Apple targets. Keep the response lifecycle in the native delegate API: the
 * data delegate accumulates every chunk and its inherited task-delegate callback finishes the
 * suspending request exactly once.
 */
@OptIn(ExperimentalForeignApi::class)
private class IosFeedDataTaskDelegate(
    private val continuation: CancellableContinuation<NSData>,
) : NSObject(), NSURLSessionDataDelegateProtocol {
    private val payload = NSMutableData()

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveData: NSData,
    ) {
        if (continuation.isActive) payload.appendData(didReceiveData)
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?,
    ) {
        session.finishTasksAndInvalidate()
        if (!continuation.isActive) return
        if (didCompleteWithError != null) {
            continuation.resumeWithException(
                IllegalStateException(didCompleteWithError.localizedDescription),
            )
            return
        }
        val status = (task.response as? NSHTTPURLResponse)?.statusCode?.toInt()
        if (status == null || status !in 200..299) {
            continuation.resumeWithException(IllegalStateException("ios_feed_http_${status ?: "unknown"}"))
            return
        }
        if (payload.length == 0uL) {
            continuation.resumeWithException(IllegalStateException("ios_feed_response_empty"))
            return
        }
        continuation.resume(payload)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toIosJsonRows(): List<Map<*, *>> {
    val json = NSJSONSerialization.JSONObjectWithData(this, options = 0u, error = null) as? List<*>
        ?: error("ios_feed_response_not_array")
    return json.map { row -> row as? Map<*, *> ?: error("ios_feed_response_row_invalid") }
}

private fun Map<*, *>.toFeedRemotePost(): FeedRemotePost = FeedRemotePost(
    id = requiredIosString("id"),
    profileId = iosString("profile_id"),
    authorId = iosString("author_id"),
    body = iosString("body"),
    content = iosString("content"),
    imageUrl = iosString("image_url"),
    videoUrl = iosString("video_url"),
    createdAt = iosString("created_at"),
)

private fun Map<*, *>.toFeedRemoteComment(): FeedRemoteComment = FeedRemoteComment(
    id = requiredIosString("id"),
    postId = iosString("post_id"),
    profileId = iosString("profile_id"),
    body = iosString("body"),
    createdAt = iosString("created_at"),
)

private fun Map<*, *>.toFeedRemoteLike(): FeedRemoteLike = FeedRemoteLike(
    postId = iosString("post_id"),
    profileId = iosString("profile_id"),
)

private fun Map<*, *>.toFeedRemoteProfile(): FeedRemoteProfile = FeedRemoteProfile(
    id = requiredIosString("id"),
    displayName = iosString("display_name"),
    fallbackName = iosString("nombre"),
    countryCode = iosString("country_code"),
    phoneLocal = iosString("phone_local"),
    neighborhood = iosString("neighborhood"),
    barrio = iosString("barrio"),
    avatarUrl = iosString("avatar_url"),
    avatar = iosString("avatar"),
    isAdmin = iosBoolean("is_admin"),
    isOfficial = iosBoolean("is_official"),
)

private fun Map<*, *>.requiredIosString(name: String): String = iosString(name) ?: error("ios_feed_response_missing_$name")
private fun Map<*, *>.iosString(name: String): String? = this[name]
    ?.takeUnless { it is NSNull }
    ?.toString()
private fun Map<*, *>.iosBoolean(name: String): Boolean = when (val value = this[name]) {
    is Boolean -> value
    is Number -> value.toInt() != 0
    else -> value?.toString()?.equals("true", ignoreCase = true) == true
}

private fun Collection<String>.toIosPostgrestInFilter(): String = "in.(${distinct().joinToString(",") { it.requireIosPostgrestIdentifier() }})"
private fun String.requireIosPostgrestIdentifier(): String {
    require(matches(IosPostgrestIdentifier)) { "ios_feed_invalid_postgrest_identifier" }
    return this
}

private fun Map<String, String>.toIosQueryString(): String = entries.joinToString(prefix = "?", separator = "&") { (key, value) ->
    "${key.iosQueryComponent()}=${value.iosQueryComponent()}"
}

private fun String.iosQueryComponent(): String = encodeToByteArray().joinToString(separator = "") { byte ->
    val value = byte.toInt() and 0xff
    if ((value in 'a'.code..'z'.code) || (value in 'A'.code..'Z'.code) || (value in '0'.code..'9'.code) || value in intArrayOf('-'.code, '.'.code, '_'.code, '~'.code)) {
        value.toChar().toString()
    } else {
        "%${value.toString(16).padStart(2, '0').uppercase()}"
    }
}

private const val PostSelect = "id,wall_id,profile_id,body,image_url,video_url,created_at,community_id,author_id,content"
private const val CommentSelect = "id,post_id,profile_id,body,created_at"
private const val LikeSelect = "post_id,profile_id,created_at"
private const val ProfileSelect = "id,display_name,barrio,neighborhood,nombre,avatar_url,avatar,is_admin,is_official"
private val IosPostgrestTableName = Regex("[A-Za-z_][A-Za-z0-9_]*")
private val IosPostgrestIdentifier = Regex("[A-Za-z0-9_-]+")
