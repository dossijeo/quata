package com.quata.feature.feed.presentation

import com.quata.core.common.AppDispatchers
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.feature.feed.domain.FeedRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FeedReportCommentResultTest {
    @Test
    fun confirmationExistsOnlyAfterTransportSuccessAndCanBeConsumed() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = ReportCommentRepository(Result.success(Unit))
        val viewModel = FeedViewModel(repository, AppDispatchers(dispatcher, dispatcher, dispatcher))
        advanceUntilIdle()

        viewModel.onEvent(FeedUiEvent.ReportComment(CommentId))
        assertTrue(viewModel.uiState.value.confirmedCommentReportIds.isEmpty())
        advanceUntilIdle()

        assertEquals(setOf(CommentId), viewModel.uiState.value.confirmedCommentReportIds)
        viewModel.onEvent(FeedUiEvent.ConfirmedCommentReportConsumed(CommentId))
        assertTrue(viewModel.uiState.value.confirmedCommentReportIds.isEmpty())
        viewModel.close()
    }

    @Test
    fun transportFailureNeverCreatesAConfirmation() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = ReportCommentRepository(Result.failure(IllegalStateException("report rejected")))
        val viewModel = FeedViewModel(repository, AppDispatchers(dispatcher, dispatcher, dispatcher))
        advanceUntilIdle()

        viewModel.onEvent(FeedUiEvent.ReportComment(CommentId))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.confirmedCommentReportIds.isEmpty())
        assertTrue(viewModel.uiState.value.error?.contains("report rejected") == true)
        viewModel.close()
    }

    @Test
    fun pendingConfirmationsAreRetainedUntilEachOneIsConsumed() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = ReportCommentRepository(Result.success(Unit))
        val viewModel = FeedViewModel(repository, AppDispatchers(dispatcher, dispatcher, dispatcher))
        advanceUntilIdle()
        val commentIds = (1..5).map { "comment-$it" }

        commentIds.forEach { viewModel.onEvent(FeedUiEvent.ReportComment(it)) }
        advanceUntilIdle()

        assertEquals(commentIds.toSet(), viewModel.uiState.value.confirmedCommentReportIds)
        commentIds.forEach { viewModel.onEvent(FeedUiEvent.ConfirmedCommentReportConsumed(it)) }
        assertTrue(viewModel.uiState.value.confirmedCommentReportIds.isEmpty())
        viewModel.close()
    }

    private class ReportCommentRepository(
        private val reportResult: Result<Unit>,
    ) : FeedRepository {
        private val post = Post(
            id = "post-1",
            author = User("author", "author@example.invalid", "Author"),
            text = "Report witness",
            createdAt = "2026-09-30T00:00:00Z",
        )

        override fun observeFeed(): Flow<Result<List<Post>>> = flowOf(Result.success(listOf(post)))
        override suspend fun getFeed() = Result.success(listOf(post))
        override suspend fun refreshFeed() = Result.success(listOf(post))
        override suspend fun loadOlderFeedPage(cursor: com.quata.feature.feed.domain.FeedCursor, limit: Int) = Result.success(emptyList<Post>())
        override suspend fun refreshCurrentUser() = Result.success<User?>(null)
        override suspend fun refreshAuthor(userId: String) = Result.success<User?>(null)
        override suspend fun refreshPost(postId: String) = Result.success(post.takeIf { it.id == postId })
        override suspend fun toggleLike(postId: String) = Result.success<Post?>(post)
        override suspend fun reportPost(postId: String) = Result.success<Post?>(post)
        override suspend fun reportComment(commentId: String) = reportResult
        override suspend fun addComment(postId: String, comment: PostComment) = Result.success<Post?>(post)
        override suspend fun deletePost(postId: String) = Result.success(Unit)
    }

    private companion object {
        const val CommentId = "comment-1"
    }
}
