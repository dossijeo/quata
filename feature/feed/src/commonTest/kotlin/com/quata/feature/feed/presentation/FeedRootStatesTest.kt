package com.quata.feature.feed.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.Post
import com.quata.core.model.User
import com.quata.feature.feed.domain.FeedReadRepository
import com.quata.feature.feed.domain.ReadOnlyFeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class FeedRootStatesTest {
    @Test
    fun rootExposesLoadingEmptyAndErrorWithoutLeavingTheSharedHost() = runComposeUiTest {
        val holder = RootStateHolder(FeedUiState(isLoading = true))
        setContent {
            QuataTheme {
                FeedScreenHost(
                    padding = PaddingValues(),
                    repository = rootRepository(),
                    stateHolder = holder,
                    slots = rootSlots(),
                )
            }
        }

        onNodeWithTag(FeedRootTestTag).assertIsDisplayed()
        onNodeWithTag(FeedLoadingTestTag).assertIsDisplayed()

        runOnIdle { holder.state.value = FeedUiState(isLoading = false) }
        onAllNodesWithTag(FeedLoadingTestTag).assertCountEquals(0)
        onNodeWithTag(FeedStatusMessageTestTag).assertIsDisplayed()
        onNodeWithTag(FeedStatusRetryTestTag).assertIsDisplayed().performClick()
        runOnIdle { assertEquals(1, holder.refreshes) }

        runOnIdle { holder.state.value = FeedUiState(isLoading = false, error = "forced-feed-error") }
        onNodeWithTag(FeedStatusMessageTestTag).assertIsDisplayed()
        onNodeWithTag(FeedStatusRetryTestTag).assertIsDisplayed().performClick()
        runOnIdle { assertEquals(2, holder.refreshes) }
    }

    @Test
    fun populatedRootKeepsTheSharedPagerInsideTheSameStableAnchor() = runComposeUiTest {
        val post = Post(
            id = "feed-root-post",
            author = User("feed-root-author", "feed-root@example.invalid", "Feed Root"),
            text = "feed-root-visible",
            createdAt = "2026-09-19T00:00:00Z",
        )
        val holder = RootStateHolder(FeedUiState(isLoading = false, posts = listOf(post)))
        setContent {
            QuataTheme {
                FeedScreenHost(
                    padding = PaddingValues(),
                    repository = rootRepository(listOf(post)),
                    stateHolder = holder,
                    slots = rootSlots(),
                )
            }
        }

        onNodeWithTag(FeedRootTestTag).assertIsDisplayed()
        onAllNodesWithTag(FeedLoadingTestTag).assertCountEquals(0)
        onAllNodesWithTag(FeedStatusMessageTestTag).assertCountEquals(0)
        onAllNodesWithTag(FeedStatusRetryTestTag).assertCountEquals(0)
    }
}

private class RootStateHolder(initial: FeedUiState) : FeedStateHolder {
    val state = MutableStateFlow(initial)
    var refreshes = 0
    override val uiState = state
    override fun onEvent(event: FeedUiEvent) {
        if (event == FeedUiEvent.Refresh) refreshes += 1
    }
}

private fun rootSlots() = FeedScreenPlatformSlots(
    media = { _, active, _, _, _, _ -> if (active) Text("feed-root-media") },
)

private fun rootRepository(posts: List<Post> = emptyList()) = ReadOnlyFeedRepository(object : FeedReadRepository {
    override fun observeFeed() = flowOf(Result.success(posts))
    override suspend fun getFeed() = Result.success(posts)
    override suspend fun refreshFeed() = Result.success(posts)
    override suspend fun loadOlderFeedPage(beforeCreatedAt: String?, limit: Int) = Result.success(emptyList<Post>())
    override suspend fun refreshCurrentUser() = Result.success<User?>(null)
    override suspend fun refreshAuthor(userId: String) = Result.success<User?>(null)
    override suspend fun refreshPost(postId: String) = Result.success(posts.find { it.id == postId })
})
