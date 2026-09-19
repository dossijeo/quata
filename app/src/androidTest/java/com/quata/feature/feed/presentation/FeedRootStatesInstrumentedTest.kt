package com.quata.feature.feed.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.Post
import com.quata.core.model.User
import com.quata.feature.feed.domain.FeedReadRepository
import com.quata.feature.feed.domain.ReadOnlyFeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedRootStatesInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun feedRootExposesLoadingEmptyErrorAndRetry() {
        val holder = AndroidFeedRootStateHolder(FeedUiState(isLoading = true))
        compose.setContent {
            QuataTheme {
                FeedScreenHost(
                    padding = PaddingValues(),
                    repository = androidFeedRootRepository(),
                    stateHolder = holder,
                    slots = androidFeedRootSlots(),
                )
            }
        }

        compose.onNodeWithTag(FeedRootTestTag).assertIsDisplayed()
        compose.onNodeWithTag(FeedLoadingTestTag).assertIsDisplayed()

        compose.runOnIdle { holder.state.value = FeedUiState(isLoading = false) }
        compose.onAllNodesWithTag(FeedLoadingTestTag).assertCountEquals(0)
        compose.onNodeWithTag(FeedStatusMessageTestTag).assertIsDisplayed()
        compose.onNodeWithTag(FeedStatusRetryTestTag).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, holder.refreshes) }

        compose.runOnIdle {
            holder.state.value = FeedUiState(isLoading = false, error = "forced-feed-error")
        }
        compose.onNodeWithTag(FeedStatusMessageTestTag).assertIsDisplayed()
        compose.onNodeWithTag(FeedStatusRetryTestTag).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(2, holder.refreshes) }
    }
}

private class AndroidFeedRootStateHolder(initial: FeedUiState) : FeedStateHolder {
    val state = MutableStateFlow(initial)
    var refreshes = 0
    override val uiState = state
    override fun onEvent(event: FeedUiEvent) {
        if (event == FeedUiEvent.Refresh) refreshes += 1
    }
}

private fun androidFeedRootSlots() = FeedScreenPlatformSlots(
    media = { _, active, _, _, _, _ -> if (active) Text("feed-root-media") },
)

private fun androidFeedRootRepository(posts: List<Post> = emptyList()) =
    ReadOnlyFeedRepository(object : FeedReadRepository {
        override fun observeFeed() = flowOf(Result.success(posts))
        override suspend fun getFeed() = Result.success(posts)
        override suspend fun refreshFeed() = Result.success(posts)
        override suspend fun loadOlderFeedPage(beforeCreatedAt: String?, limit: Int) = Result.success(emptyList<Post>())
        override suspend fun refreshCurrentUser() = Result.success<User?>(null)
        override suspend fun refreshAuthor(userId: String) = Result.success<User?>(null)
        override suspend fun refreshPost(postId: String) = Result.success(posts.find { it.id == postId })
    })
