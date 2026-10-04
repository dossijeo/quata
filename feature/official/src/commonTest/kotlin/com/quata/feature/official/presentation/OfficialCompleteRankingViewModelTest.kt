package com.quata.feature.official.presentation

import com.quata.core.common.AppDispatchers
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.feature.official.domain.OfficialFeedCursor
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialRepository
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
class OfficialCompleteRankingViewModelTest {
    @Test
    fun loadCompleteRankingPublishesAllRowsWithoutReplacingTheVisiblePager() = runTest {
        val initial = (0 until 50).map(::post)
        val repository = RankingViewModelRepository(initial, Result.success(listOf(post(50))))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = OfficialFeedViewModel(
            repository,
            AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
        )
        try {
            advanceUntilIdle()
            viewModel.onEvent(OfficialFeedUiEvent.LoadCompleteRanking)
            assertEquals(true, viewModel.uiState.value.isLoadingRanking)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals((0..50).map { "official-$it" }, state.rankingPosts?.map(OfficialPostItem::id))
            assertEquals(initial.map(OfficialPostItem::id), state.posts.map(OfficialPostItem::id))
            assertFalse(state.isLoadingRanking)
            assertNull(state.rankingError)
        } finally {
            viewModel.close()
        }
    }

    @Test
    fun rankingFailureKeepsTheVisiblePagerAndExposesRetryState() = runTest {
        val initial = (0 until 50).map(::post)
        val repository = RankingViewModelRepository(
            initial,
            Result.failure(IllegalStateException("forced ranking failure")),
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = OfficialFeedViewModel(
            repository,
            AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
        )
        try {
            advanceUntilIdle()
            viewModel.onEvent(OfficialFeedUiEvent.LoadCompleteRanking)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(initial.map(OfficialPostItem::id), state.posts.map(OfficialPostItem::id))
            assertNull(state.rankingPosts)
            assertFalse(state.isLoadingRanking)
            assertEquals("forced ranking failure", state.rankingError)

            repository.older = Result.success(listOf(post(50)))
            viewModel.onEvent(OfficialFeedUiEvent.LoadCompleteRanking)
            advanceUntilIdle()

            val recovered = viewModel.uiState.value
            assertEquals((0..50).map { "official-$it" }, recovered.rankingPosts?.map(OfficialPostItem::id))
            assertFalse(recovered.isLoadingRanking)
            assertNull(recovered.rankingError)
        } finally {
            viewModel.close()
        }
    }

    @Test
    fun aNewRankingSelectionRetriesATerminalExactPostLoad() = runTest {
        val initial = listOf(post(0))
        val target = post(50)
        val repository = RankingViewModelRepository(
            initial,
            Result.success(emptyList()),
            ArrayDeque(
                listOf(
                    Result.failure(IllegalStateException("forced exact failure")),
                    Result.failure(IllegalStateException("forced exact failure")),
                    Result.failure(IllegalStateException("forced exact failure")),
                    Result.failure(IllegalStateException("forced exact failure")),
                    Result.success(null),
                    Result.success(null),
                    Result.success(null),
                    Result.success(null),
                    Result.success(target),
                ),
            ),
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = OfficialFeedViewModel(
            repository,
            AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
        )
        try {
            advanceUntilIdle()
            viewModel.onEvent(OfficialFeedUiEvent.EnsurePostLoaded(target.id))
            assertEquals(OfficialFocusedPostLoad.Loading, viewModel.uiState.value.focusedPostLoads[target.id])
            advanceUntilIdle()
            assertEquals(OfficialFocusedPostLoad.Failed, viewModel.uiState.value.focusedPostLoads[target.id])

            viewModel.onEvent(OfficialFeedUiEvent.EnsurePostLoaded(target.id))
            assertEquals(OfficialFocusedPostLoad.Loading, viewModel.uiState.value.focusedPostLoads[target.id])
            advanceUntilIdle()

            assertEquals(OfficialFocusedPostLoad.NotFound, viewModel.uiState.value.focusedPostLoads[target.id])
            viewModel.onEvent(OfficialFeedUiEvent.EnsurePostLoaded(target.id))
            assertEquals(OfficialFocusedPostLoad.Loading, viewModel.uiState.value.focusedPostLoads[target.id])
            advanceUntilIdle()

            assertEquals(OfficialFocusedPostLoad.Loaded, viewModel.uiState.value.focusedPostLoads[target.id])
            assertTrue(viewModel.uiState.value.posts.any { it.id == target.id })
        } finally {
            viewModel.close()
        }
    }

    private class RankingViewModelRepository(
        private val initial: List<OfficialPostItem>,
        var older: Result<List<OfficialPostItem>>,
        private val exactResults: ArrayDeque<Result<OfficialPostItem?>> = ArrayDeque(),
    ) : OfficialRepository {
        override fun observeOfficialFeed(): Flow<Result<List<OfficialPostItem>>> = flowOf(Result.success(initial))
        override suspend fun getOfficialFeed() = Result.success(initial)
        override suspend fun refreshOfficialFeed() = Result.success(initial)
        override suspend fun loadOlderOfficialFeedPage(cursor: OfficialFeedCursor, limit: Int) = older
        override suspend fun getOfficialPost(postId: String) =
            exactResults.removeFirstOrNull() ?: Result.success<OfficialPostItem?>(null)
        override suspend fun refreshCurrentUser() = Result.success<User?>(null)
        override suspend fun createPost(draft: OfficialPostDraft) = Result.success<OfficialPostItem?>(null)
        override suspend fun deletePost(postId: String) = Result.success(Unit)
        override suspend fun toggleLike(postId: String) = Result.success<OfficialPostItem?>(null)
        override suspend fun addComment(postId: String, comment: PostComment) = Result.success<OfficialPostItem?>(null)
        override suspend fun reportComment(commentId: String) = Result.success(Unit)
    }

    private companion object {
        val author = User("official-author", "official@example.invalid", "Official")

        fun post(index: Int) = OfficialPostItem(
            id = "official-$index",
            author = author,
            title = "Official $index",
            summary = "Summary $index",
            contentHtml = "<p>Body $index</p>",
            contentPlain = "Body $index",
            createdAt = "2026-10-04T00:${(59 - index.coerceAtMost(59)).toString().padStart(2, '0')}:00Z",
        )
    }
}
