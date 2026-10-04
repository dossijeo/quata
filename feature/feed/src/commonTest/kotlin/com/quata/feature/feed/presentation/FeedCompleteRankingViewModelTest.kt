package com.quata.feature.feed.presentation

import com.quata.core.common.AppDispatchers
import com.quata.core.model.Post
import com.quata.core.model.User
import com.quata.feature.feed.domain.FeedCursor
import com.quata.feature.feed.domain.FeedReadRepository
import com.quata.feature.feed.domain.ReadOnlyFeedRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class FeedCompleteRankingViewModelTest {
    @Test
    fun completeSnapshotDoesNotReplaceVisiblePager() = runTest {
        val visible = listOf(post("visible"))
        val first = (0 until 50).map { post("rank-$it") }
        val read = RankingViewModelReadRepository(visible, ArrayDeque(listOf(
            Result.success(first),
            Result.success(listOf(post("rank-50"))),
        )))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val model = FeedViewModel(ReadOnlyFeedRepository(read), AppDispatchers(dispatcher, dispatcher, dispatcher))
        try {
            advanceUntilIdle()
            model.onEvent(FeedUiEvent.LoadCompleteRanking)
            advanceUntilIdle()

            val state = model.uiState.value
            assertEquals(listOf("visible"), state.posts.map(Post::id))
            assertEquals((0..50).map { "rank-$it" }, state.rankingPosts?.map(Post::id))
            assertFalse(state.isLoadingRanking)
            assertNull(state.rankingError)
        } finally { model.close() }
    }

    @Test
    fun failedSnapshotExposesRetryAndThenRecovers() = runTest {
        val visible = listOf(post("visible"))
        val read = RankingViewModelReadRepository(visible, ArrayDeque(listOf(
            Result.failure(IllegalStateException("forced ranking failure")),
            Result.success(listOf(post("recovered"))),
        )))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val model = FeedViewModel(ReadOnlyFeedRepository(read), AppDispatchers(dispatcher, dispatcher, dispatcher))
        try {
            advanceUntilIdle()
            model.onEvent(FeedUiEvent.LoadCompleteRanking)
            advanceUntilIdle()
            assertEquals("forced ranking failure", model.uiState.value.rankingError)
            assertNull(model.uiState.value.rankingPosts)

            model.onEvent(FeedUiEvent.LoadCompleteRanking)
            advanceUntilIdle()
            assertEquals(listOf("recovered"), model.uiState.value.rankingPosts?.map(Post::id))
            assertNull(model.uiState.value.rankingError)
        } finally { model.close() }
    }

    private class RankingViewModelReadRepository(
        private val visible: List<Post>,
        private val refreshResults: ArrayDeque<Result<List<Post>>>,
    ) : FeedReadRepository {
        override fun observeFeed() = flowOf(Result.success(visible))
        override suspend fun getFeed() = Result.success(visible)
        override suspend fun refreshFeed() = refreshResults.removeFirstOrNull() ?: Result.success(emptyList())
        override suspend fun loadOlderFeedPage(cursor: FeedCursor, limit: Int) =
            refreshResults.removeFirstOrNull() ?: Result.success(emptyList())
        override suspend fun refreshCurrentUser() = Result.success<User?>(null)
        override suspend fun refreshAuthor(userId: String) = Result.success<User?>(null)
        override suspend fun refreshPost(postId: String) = Result.success<Post?>(null)
    }

    private companion object {
        val author = User("author", "author@example.invalid", "Author")
        fun post(id: String) = Post(id, author, id, createdAt = "2026-10-04T10:00:00Z")
    }
}
