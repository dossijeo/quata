package com.quata.feature.feed.presentation

import com.quata.core.common.AppDispatchers
import com.quata.core.model.Post
import com.quata.core.model.User
import com.quata.feature.feed.domain.FeedCursor
import com.quata.feature.feed.domain.FeedReadRepository
import com.quata.feature.feed.domain.ReadOnlyFeedRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FeedPaginationRecoveryTest {
    @Test
    fun failedOlderPageKeepsContentRetriesSameCursorAndExhaustsAllPages() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = RecoveringFeedRepository()
        val viewModel = FeedViewModel(
            repository = ReadOnlyFeedRepository(repository),
            dispatchers = AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
        )
        try {
            advanceUntilIdle()
            assertEquals(50, viewModel.uiState.value.posts.size)
            assertTrue(viewModel.uiState.value.hasMoreOlderPosts)

            viewModel.onEvent(FeedUiEvent.LoadOlderPage)
            advanceUntilIdle()

            val failed = viewModel.uiState.value
            assertEquals(50, failed.posts.size)
            assertEquals("forced feed page failure", failed.olderPageError)
            assertNull(failed.error)
            assertTrue(failed.hasMoreOlderPosts)
            assertEquals(1, repository.cursors.size)

            viewModel.onEvent(FeedUiEvent.RetryOlderPage)
            advanceUntilIdle()

            val recovered = viewModel.uiState.value
            assertEquals(100, recovered.posts.size)
            assertNull(recovered.olderPageError)
            assertTrue(recovered.hasMoreOlderPosts)
            assertEquals(repository.cursors[0], repository.cursors[1])

            viewModel.onEvent(FeedUiEvent.LoadOlderPage)
            advanceUntilIdle()

            val exhausted = viewModel.uiState.value
            assertEquals((0..100).map { "feed-$it" }, exhausted.posts.map(Post::id))
            assertFalse(exhausted.hasMoreOlderPosts)
            assertNull(exhausted.olderPageError)
            assertEquals(listOf("feed-49", "feed-49", "feed-99"), repository.cursors.map(FeedCursor::postId))
        } finally {
            viewModel.close()
        }
    }

    private class RecoveringFeedRepository : FeedReadRepository {
        private val allPosts = (0..100).map(::post)
        private val first = allPosts.subList(0, 50)
        private val second = allPosts.subList(50, 100)
        private val final = allPosts.subList(100, 101)
        private var failOnce = true
        val cursors = mutableListOf<FeedCursor>()

        override fun observeFeed(): Flow<Result<List<Post>>> = flowOf(Result.success(first))
        override suspend fun getFeed() = Result.success(first)
        override suspend fun refreshFeed() = Result.success(first)
        override suspend fun loadOlderFeedPage(cursor: FeedCursor, limit: Int): Result<List<Post>> {
            cursors += cursor
            check(limit == 50)
            return when (cursor.postId) {
                "feed-49" -> if (failOnce) {
                    failOnce = false
                    Result.failure(IllegalStateException("forced feed page failure"))
                } else Result.success(second)
                "feed-99" -> Result.success(final)
                else -> Result.failure(IllegalStateException("unexpected feed cursor ${cursor.postId}"))
            }
        }
        override suspend fun refreshCurrentUser() = Result.success<User?>(null)
        override suspend fun refreshAuthor(userId: String) = Result.success<User?>(null)
        override suspend fun refreshPost(postId: String) = Result.success(allPosts.firstOrNull { it.id == postId })
    }

    private companion object {
        fun post(index: Int) = Post(
            id = "feed-$index",
            author = User("author-$index", "author-$index@example.invalid", "Author $index"),
            text = "Feed post $index",
            createdAt = "2026-09-${(30 - index / 24).toString().padStart(2, '0')}T${(23 - index % 24).toString().padStart(2, '0')}:00:00Z",
        )
    }
}
