package com.quata.feature.feed.data

import com.quata.core.model.Post
import com.quata.core.model.User
import com.quata.feature.feed.domain.FeedReadRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * Platform-authenticated transport for the DTOs already shared by Feed.
 *
 * Implementations own HTTP, endpoint configuration and session credentials. This keeps those
 * concerns out of the shared repository and lets iOS use URLSession without duplicating domain
 * mapping or polling policy.
 */
interface FeedReadTransport {
    suspend fun fetchPosts(request: FeedRemotePostRequest): Result<List<FeedRemotePost>>
    suspend fun fetchComments(postIds: List<String>): Result<List<FeedRemoteComment>>
    suspend fun fetchLikes(postIds: List<String>): Result<List<FeedRemoteLike>>
    suspend fun fetchProfiles(profileIds: List<String>): Result<List<FeedRemoteProfile>>
    suspend fun currentUserId(): Result<String?>
}

data class FeedRemotePostRequest(
    val limit: Int,
    val beforeCreatedAt: String? = null,
    val postId: String? = null,
)

/**
 * Read-only Feed repository reusable by iOS and other hosts once they inject a real transport.
 * It deliberately has no default transport, URL or credential source.
 */
class RemoteFeedReadRepository(
    private val transport: FeedReadTransport,
    private val pollIntervalMillis: Long = DefaultPollIntervalMillis,
) : FeedReadRepository {
    override fun observeFeed(): Flow<Result<List<Post>>> = flow {
        while (currentCoroutineContext().isActive) {
            emit(loadFeed(FeedPageSize))
            delay(pollIntervalMillis.coerceAtLeast(MinimumPollIntervalMillis))
        }
    }

    override suspend fun getFeed(): Result<List<Post>> = loadFeed(FeedPageSize)

    override suspend fun refreshFeed(): Result<List<Post>> = loadFeed(FeedPageSize)

    override suspend fun loadOlderFeedPage(beforeCreatedAt: String?, limit: Int): Result<List<Post>> =
        loadFeed(limit.coerceAtLeast(1), beforeCreatedAt = beforeCreatedAt?.takeIf(String::isNotBlank))

    override suspend fun refreshCurrentUser(): Result<User?> = runCatching {
        val userId = transport.currentUserId().getOrThrow() ?: return@runCatching null
        transport.fetchProfiles(listOf(userId)).getOrThrow().firstOrNull()?.toFeedDomainUser()
    }

    override suspend fun refreshAuthor(userId: String): Result<User?> = runCatching {
        userId.takeIf(String::isNotBlank)
            ?.let { transport.fetchProfiles(listOf(it)).getOrThrow().firstOrNull()?.toFeedDomainUser() }
    }

    override suspend fun refreshPost(postId: String): Result<Post?> =
        postId.takeIf(String::isNotBlank)?.let { loadFeed(limit = 1, postId = it).map { posts -> posts.firstOrNull() } }
            ?: Result.success(null)

    private suspend fun loadFeed(
        limit: Int,
        beforeCreatedAt: String? = null,
        postId: String? = null,
    ): Result<List<Post>> = runCatching {
        val posts = transport.fetchPosts(
            FeedRemotePostRequest(limit = limit.coerceAtLeast(1), beforeCreatedAt = beforeCreatedAt, postId = postId),
        ).getOrThrow()
        if (posts.isEmpty()) return@runCatching emptyList()
        val postIds = posts.map(FeedRemotePost::id)
        val comments = transport.fetchComments(postIds).getOrThrow()
        val likes = transport.fetchLikes(postIds).getOrThrow()
        val profiles = transport.fetchProfiles(feedRemoteProfileIds(posts, comments, likes)).getOrThrow()
        buildFeedDomainPosts(
            posts = posts,
            comments = comments,
            likes = likes,
            profiles = profiles,
            currentUserId = transport.currentUserId().getOrThrow(),
        )
    }

    private companion object {
        const val FeedPageSize = 50
        const val DefaultPollIntervalMillis = 30_000L
        const val MinimumPollIntervalMillis = 5_000L
    }
}
