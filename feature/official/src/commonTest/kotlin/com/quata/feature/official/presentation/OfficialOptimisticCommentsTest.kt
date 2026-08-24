package com.quata.feature.official.presentation

import com.quata.core.common.AppDispatchers
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostType
import com.quata.feature.official.domain.OfficialRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfficialOptimisticCommentsTest {
    @Test
    fun keepsLocalPendingCommentAndCountWhenBackendReadIsStale() {
        val stale = post(comments = listOf(remoteComment("seed", "Seed")))
        val existing = stale.copy(
            comments = stale.comments + localComment("local_1", "Reply", replyToCommentId = "seed"),
            commentsCount = 2,
        )

        val reconciled = stale.withLocalPendingCommentsFrom(existing)

        assertEquals(listOf("Seed", "Reply"), reconciled.comments.map(PostComment::message))
        assertEquals(2, reconciled.commentsCount)
    }

    @Test
    fun dropsLocalPendingCommentWhenBackendReturnsEquivalentRemoteComment() {
        val remote = post(
            comments = listOf(
                remoteComment("seed", "Seed"),
                remoteComment("remote_1", "Reply", replyToCommentId = "seed"),
            ),
            commentsCount = 2,
        )
        val existing = post(
            comments = listOf(
                remoteComment("seed", "Seed"),
                localComment("local_1", " Reply ", replyToCommentId = "seed"),
            ),
            commentsCount = 2,
        )

        val reconciled = remote.withLocalPendingCommentsFrom(existing)

        assertEquals(listOf("seed", "remote_1"), reconciled.comments.map(PostComment::id))
        assertEquals(2, reconciled.commentsCount)
    }

    @Test
    fun removesLocalPendingCommentAndCountWhenBackendMutationFails() {
        val pending = localComment("local_1", "😀 rollback", replyToCommentId = "seed")
        val existing = post(
            comments = listOf(
                remoteComment("seed", "Seed"),
                pending,
            ),
            commentsCount = 2,
        )

        val rolledBack = existing.withoutLocalPendingComment(pending)

        assertEquals(listOf("seed"), rolledBack.comments.map(PostComment::id))
        assertEquals(1, rolledBack.commentsCount)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `view model rolls back forced emoji comment failure and surfaces error`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val seed = post(comments = listOf(remoteComment("seed", "Seed")), commentsCount = 1)
        val repository = FailingCommentOfficialRepository(seed, IllegalStateException("forced official emoji rollback"))
        val viewModel = OfficialFeedViewModel(
            repository,
            AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
        )
        advanceUntilIdle()

        val pending = localComment("local_rollback", "😀 rollback", replyToCommentId = "seed")
        viewModel.onEvent(OfficialFeedUiEvent.AddComment(seed.id, pending))
        assertEquals(listOf("seed", "local_rollback"), viewModel.uiState.value.posts.single().comments.map(PostComment::id))
        assertEquals(2, viewModel.uiState.value.posts.single().commentsCount)

        advanceUntilIdle()

        assertEquals(listOf("seed"), viewModel.uiState.value.posts.single().comments.map(PostComment::id))
        assertEquals(1, viewModel.uiState.value.posts.single().commentsCount)
        assertTrue(viewModel.uiState.value.error?.contains("forced official emoji rollback") == true)
        assertTrue(viewModel.uiState.value.commentErrorsByPostId[seed.id]?.contains("forced official emoji rollback") == true)
        viewModel.close()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `view model confirms local comment when backend success omits updated post`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val seed = post(comments = listOf(remoteComment("seed", "Seed")), commentsCount = 1)
        val repository = NullSuccessCommentOfficialRepository(seed)
        val viewModel = OfficialFeedViewModel(
            repository,
            AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
        )
        advanceUntilIdle()

        val pending = localComment("local_confirmed", "😀 confirmed", replyToCommentId = "seed")
        viewModel.onEvent(OfficialFeedUiEvent.AddComment(seed.id, pending))
        advanceUntilIdle()

        assertEquals(listOf("seed", "local_confirmed"), viewModel.uiState.value.posts.single().comments.map(PostComment::id))
        assertEquals(2, viewModel.uiState.value.posts.single().commentsCount)
        assertTrue("local_confirmed" in viewModel.uiState.value.confirmedCommentIds)
        assertEquals(null, viewModel.uiState.value.commentErrorsByPostId[seed.id])
        val secondPending = localComment("local_confirmed_second", "😀 confirmed again", replyToCommentId = "seed")
        viewModel.onEvent(OfficialFeedUiEvent.AddComment(seed.id, secondPending))
        advanceUntilIdle()
        assertEquals(setOf("local_confirmed", "local_confirmed_second"), viewModel.uiState.value.confirmedCommentIds)
        viewModel.onEvent(OfficialFeedUiEvent.ConfirmedCommentConsumed("local_confirmed"))
        assertEquals(setOf("local_confirmed_second"), viewModel.uiState.value.confirmedCommentIds)
        viewModel.onEvent(OfficialFeedUiEvent.ConfirmedCommentConsumed("local_confirmed_second"))
        assertEquals(emptySet(), viewModel.uiState.value.confirmedCommentIds)
        viewModel.close()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `view model confirms local comment when backend returns post retaining local id`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val seed = post(comments = listOf(remoteComment("seed", "Seed")), commentsCount = 1)
        val repository = EchoLocalSuccessCommentOfficialRepository(seed)
        val viewModel = OfficialFeedViewModel(
            repository,
            AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
        )
        advanceUntilIdle()

        val pending = localComment("local_echo", "😀 echoed", replyToCommentId = "seed")
        viewModel.onEvent(OfficialFeedUiEvent.AddComment(seed.id, pending))
        advanceUntilIdle()

        assertEquals(listOf("seed", "local_echo"), viewModel.uiState.value.posts.single().comments.map(PostComment::id))
        assertEquals(2, viewModel.uiState.value.posts.single().commentsCount)
        assertEquals(setOf("local_echo"), viewModel.uiState.value.confirmedCommentIds)
        assertEquals(emptyMap(), viewModel.uiState.value.commentErrorsByCommentId)
        viewModel.close()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `view model correlates forced comment failure by comment id`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val seed = post(comments = listOf(remoteComment("seed", "Seed")), commentsCount = 1)
        val repository = FailingCommentOfficialRepository(seed, IllegalStateException("forced official id-scoped rollback"))
        val viewModel = OfficialFeedViewModel(
            repository,
            AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
        )
        advanceUntilIdle()

        val pending = localComment("local_failed", "😀 failed", replyToCommentId = "seed")
        viewModel.onEvent(OfficialFeedUiEvent.AddComment(seed.id, pending))
        advanceUntilIdle()

        assertEquals(setOf("local_failed"), viewModel.uiState.value.commentErrorsByCommentId.keys)
        assertTrue(viewModel.uiState.value.commentErrorsByCommentId["local_failed"]?.contains("forced official id-scoped rollback") == true)
        viewModel.close()
    }

    private fun post(comments: List<PostComment>, commentsCount: Int = comments.size) = OfficialPostItem(
        id = "official-1",
        author = User(id = "author", email = "official@example.test", displayName = "Official", neighborhood = "Centro"),
        title = "Title",
        summary = "Summary",
        contentHtml = "<p>Body</p>",
        contentPlain = "Body",
        type = OfficialPostType.Announcement,
        createdAt = "2026-08-17T20:00:00Z",
        commentsCount = commentsCount,
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

    private class FailingCommentOfficialRepository(
        private val post: OfficialPostItem,
        private val failure: Throwable,
    ) : OfficialRepository {
        override fun observeOfficialFeed(): Flow<Result<List<OfficialPostItem>>> = flowOf(Result.success(listOf(post)))
        override suspend fun getOfficialFeed(): Result<List<OfficialPostItem>> = Result.success(listOf(post))
        override suspend fun refreshOfficialFeed(): Result<List<OfficialPostItem>> = Result.success(listOf(post))
        override suspend fun loadOlderOfficialFeedPage(beforePublishedAt: String?, limit: Int): Result<List<OfficialPostItem>> = Result.success(emptyList())
        override suspend fun getOfficialPost(postId: String): Result<OfficialPostItem?> = Result.success(post.takeIf { it.id == postId })
        override suspend fun refreshCurrentUser(): Result<User?> = Result.success(null)
        override suspend fun createPost(draft: OfficialPostDraft): Result<OfficialPostItem?> = Result.success(post)
        override suspend fun deletePost(postId: String): Result<Unit> = Result.success(Unit)
        override suspend fun toggleLike(postId: String): Result<OfficialPostItem?> = Result.success(post)
        override suspend fun addComment(postId: String, comment: PostComment): Result<OfficialPostItem?> = Result.failure(failure)
        override suspend fun reportComment(commentId: String): Result<Unit> = Result.success(Unit)
    }

    private class NullSuccessCommentOfficialRepository(
        private val post: OfficialPostItem,
    ) : OfficialRepository {
        override fun observeOfficialFeed(): Flow<Result<List<OfficialPostItem>>> = flowOf(Result.success(listOf(post)))
        override suspend fun getOfficialFeed(): Result<List<OfficialPostItem>> = Result.success(listOf(post))
        override suspend fun refreshOfficialFeed(): Result<List<OfficialPostItem>> = Result.success(listOf(post))
        override suspend fun loadOlderOfficialFeedPage(beforePublishedAt: String?, limit: Int): Result<List<OfficialPostItem>> = Result.success(emptyList())
        override suspend fun getOfficialPost(postId: String): Result<OfficialPostItem?> = Result.success(post.takeIf { it.id == postId })
        override suspend fun refreshCurrentUser(): Result<User?> = Result.success(null)
        override suspend fun createPost(draft: OfficialPostDraft): Result<OfficialPostItem?> = Result.success(post)
        override suspend fun deletePost(postId: String): Result<Unit> = Result.success(Unit)
        override suspend fun toggleLike(postId: String): Result<OfficialPostItem?> = Result.success(post)
        override suspend fun addComment(postId: String, comment: PostComment): Result<OfficialPostItem?> = Result.success(null)
        override suspend fun reportComment(commentId: String): Result<Unit> = Result.success(Unit)
    }

    private class EchoLocalSuccessCommentOfficialRepository(
        private val post: OfficialPostItem,
    ) : OfficialRepository {
        override fun observeOfficialFeed(): Flow<Result<List<OfficialPostItem>>> = flowOf(Result.success(listOf(post)))
        override suspend fun getOfficialFeed(): Result<List<OfficialPostItem>> = Result.success(listOf(post))
        override suspend fun refreshOfficialFeed(): Result<List<OfficialPostItem>> = Result.success(listOf(post))
        override suspend fun loadOlderOfficialFeedPage(beforePublishedAt: String?, limit: Int): Result<List<OfficialPostItem>> = Result.success(emptyList())
        override suspend fun getOfficialPost(postId: String): Result<OfficialPostItem?> = Result.success(post.takeIf { it.id == postId })
        override suspend fun refreshCurrentUser(): Result<User?> = Result.success(null)
        override suspend fun createPost(draft: OfficialPostDraft): Result<OfficialPostItem?> = Result.success(post)
        override suspend fun deletePost(postId: String): Result<Unit> = Result.success(Unit)
        override suspend fun toggleLike(postId: String): Result<OfficialPostItem?> = Result.success(post)
        override suspend fun addComment(postId: String, comment: PostComment): Result<OfficialPostItem?> =
            Result.success(post.copy(comments = post.comments + comment, commentsCount = post.commentsCount + 1))
        override suspend fun reportComment(commentId: String): Result<Unit> = Result.success(Unit)
    }
}
