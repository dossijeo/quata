package com.quata.feature.feed.presentation

import com.quata.core.model.Post
import com.quata.core.model.User
import com.quata.core.platform.IosShareService
import com.quata.core.ui.components.IosMemberProfileOpeningState
import com.quata.feature.feed.domain.FeedCursor
import com.quata.feature.feed.domain.FeedReadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import platform.UIKit.UIViewController

/** Opt-in iOS XCTest fixture that keeps the product Feed host and replaces only its read boundary. */
fun QuataIosFeedLiveRankingFixtureViewController(
    mediaFactory: IosFeedMediaFactory,
    failFirstOlderPage: Boolean,
): UIViewController {
    val repository = IosFeedLiveRankingFixtureRepository(failFirstOlderPage)
    return QuataFeedViewController(
        iosReadOnlyFeedHostDependencies(
            readRepository = repository,
            mediaFactory = mediaFactory,
            shareService = IosShareService(),
            profileOpeningState = IosMemberProfileOpeningState(),
        ),
    )
}

private class IosFeedLiveRankingFixtureRepository(
    failFirstOlderPage: Boolean,
) : FeedReadRepository {
    private val allPosts = (0..100).map(::feedRankingFixturePost)
    private val firstPage = allPosts.subList(0, 50)
    private val secondPage = allPosts.subList(50, 100)
    private val finalPage = allPosts.subList(100, 101)
    private val observed = MutableStateFlow(Result.success(firstPage))
    private var shouldFailOlderPage = failFirstOlderPage

    override fun observeFeed(): Flow<Result<List<Post>>> = observed
    override suspend fun getFeed(): Result<List<Post>> = Result.success(firstPage)
    override suspend fun refreshFeed(): Result<List<Post>> = Result.success(firstPage)

    override suspend fun loadOlderFeedPage(cursor: FeedCursor, limit: Int): Result<List<Post>> {
        check(limit == 50) { "feed_ranking_fixture_unexpected_limit" }
        return when (cursor.postId) {
            firstPage.last().id -> {
                if (shouldFailOlderPage) {
                    shouldFailOlderPage = false
                    Result.failure(IllegalStateException("feed_ranking_fixture_forced_page_failure"))
                } else {
                    Result.success(secondPage)
                }
            }
            secondPage.last().id -> Result.success(finalPage)
            finalPage.last().id -> Result.success(emptyList())
            else -> Result.failure(IllegalStateException("feed_ranking_fixture_unexpected_cursor"))
        }
    }

    override suspend fun refreshCurrentUser(): Result<User?> = Result.success(null)
    override suspend fun refreshAuthor(userId: String): Result<User?> =
        Result.success(allPosts.firstOrNull { it.author.id == userId }?.author)
    override suspend fun refreshPost(postId: String): Result<Post?> =
        Result.success(allPosts.firstOrNull { it.id == postId })
}

private fun feedRankingFixturePost(index: Int): Post {
    val isRemoteTarget = index == 50
    val id = if (isRemoteTarget) FeedLiveRankingRemoteTargetId else "feed-ranking-fixture-$index"
    val day = 30 - (index / 24)
    val hour = 23 - (index % 24)
    return Post(
        id = id,
        author = User(
            id = "feed-ranking-author-$index",
            email = "feed-ranking-$index@example.invalid",
            displayName = if (isRemoteTarget) "Remote Feed Ranking Author" else "Feed Ranking Author $index",
        ),
        text = when {
            isRemoteTarget -> FeedLiveRankingRemoteTargetText
            index == 0 -> FeedLiveRankingInitialMarkerText
            else -> "Feed ranking fixture post $index"
        },
        createdAt = "2026-09-${day.toString().padStart(2, '0')}T${hour.toString().padStart(2, '0')}:00:00Z",
        likesCount = if (isRemoteTarget) 10_000 else 100 - index,
    )
}

const val FeedLiveRankingRemoteTargetId = "feed-ranking-remote-target"
const val FeedLiveRankingRemoteTargetText = "Feed remote ranking target loaded exactly"
const val FeedLiveRankingInitialMarkerText = "Feed initial pager remains intact"
