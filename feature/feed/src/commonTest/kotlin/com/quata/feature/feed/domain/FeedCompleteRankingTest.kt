package com.quata.feature.feed.domain

import com.quata.core.model.Post
import com.quata.core.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class FeedCompleteRankingTest {
    @Test
    fun incompleteClientCursorFailsBeforeTransport() {
        assertFailsWith<IllegalArgumentException> { FeedCursor("", "feed-1") }
        assertFailsWith<IllegalArgumentException> { FeedCursor(EqualTimestamp, " ") }
    }

    @Test
    fun exhaustsEveryTotalOrderPageIncludingEqualTimestamps() = runTest {
        val read = RankingReadRepository(
            first = listOf(post(0), post(1)),
            older = ArrayDeque(listOf(
                Result.success(listOf(post(2), post(3))),
                Result.success(listOf(post(4))),
            )),
        )

        val result = ReadOnlyFeedRepository(read).loadCompleteFeedRanking(pageSize = 2).getOrThrow()

        assertEquals((0..4).map { "feed-$it" }, result.map(Post::id))
        assertEquals(1, read.refreshCalls)
        assertEquals(listOf("feed-1", "feed-3"), read.cursors.map(FeedCursor::postId))
        assertTrue(read.cursors.all { it.createdAt == EqualTimestamp })
    }

    @Test
    fun failsClosedWhenPagesOverlap() = runTest {
        val read = RankingReadRepository(
            first = listOf(post(0), post(1)),
            older = ArrayDeque(listOf(Result.success(listOf(post(1))))),
        )

        val failure = ReadOnlyFeedRepository(read).loadCompleteFeedRanking(pageSize = 2).exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("feed_ranking_duplicate_post"))
    }

    @Test
    fun propagatesTransportFailureWithoutPublishingAPartialSnapshot() = runTest {
        val read = RankingReadRepository(
            first = listOf(post(0), post(1)),
            older = ArrayDeque(listOf(Result.failure(IllegalStateException("forced ranking failure")))),
        )

        val result = ReadOnlyFeedRepository(read).loadCompleteFeedRanking(pageSize = 2)

        assertTrue(result.isFailure)
        assertEquals("forced ranking failure", result.exceptionOrNull()?.message)
    }

    private class RankingReadRepository(
        private val first: List<Post>,
        private val older: ArrayDeque<Result<List<Post>>>,
    ) : FeedReadRepository {
        var refreshCalls = 0
        val cursors = mutableListOf<FeedCursor>()
        override fun observeFeed(): Flow<Result<List<Post>>> = flowOf(Result.success(first))
        override suspend fun getFeed() = Result.success(first)
        override suspend fun refreshFeed(): Result<List<Post>> {
            refreshCalls += 1
            return Result.success(first)
        }
        override suspend fun loadOlderFeedPage(cursor: FeedCursor, limit: Int): Result<List<Post>> {
            cursors += cursor
            return older.removeFirstOrNull() ?: Result.success(emptyList())
        }
        override suspend fun refreshCurrentUser() = Result.success<User?>(null)
        override suspend fun refreshAuthor(userId: String) = Result.success<User?>(null)
        override suspend fun refreshPost(postId: String) = Result.success<Post?>(null)
    }

    private companion object {
        const val EqualTimestamp = "2026-10-04T10:00:00Z"
        val author = User("author", "author@example.invalid", "Author")
        fun post(index: Int) = Post("feed-$index", author, "Feed $index", createdAt = EqualTimestamp)
    }
}
