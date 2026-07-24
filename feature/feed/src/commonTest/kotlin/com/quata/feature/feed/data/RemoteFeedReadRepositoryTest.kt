package com.quata.feature.feed.data

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteFeedReadRepositoryTest {
    @Test
    fun mapsPostsCommentsLikesAndProfilesThroughTransport() = runBlocking {
        val transport = FakeFeedReadTransport(
            posts = listOf(FeedRemotePost(id = "post-1", profileId = "author", body = "Hola &amp; Quata")),
            comments = listOf(FeedRemoteComment(id = "comment-1", postId = "post-1", profileId = "commenter", body = "Buen dÃ­a")),
            likes = listOf(FeedRemoteLike(postId = "post-1", profileId = "viewer")),
            profiles = listOf(
                FeedRemoteProfile(id = "author", displayName = "Autora", neighborhood = "Centro", isOfficial = true),
                FeedRemoteProfile(id = "commenter", fallbackName = "Vecino"),
                FeedRemoteProfile(id = "viewer", displayName = "Yo"),
            ),
            currentUserId = "viewer",
        )

        val post = RemoteFeedReadRepository(transport).getFeed().getOrThrow().single()

        assertEquals("Hola & Quata", post.text)
        assertEquals("Autora", post.author.displayName)
        assertEquals("Centro", post.author.neighborhood)
        assertTrue(post.author.isOfficial)
        assertEquals(1, post.likesCount)
        assertTrue(post.isLikedByCurrentUser)
        assertEquals("Vecino", post.comments.single().authorName)
        assertEquals(listOf("post-1"), transport.commentRequests.single())
        assertEquals(listOf("post-1"), transport.likeRequests.single())
        assertEquals(setOf("author", "commenter", "viewer"), transport.profileRequests.single().toSet())
    }

    @Test
    fun refreshPostUsesIdRequestAndReturnsOnlyRequestedDetail() = runBlocking {
        val transport = FakeFeedReadTransport(
            posts = listOf(
                FeedRemotePost(id = "first", profileId = "author"),
                FeedRemotePost(id = "target", profileId = "author", body = "Detalle"),
            ),
            profiles = listOf(FeedRemoteProfile(id = "author", displayName = "Autor")),
        )
        val repository = RemoteFeedReadRepository(transport)

        val post = repository.refreshPost("target").getOrThrow()

        assertEquals("target", post?.id)
        assertEquals(FeedRemotePostRequest(limit = 1, postId = "target"), transport.postRequests.single())
        assertNull(repository.refreshPost(" ").getOrThrow())
    }

    @Test
    fun requestsOlderPageWithCursorAndMinimumLimit() = runBlocking {
        val transport = FakeFeedReadTransport(
            posts = listOf(FeedRemotePost(id = "older", profileId = "author")),
            profiles = listOf(FeedRemoteProfile(id = "author")),
        )
        val repository = RemoteFeedReadRepository(transport)

        repository.loadOlderFeedPage("2026-07-01T12:00:00Z", 0).getOrThrow()
        repository.loadOlderFeedPage("   ", 12).getOrThrow()

        assertEquals(FeedRemotePostRequest(limit = 1, beforeCreatedAt = "2026-07-01T12:00:00Z"), transport.postRequests[0])
        assertEquals(FeedRemotePostRequest(limit = 12), transport.postRequests[1])
    }

    @Test
    fun refreshesCurrentUserAndAuthorFromProfiles() = runBlocking {
        val transport = FakeFeedReadTransport(
            profiles = listOf(
                FeedRemoteProfile(id = "viewer", fallbackName = "Cuenta actual", phoneLocal = "600123123"),
                FeedRemoteProfile(id = "author", displayName = "Autor remoto"),
            ),
            currentUserId = "viewer",
        )
        val repository = RemoteFeedReadRepository(transport)

        assertEquals("Cuenta actual", repository.refreshCurrentUser().getOrThrow()?.displayName)
        assertEquals("Autor remoto", repository.refreshAuthor("author").getOrThrow()?.displayName)
        assertNull(repository.refreshAuthor(" ").getOrThrow())
        assertEquals(listOf("viewer"), transport.profileRequests[0])
        assertEquals(listOf("author"), transport.profileRequests[1])
    }

    @Test
    fun propagatesTransportFailuresForEveryReadStage() = runBlocking {
        val transport = FakeFeedReadTransport(
            posts = listOf(FeedRemotePost(id = "post", profileId = "author")),
            profiles = listOf(FeedRemoteProfile(id = "author")),
        )
        val repository = RemoteFeedReadRepository(transport)

        transport.postsFailure = IllegalStateException("posts")
        assertTrue(repository.getFeed().isFailure)
        transport.postsFailure = null
        transport.commentsFailure = IllegalStateException("comments")
        assertTrue(repository.getFeed().isFailure)
        transport.commentsFailure = null
        transport.likesFailure = IllegalStateException("likes")
        assertTrue(repository.getFeed().isFailure)
        transport.likesFailure = null
        transport.profilesFailure = IllegalStateException("profiles")
        assertTrue(repository.getFeed().isFailure)
        transport.profilesFailure = null
        transport.currentUserFailure = IllegalStateException("current-user")
        assertTrue(repository.getFeed().isFailure)
        assertTrue(repository.refreshCurrentUser().isFailure)
        assertFalse(repository.refreshPost("post").isSuccess)
    }
}

private class FakeFeedReadTransport(
    private val posts: List<FeedRemotePost> = emptyList(),
    private val comments: List<FeedRemoteComment> = emptyList(),
    private val likes: List<FeedRemoteLike> = emptyList(),
    private val profiles: List<FeedRemoteProfile> = emptyList(),
    private val currentUserId: String? = null,
) : FeedReadTransport {
    val postRequests = mutableListOf<FeedRemotePostRequest>()
    val commentRequests = mutableListOf<List<String>>()
    val likeRequests = mutableListOf<List<String>>()
    val profileRequests = mutableListOf<List<String>>()
    var postsFailure: Throwable? = null
    var commentsFailure: Throwable? = null
    var likesFailure: Throwable? = null
    var profilesFailure: Throwable? = null
    var currentUserFailure: Throwable? = null

    override suspend fun fetchPosts(request: FeedRemotePostRequest): Result<List<FeedRemotePost>> {
        postRequests += request
        return postsFailure.asFailureOr {
            posts.filter { request.postId == null || it.id == request.postId }.take(request.limit)
        }
    }

    override suspend fun fetchComments(postIds: List<String>): Result<List<FeedRemoteComment>> {
        commentRequests += postIds
        return commentsFailure.asFailureOr { comments.filter { it.postId in postIds } }
    }

    override suspend fun fetchLikes(postIds: List<String>): Result<List<FeedRemoteLike>> {
        likeRequests += postIds
        return likesFailure.asFailureOr { likes.filter { it.postId in postIds } }
    }

    override suspend fun fetchProfiles(profileIds: List<String>): Result<List<FeedRemoteProfile>> {
        profileRequests += profileIds
        return profilesFailure.asFailureOr { profiles.filter { it.id in profileIds } }
    }

    override suspend fun currentUserId(): Result<String?> = currentUserFailure.asFailureOr { currentUserId }
}

private fun <T> Throwable?.asFailureOr(value: () -> T): Result<T> =
    if (this != null) Result.failure(this) else Result.success(value())
