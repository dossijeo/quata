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
class OfficialFeedPaginationRecoveryTest {
    @Test
    fun failedOlderPageKeepsContentAndRetriesTheSameCursorOnlyWhenRequested() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val initial = (0 until 50).map(::post)
        val repository = RecoveringOlderPageRepository(initial)
        val viewModel = OfficialFeedViewModel(
            repository = repository,
            dispatchers = AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
        )
        try {
            advanceUntilIdle()
            assertEquals(initial.map(OfficialPostItem::id), viewModel.uiState.value.posts.map(OfficialPostItem::id))
            assertTrue(viewModel.uiState.value.hasMoreOlderPosts)

            viewModel.onEvent(OfficialFeedUiEvent.LoadOlderPage)
            advanceUntilIdle()

            val failed = viewModel.uiState.value
            assertEquals(initial.map(OfficialPostItem::id), failed.posts.map(OfficialPostItem::id))
            assertEquals("forced older page failure", failed.olderPageError)
            assertNull(failed.error)
            assertTrue(failed.hasMoreOlderPosts)
            assertEquals(1, repository.cursors.size)

            viewModel.onEvent(OfficialFeedUiEvent.RetryOlderPage)
            advanceUntilIdle()

            val recovered = viewModel.uiState.value
            assertNull(recovered.olderPageError)
            assertEquals((initial + post(50)).map(OfficialPostItem::id), recovered.posts.map(OfficialPostItem::id))
            assertFalse(recovered.hasMoreOlderPosts)
            assertEquals(2, repository.cursors.size)
            assertEquals(repository.cursors.first(), repository.cursors.last())
        } finally {
            viewModel.close()
        }
    }

    private class RecoveringOlderPageRepository(
        private val initial: List<OfficialPostItem>,
    ) : OfficialRepository {
        val cursors = mutableListOf<OfficialFeedCursor>()

        override fun observeOfficialFeed(): Flow<Result<List<OfficialPostItem>>> = flowOf(Result.success(initial))
        override suspend fun getOfficialFeed() = Result.success(initial)
        override suspend fun refreshOfficialFeed() = Result.success(initial)
        override suspend fun loadOlderOfficialFeedPage(cursor: OfficialFeedCursor, limit: Int): Result<List<OfficialPostItem>> {
            cursors += cursor
            return if (cursors.size == 1) {
                Result.failure(IllegalStateException("forced older page failure"))
            } else {
                Result.success(listOf(post(50)))
            }
        }
        override suspend fun getOfficialPost(postId: String) = Result.success<OfficialPostItem?>(null)
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
            createdAt = "2026-09-01T00:00:${(59 - index).toString().padStart(2, '0')}Z",
        )
    }
}
