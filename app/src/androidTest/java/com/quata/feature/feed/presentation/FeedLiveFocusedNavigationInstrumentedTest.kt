package com.quata.feature.feed.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

/** Android runtime coverage for the focused Live-to-detail route added in #336. */
@RunWith(AndroidJUnit4::class)
class FeedLiveFocusedNavigationInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun focusedLiveSelectionChangesIdentityAndBackReturnsToFeed() {
        val route = mutableStateOf<String?>("a")
        val changes = mutableListOf<String>()
        val holder = AndroidLiveFixtureState()

        compose.setContent {
            QuataTheme {
                FeedScreenHost(
                    padding = PaddingValues(),
                    repository = androidLiveRepository(),
                    stateHolder = holder,
                    slots = FeedScreenPlatformSlots(
                        media = { post, active, _, _, _, _ ->
                            if (active) Text("android-live-active-${post.id}")
                        },
                    ),
                    focusedPostId = route.value,
                    onFocusedPostChanged = { postId ->
                        changes += postId
                        route.value = postId
                    },
                    onBackFromFocusedPost = { route.value = null },
                )
            }
        }

        compose.onNodeWithTag(FeedPostDetailChromeTestTag).assertIsDisplayed()
        compose.onNodeWithText("android-live-active-a").assertIsDisplayed()

        compose.onNodeWithContentDescription("LIVE").performClick()
        compose.onNodeWithTag("live.ranking.open.b").assertIsDisplayed().performClick()
        compose.onNodeWithText("android-live-active-b").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf("b"), changes)
            assertEquals("b", route.value)
        }

        compose.onNodeWithContentDescription("LIVE").performClick()
        compose.onNodeWithTag("live.ranking.open.a").assertIsDisplayed().performClick()
        compose.onNodeWithText("android-live-active-a").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf("b", "a"), changes)
            assertEquals("a", route.value)
        }

        compose.onNodeWithTag(FeedPostDetailBackTestTag).performClick()
        compose.onAllNodesWithTag(FeedPostDetailChromeTestTag).assertCountEquals(0)
        compose.runOnIdle { assertEquals(null, route.value) }
    }
}

private val androidLivePosts = listOf(
    Post(
        id = "a",
        author = User("author-a", "a@example.invalid", "Author A"),
        text = "Post A",
        imageUrl = "fixture://a",
        createdAt = "2026-09-14",
        likesCount = 1,
    ),
    Post(
        id = "b",
        author = User("author-b", "b@example.invalid", "Author B"),
        text = "Post B",
        imageUrl = "fixture://b",
        createdAt = "2026-09-14",
        likesCount = 2,
    ),
)

private class AndroidLiveFixtureState : FeedStateHolder {
    override val uiState = MutableStateFlow(FeedUiState(isLoading = false, posts = androidLivePosts))
    override fun onEvent(event: FeedUiEvent) = Unit
}

private fun androidLiveRepository() = ReadOnlyFeedRepository(object : FeedReadRepository {
    override fun observeFeed() = flowOf(Result.success(androidLivePosts))
    override suspend fun getFeed() = Result.success(androidLivePosts)
    override suspend fun refreshFeed() = Result.success(androidLivePosts)
    override suspend fun loadOlderFeedPage(beforeCreatedAt: String?, limit: Int) = Result.success(emptyList<Post>())
    override suspend fun refreshCurrentUser() = Result.success<User?>(null)
    override suspend fun refreshAuthor(userId: String) = Result.success<User?>(null)
    override suspend fun refreshPost(postId: String) = Result.success(androidLivePosts.find { it.id == postId })
})
