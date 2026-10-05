package com.quata.feature.feed.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.Post
import com.quata.core.model.User
import com.quata.feature.feed.domain.FeedCursor
import com.quata.feature.feed.domain.FeedReadRepository
import com.quata.feature.feed.domain.ReadOnlyFeedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedRemoteRankingInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun remoteSecondPageFailsClosedRetriesAndOpensExactTarget() {
        val readRepository = AndroidFeedRemoteRankingRepository()
        val repository = ReadOnlyFeedRepository(readRepository)
        val model = FeedViewModel(repository)
        try {
            compose.setContent {
                QuataTheme {
                    FeedScreenHost(
                        padding = PaddingValues(),
                        repository = repository,
                        stateHolder = model,
                        slots = FeedScreenPlatformSlots(media = { _, _, _, _, _, _ -> }),
                    )
                }
            }

            compose.waitUntil(10_000) { model.uiState.value.posts.size == 50 }
            compose.onNodeWithText(AndroidFeedInitialMarker).assertIsDisplayed()
            compose.onAllNodesWithContentDescription("LIVE")[0].performClick()
            compose.waitUntil(10_000) { model.uiState.value.rankingError != null }
            compose.onNodeWithTag(FeedRankingErrorTestTag).assertIsDisplayed()
            compose.onAllNodesWithTag("live.ranking.open.$AndroidFeedRemoteTargetId").assertCountEquals(0)

            compose.onNodeWithTag(FeedRankingRetryTestTag).performClick()
            compose.waitUntil(10_000) { model.uiState.value.rankingPosts?.size == 101 }
            compose.runOnIdle {
                assertEquals(50, model.uiState.value.posts.size)
                assertFalse(model.uiState.value.posts.any { it.id == AndroidFeedRemoteTargetId })
                assertEquals(3, readRepository.olderPageCalls)
            }
            compose.onNodeWithTag("live.ranking.open.$AndroidFeedRemoteTargetId").assertIsDisplayed().performClick()
            compose.waitUntil(10_000) { model.uiState.value.posts.any { it.id == AndroidFeedRemoteTargetId } }
            compose.onNodeWithText(AndroidFeedRemoteTargetText).assertIsDisplayed()
            compose.runOnIdle {
                assertTrue(model.uiState.value.posts.any { it.id == AndroidFeedRemoteTargetId })
            }
        } finally {
            model.close()
        }
    }
}

private class AndroidFeedRemoteRankingRepository : FeedReadRepository {
    private val allPosts = (0..100).map(::androidFeedRankingPost)
    private val first = allPosts.subList(0, 50)
    private val second = allPosts.subList(50, 100)
    private val final = allPosts.subList(100, 101)
    private val observed = MutableStateFlow(Result.success(first))
    private var failOnce = true
    var olderPageCalls = 0
        private set

    override fun observeFeed(): Flow<Result<List<Post>>> = observed
    override suspend fun getFeed() = Result.success(first)
    override suspend fun refreshFeed() = Result.success(first)
    override suspend fun loadOlderFeedPage(cursor: FeedCursor, limit: Int): Result<List<Post>> {
        olderPageCalls += 1
        check(limit == 50)
        return when (cursor.postId) {
            first.last().id -> if (failOnce) {
                failOnce = false
                Result.failure(IllegalStateException("forced_feed_ranking_page_failure"))
            } else Result.success(second)
            second.last().id -> Result.success(final)
            else -> Result.success(emptyList())
        }
    }
    override suspend fun refreshCurrentUser() = Result.success<User?>(null)
    override suspend fun refreshAuthor(userId: String) = Result.success(allPosts.firstOrNull { it.author.id == userId }?.author)
    override suspend fun refreshPost(postId: String) = Result.success(allPosts.firstOrNull { it.id == postId })
}

private fun androidFeedRankingPost(index: Int): Post {
    val target = index == 50
    val id = if (target) AndroidFeedRemoteTargetId else "android-feed-ranking-$index"
    return Post(
        id = id,
        author = User("android-feed-author-$index", "android-feed-$index@example.invalid", "Feed Author $index"),
        text = when {
            target -> AndroidFeedRemoteTargetText
            index == 0 -> AndroidFeedInitialMarker
            else -> "Android feed ranking post $index"
        },
        createdAt = "2026-09-${(30 - index / 24).toString().padStart(2, '0')}T${(23 - index % 24).toString().padStart(2, '0')}:00:00Z",
        likesCount = if (target) 10_000 else 100 - index,
    )
}

private const val AndroidFeedRemoteTargetId = "android-feed-ranking-remote-target"
private const val AndroidFeedRemoteTargetText = "Android Feed remote ranking target loaded exactly"
private const val AndroidFeedInitialMarker = "Android Feed initial pager remains intact"
