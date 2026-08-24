package com.quata.feature.feed.presentation

import com.quata.core.common.AppDispatchers
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.feature.feed.domain.FeedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedOptimisticCommentsTest {
    @Test
    fun keepsLocalPendingCommentWhenBackendReadIsStale() {
        val stale = post(comments = listOf(remoteComment("seed", "Seed")))
        val existing = stale.copy(comments = stale.comments + localComment("local_1", "Reply", replyToCommentId = "seed"))

        val reconciled = stale.withLocalPendingCommentsFrom(existing)

        assertEquals(listOf("Seed", "Reply"), reconciled.comments.map(PostComment::message))
    }

    @Test
    fun dropsLocalPendingCommentWhenBackendReturnsEquivalentRemoteComment() {
        val remote = post(
            comments = listOf(
                remoteComment("seed", "Seed"),
                remoteComment("remote_1", "Reply", replyToCommentId = "seed"),
            )
        )
        val existing = post(
            comments = listOf(
                remoteComment("seed", "Seed"),
                localComment("local_1", " Reply ", replyToCommentId = "seed"),
            )
        )

        val reconciled = remote.withLocalPendingCommentsFrom(existing)

        assertEquals(listOf("seed", "remote_1"), reconciled.comments.map(PostComment::id))
    }

    @Test
    fun removesLocalPendingCommentWhenBackendMutationFails() {
        val pending = localComment("local_1", "😀 rollback", replyToCommentId = "seed")
        val existing = post(
            comments = listOf(
                remoteComment("seed", "Seed"),
                pending,
            )
        )

        val rolledBack = existing.withoutLocalPendingComment(pending)

        assertEquals(listOf("seed"), rolledBack.comments.map(PostComment::id))
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `view model rolls back forced emoji comment failure and surfaces error`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val seed = post(comments = listOf(remoteComment("seed", "Seed")))
        val repository = FailingCommentFeedRepository(seed, IllegalStateException("forced emoji rollback"))
        val viewModel = FeedViewModel(
            repository,
            AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
        )
        advanceUntilIdle()

        val pending = localComment("local_rollback", "😀 rollback", replyToCommentId = "seed")
        viewModel.onEvent(FeedUiEvent.AddComment(seed.id, pending))
        assertEquals(listOf("seed", "local_rollback"), viewModel.uiState.value.posts.single().comments.map(PostComment::id))

        advanceUntilIdle()

        assertEquals(listOf("seed"), viewModel.uiState.value.posts.single().comments.map(PostComment::id))
        assertTrue(viewModel.uiState.value.error?.contains("forced emoji rollback") == true)
        assertTrue(viewModel.uiState.value.commentErrorsByPostId[seed.id]?.contains("forced emoji rollback") == true)
        viewModel.close()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `view model confirms local comment when backend success omits updated post`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val seed = post(comments = listOf(remoteComment("seed", "Seed")))
        val repository = NullSuccessCommentFeedRepository(seed)
        val viewModel = FeedViewModel(
            repository,
            AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
        )
        advanceUntilIdle()

        val pending = localComment("local_confirmed", "😀 confirmed", replyToCommentId = "seed")
        viewModel.onEvent(FeedUiEvent.AddComment(seed.id, pending))
        advanceUntilIdle()

        assertEquals(listOf("seed", "local_confirmed"), viewModel.uiState.value.posts.single().comments.map(PostComment::id))
        assertTrue("local_confirmed" in viewModel.uiState.value.confirmedCommentIds)
        assertEquals(null, viewModel.uiState.value.commentErrorsByPostId[seed.id])
        val secondPending = localComment("local_confirmed_second", "😀 confirmed again", replyToCommentId = "seed")
        viewModel.onEvent(FeedUiEvent.AddComment(seed.id, secondPending))
        advanceUntilIdle()
        assertEquals(setOf("local_confirmed", "local_confirmed_second"), viewModel.uiState.value.confirmedCommentIds)
        viewModel.onEvent(FeedUiEvent.ConfirmedCommentConsumed("local_confirmed"))
        assertEquals(setOf("local_confirmed_second"), viewModel.uiState.value.confirmedCommentIds)
        viewModel.onEvent(FeedUiEvent.ConfirmedCommentConsumed("local_confirmed_second"))
        assertEquals(emptySet(), viewModel.uiState.value.confirmedCommentIds)
        viewModel.close()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `view model confirms local comment when backend returns post retaining local id`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val seed = post(comments = listOf(remoteComment("seed", "Seed")))
        val repository = EchoLocalSuccessCommentFeedRepository(seed)
        val viewModel = FeedViewModel(
            repository,
            AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
        )
        advanceUntilIdle()

        val pending = localComment("local_echo", "😀 echoed", replyToCommentId = "seed")
        viewModel.onEvent(FeedUiEvent.AddComment(seed.id, pending))
        advanceUntilIdle()

        assertEquals(listOf("seed", "local_echo"), viewModel.uiState.value.posts.single().comments.map(PostComment::id))
        assertEquals(setOf("local_echo"), viewModel.uiState.value.confirmedCommentIds)
        assertEquals(emptyMap(), viewModel.uiState.value.commentErrorsByCommentId)
        viewModel.close()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `view model correlates forced comment failure by comment id`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val seed = post(comments = listOf(remoteComment("seed", "Seed")))
        val repository = FailingCommentFeedRepository(seed, IllegalStateException("forced id-scoped rollback"))
        val viewModel = FeedViewModel(
            repository,
            AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
        )
        advanceUntilIdle()

        val pending = localComment("local_failed", "😀 failed", replyToCommentId = "seed")
        viewModel.onEvent(FeedUiEvent.AddComment(seed.id, pending))
        advanceUntilIdle()

        assertEquals(setOf("local_failed"), viewModel.uiState.value.commentErrorsByCommentId.keys)
        assertTrue(viewModel.uiState.value.commentErrorsByCommentId["local_failed"]?.contains("forced id-scoped rollback") == true)
        viewModel.close()
    }

    private fun post(comments: List<PostComment>) = Post(
        id = "post-1",
        author = User(id = "author", email = "author@example.test", displayName = "Author", neighborhood = "Centro"),
        text = "Post",
        createdAt = "2026-08-17T20:00:00Z",
        comments = comments,
    )

    private fun remoteComment(id: String, message: String, replyToCommentId: String? = null) = PostComment(
        id = id,
        authorName = "Gabrielu",
        message = message,
        timestamp = "2026-08-17T20:00:00Z",
        replyToAuthorName = replyToCommentId?.let { "Gabrielo" },
        replyToCommentId = replyToCommentId,
    )

    private fun localComment(id: String, message: String, replyToCommentId: String? = null) = PostComment(
        id = id,
        authorName = "You",
        message = message,
        timestamp = "now",
        replyToAuthorName = replyToCommentId?.let { "Gabrielo" },
        replyToCommentId = replyToCommentId,
    )

    private class FailingCommentFeedRepository(
        private val post: Post,
        private val failure: Throwable,
    ) : FeedRepository {
        override fun observeFeed(): Flow<Result<List<Post>>> = flowOf(Result.success(listOf(post)))
        override suspend fun getFeed(): Result<List<Post>> = Result.success(listOf(post))
        override suspend fun refreshFeed(): Result<List<Post>> = Result.success(listOf(post))
        override suspend fun loadOlderFeedPage(beforeCreatedAt: String?, limit: Int): Result<List<Post>> = Result.success(emptyList())
        override suspend fun refreshCurrentUser(): Result<User?> = Result.success(null)
        override suspend fun refreshAuthor(userId: String): Result<User?> = Result.success(post.author)
        override suspend fun refreshPost(postId: String): Result<Post?> = Result.success(post.takeIf { it.id == postId })
        override suspend fun toggleLike(postId: String): Result<Post?> = Result.success(post)
        override suspend fun reportPost(postId: String): Result<Post?> = Result.success(post)
        override suspend fun addComment(postId: String, comment: PostComment): Result<Post?> = Result.failure(failure)
        override suspend fun deletePost(postId: String): Result<Unit> = Result.success(Unit)
    }

    private class NullSuccessCommentFeedRepository(
        private val post: Post,
    ) : FeedRepository {
        override fun observeFeed(): Flow<Result<List<Post>>> = flowOf(Result.success(listOf(post)))
        override suspend fun getFeed(): Result<List<Post>> = Result.success(listOf(post))
        override suspend fun refreshFeed(): Result<List<Post>> = Result.success(listOf(post))
        override suspend fun loadOlderFeedPage(beforeCreatedAt: String?, limit: Int): Result<List<Post>> = Result.success(emptyList())
        override suspend fun refreshCurrentUser(): Result<User?> = Result.success(null)
        override suspend fun refreshAuthor(userId: String): Result<User?> = Result.success(post.author)
        override suspend fun refreshPost(postId: String): Result<Post?> = Result.success(post.takeIf { it.id == postId })
        override suspend fun toggleLike(postId: String): Result<Post?> = Result.success(post)
        override suspend fun reportPost(postId: String): Result<Post?> = Result.success(post)
        override suspend fun addComment(postId: String, comment: PostComment): Result<Post?> = Result.success(null)
        override suspend fun deletePost(postId: String): Result<Unit> = Result.success(Unit)
    }

    private class EchoLocalSuccessCommentFeedRepository(
        private val post: Post,
    ) : FeedRepository {
        override fun observeFeed(): Flow<Result<List<Post>>> = flowOf(Result.success(listOf(post)))
        override suspend fun getFeed(): Result<List<Post>> = Result.success(listOf(post))
        override suspend fun refreshFeed(): Result<List<Post>> = Result.success(listOf(post))
        override suspend fun loadOlderFeedPage(beforeCreatedAt: String?, limit: Int): Result<List<Post>> = Result.success(emptyList())
        override suspend fun refreshCurrentUser(): Result<User?> = Result.success(null)
        override suspend fun refreshAuthor(userId: String): Result<User?> = Result.success(post.author)
        override suspend fun refreshPost(postId: String): Result<Post?> = Result.success(post.takeIf { it.id == postId })
        override suspend fun toggleLike(postId: String): Result<Post?> = Result.success(post)
        override suspend fun reportPost(postId: String): Result<Post?> = Result.success(post)
        override suspend fun addComment(postId: String, comment: PostComment): Result<Post?> =
            Result.success(post.copy(comments = post.comments + comment))
        override suspend fun deletePost(postId: String): Result<Unit> = Result.success(Unit)
    }
}
