package com.quata.feature.official.domain

import com.quata.core.model.PostComment
import com.quata.core.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfficialCompleteRankingTest {
    @Test
    fun exhaustsEveryTotalOrderPageFromAFreshFirstPage() = runTest {
        val repository = RankingRepository(
            first = listOf(post(0), post(1)),
            older = ArrayDeque(listOf(
                Result.success(listOf(post(2), post(3))),
                Result.success(listOf(post(4))),
            )),
        )

        val result = repository.loadCompleteOfficialRanking(pageSize = 2).getOrThrow()

        assertEquals((0..4).map { "official-$it" }, result.map(OfficialPostItem::id))
        assertEquals(1, repository.refreshCalls)
        assertEquals(listOf("official-1", "official-3"), repository.cursors.map(OfficialFeedCursor::postId))
        assertEquals(listOf(2, 2), repository.limits)
    }

    @Test
    fun failsClosedWhenPagesOverlap() = runTest {
        val repository = RankingRepository(
            first = listOf(post(0), post(1)),
            older = ArrayDeque(listOf(Result.success(listOf(post(1))))),
        )

        val failure = repository.loadCompleteOfficialRanking(pageSize = 2).exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("official_ranking_duplicate_post"))
    }

    @Test
    fun propagatesAnOlderPageFailureWithoutReturningAPartialRanking() = runTest {
        val repository = RankingRepository(
            first = listOf(post(0), post(1)),
            older = ArrayDeque(listOf(Result.failure(IllegalStateException("forced ranking failure")))),
        )

        val result = repository.loadCompleteOfficialRanking(pageSize = 2)

        assertTrue(result.isFailure)
        assertEquals("forced ranking failure", result.exceptionOrNull()?.message)
    }

    private class RankingRepository(
        private val first: List<OfficialPostItem>,
        private val older: ArrayDeque<Result<List<OfficialPostItem>>>,
    ) : OfficialRepository {
        var refreshCalls = 0
        val cursors = mutableListOf<OfficialFeedCursor>()
        val limits = mutableListOf<Int>()

        override fun observeOfficialFeed(): Flow<Result<List<OfficialPostItem>>> = flowOf(Result.success(first))
        override suspend fun getOfficialFeed() = Result.success(first)
        override suspend fun refreshOfficialFeed(): Result<List<OfficialPostItem>> {
            refreshCalls += 1
            return Result.success(first)
        }
        override suspend fun loadOlderOfficialFeedPage(cursor: OfficialFeedCursor, limit: Int): Result<List<OfficialPostItem>> {
            cursors += cursor
            limits += limit
            return older.removeFirstOrNull() ?: Result.success(emptyList())
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
            createdAt = "2026-10-04T00:00:${(59 - index).toString().padStart(2, '0')}Z",
        )
    }
}
