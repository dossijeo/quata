package com.quata.feature.feed.data

import com.quata.core.model.Post
import com.quata.core.model.User
import com.quata.core.data.loadCompleteKeyset
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
    suspend fun fetchCommentsPage(request: FeedRemoteCommentPageRequest): Result<List<FeedRemoteComment>>
    suspend fun fetchLikesPage(request: FeedRemoteLikePageRequest): Result<List<FeedRemoteLike>>
    /**
     * Profiles rendered as part of a feed page. Hosts may expose this narrow read publicly while
     * retaining [fetchProfiles] for identity/profile screens behind a session boundary.
     */
    suspend fun fetchFeedProfiles(profileIds: List<String>): Result<List<FeedRemoteProfile>> =
        fetchProfiles(profileIds)

    /** Current actor profile; kept separate from arbitrary author lookups for host auth policy. */
    suspend fun fetchCurrentUserProfile(profileId: String): Result<FeedRemoteProfile?> =
        fetchProfiles(listOf(profileId)).map { it.firstOrNull() }

    /** Session-scoped profile read used by current-user and author refreshes. */
    suspend fun fetchProfiles(profileIds: List<String>): Result<List<FeedRemoteProfile>>
    suspend fun currentUserId(): Result<String?>
}

data class FeedRemotePostRequest(
    val limit: Int,
    val beforeCreatedAt: String? = null,
    val beforeId: String? = null,
    val postId: String? = null,
)

data class FeedRemoteCommentPageRequest(
    val postIds: List<String>,
    val afterIdExclusive: String? = null,
    val limit: Int,
)

data class FeedRemoteLikePageRequest(
    val postIds: List<String>,
    val afterIdExclusive: String? = null,
    val limit: Int,
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

    override suspend fun loadOlderFeedPage(cursor: com.quata.feature.feed.domain.FeedCursor, limit: Int): Result<List<Post>> =
        loadFeed(
            limit.coerceAtLeast(1),
            beforeCreatedAt = cursor.createdAt.takeIf(String::isNotBlank),
            beforeId = cursor.postId.takeIf(String::isNotBlank),
        )

    suspend fun loadFeedPage(limit: Int): Result<List<Post>> =
        loadFeed(limit.coerceAtLeast(1))

    override suspend fun refreshCurrentUser(): Result<User?> = runCatching {
        val userId = transport.currentUserId().getOrThrow() ?: return@runCatching null
        transport.fetchCurrentUserProfile(userId).getOrThrow()?.toFeedDomainUser()
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
        beforeId: String? = null,
        postId: String? = null,
    ): Result<List<Post>> = runCatching {
        val posts = transport.fetchPosts(
            FeedRemotePostRequest(
                limit = limit.coerceAtLeast(1),
                beforeCreatedAt = beforeCreatedAt,
                beforeId = beforeId,
                postId = postId,
            ),
        ).getOrThrow()
        if (posts.isEmpty()) return@runCatching emptyList()
        val postIds = posts.map(FeedRemotePost::id)
        val comments = loadCompleteKeyset(
            pageSize = CommentPageSize,
            cursorOf = FeedRemoteComment::id,
        ) { afterIdExclusive, pageSize ->
            transport.fetchCommentsPage(
                FeedRemoteCommentPageRequest(
                    postIds = postIds,
                    afterIdExclusive = afterIdExclusive,
                    limit = pageSize,
                ),
            ).getOrThrow()
        }.sortedWith(compareBy<FeedRemoteComment> { it.createdAt.orEmpty() }.thenBy(FeedRemoteComment::id))
        val likes = loadCompleteKeyset(
            pageSize = LikePageSize,
            cursorOf = FeedRemoteLike::id,
        ) { afterIdExclusive, pageSize ->
            transport.fetchLikesPage(
                FeedRemoteLikePageRequest(
                    postIds = postIds,
                    afterIdExclusive = afterIdExclusive,
                    limit = pageSize,
                ),
            ).getOrThrow()
        }
        val profiles = transport.fetchFeedProfiles(feedRemoteProfileIds(posts, comments)).getOrThrow()
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
        const val CommentPageSize = 500
        const val LikePageSize = 500
        const val DefaultPollIntervalMillis = 30_000L
        const val MinimumPollIntervalMillis = 5_000L
    }
}
