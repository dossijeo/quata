package com.quata.feature.feed.data

import com.quata.core.data.toFoundationData
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSJSONSerialization
import platform.Foundation.NSNull
import platform.Foundation.NSURL
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSessionTask
import platform.Foundation.setHTTPMethod
import platform.darwin.NSObject

/** Client-safe deployment settings; never provide a service-role key through this boundary. */
data class IosFeedRuntimeConfiguration(
    val supabaseUrl: String,
    val supabasePublishableKey: String,
)

/**
 * URLSession implementation of the public Feed read protocol. Public requests deliberately use
 * only the publishable key and JSON accept header: this browser must not observe, restore, or
 * send an interactive session while reading posts, comments, likes, or profiles.
 */
@OptIn(ExperimentalForeignApi::class)
class IosFeedReadTransport(
    private val configuration: IosFeedRuntimeConfiguration,
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

    override suspend fun currentUserId(): Result<String?> = Result.success(null)

    private suspend fun getRows(table: String, query: Map<String, String>): List<Map<*, *>> {
        require(table.matches(IosPostgrestTableName)) { "ios_feed_postgrest_table_invalid" }
        val baseUrl = configuration.supabaseUrl.trim().trimEnd('/')
            .takeIf(String::isNotEmpty)
            ?: error("ios_feed_supabase_url_missing")
        val publishableKey = configuration.supabasePublishableKey.trim().takeIf(String::isNotEmpty)
            ?: error("ios_feed_supabase_publishable_key_missing")
        val publicRequest = iosPublicFeedRequest(
            baseUrl = baseUrl,
            publishableKey = publishableKey,
            table = table,
            query = query,
        )
        val url = NSURL(string = publicRequest.url)
            ?: error("ios_feed_url_invalid")
        val requestConfiguration = NSURLSessionConfiguration.ephemeralSessionConfiguration().apply {
            HTTPAdditionalHeaders = publicRequest.headers
        }
        return requestConfiguration.iosData(url, publicRequest.method).toIosJsonRows()
    }
}

/** Kept pure so Apple-target tests can prove the anonymous transport has no credential slot. */
internal fun iosFeedPublicHeaders(publishableKey: String): Map<Any?, Any?> = mapOf(
    "apikey" to publishableKey,
    "Accept" to "application/json",
)

/**
 * Complete public request plan shared by every Feed read endpoint. Keeping it pure makes the
 * table-by-table anonymous request policy testable without URLSession or a deployed backend.
 */
internal data class IosPublicFeedRequest(
    val method: String,
    val url: String,
    val headers: Map<Any?, Any?>,
)

internal fun iosPublicFeedRequest(
    baseUrl: String,
    publishableKey: String,
    table: String,
    query: Map<String, String>,
): IosPublicFeedRequest {
    require(table.matches(IosPostgrestTableName)) { "ios_feed_postgrest_table_invalid" }
    return IosPublicFeedRequest(
        method = "GET",
        url = "${baseUrl.trim().trimEnd('/')}/rest/v1/$table${query.toIosQueryString()}",
        headers = iosFeedPublicHeaders(publishableKey.trim()),
    )
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun NSURLSessionConfiguration.iosData(url: NSURL, method: String): NSData = suspendCancellableCoroutine { continuation ->
    val delegate = IosFeedDataTaskDelegate(continuation)
    val session = NSURLSession.sessionWithConfiguration(this, delegate, null)
    val task = session.dataTaskWithRequest(NSMutableURLRequest.requestWithURL(url).apply { setHTTPMethod(method) })
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
    private val chunks = mutableListOf<ByteArray>()

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveData: NSData,
    ) {
        if (continuation.isActive) chunks += didReceiveData.toIosByteArray()
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
        val payload = chunks.toIosDataOrNull()
        if (payload == null || payload.length == 0uL) {
            continuation.resumeWithException(IllegalStateException("ios_feed_response_empty"))
            return
        }
        continuation.resume(payload)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toIosByteArray(): ByteArray =
    if (length == 0uL) ByteArray(0) else bytes?.readBytes(length.toInt()) ?: ByteArray(0)

@OptIn(ExperimentalForeignApi::class)
private fun List<ByteArray>.toIosDataOrNull(): NSData? {
    if (all(ByteArray::isEmpty)) return null
    return toFoundationData()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toIosJsonRows(): List<Map<*, *>> {
    val json = NSJSONSerialization.JSONObjectWithData(this, options = 0u, error = null) as? List<*>
        ?: error("ios_feed_response_not_array")
    return json.map { row -> row as? Map<*, *> ?: error("ios_feed_response_row_invalid") }
}

private fun Map<*, *>.toFeedRemotePost(): FeedRemotePost = feedRemotePostFromFields(
    field = { name -> iosString(name) },
    missingIdError = { IllegalStateException("ios_feed_response_missing_id") },
)

private fun Map<*, *>.toFeedRemoteComment(): FeedRemoteComment = feedRemoteCommentFromFields(
    field = { name -> iosString(name) },
    missingIdError = { IllegalStateException("ios_feed_response_missing_id") },
)

private fun Map<*, *>.toFeedRemoteLike(): FeedRemoteLike = feedRemoteLikeFromFields { name -> iosString(name) }
private fun Map<*, *>.toFeedRemoteProfile(): FeedRemoteProfile = feedRemoteProfileFromFields(
    field = { name -> iosString(name) },
    booleanField = { name -> iosBoolean(name) },
    missingIdError = { IllegalStateException("ios_feed_response_missing_id") },
)

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
