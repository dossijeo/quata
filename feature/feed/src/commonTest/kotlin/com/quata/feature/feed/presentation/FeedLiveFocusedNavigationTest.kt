package com.quata.feature.feed.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.Post
import com.quata.core.model.User
import com.quata.feature.feed.domain.FeedReadRepository
import com.quata.feature.feed.domain.ReadOnlyFeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals

/** Exercises the real common host and ranking controls; no OS, network or media certification. */
@OptIn(ExperimentalTestApi::class)
class FeedLiveFocusedNavigationTest {
    @Test fun routeControlledCancelKeepsFocus() = detailScenario(true, DetailAction.Cancel)
    @Test fun nativeMountedCancelKeepsFocus() = detailScenario(false, DetailAction.Cancel)
    @Test fun routeControlledRoundTrip() = detailScenario(true, DetailAction.RoundTrip)
    @Test fun nativeMountedRoundTrip() = detailScenario(false, DetailAction.RoundTrip)
    @Test fun routeControlledBackAfterSelection() = detailScenario(true, DetailAction.Back)
    @Test fun nativeMountedBackAfterSelection() = detailScenario(false, DetailAction.Back)

    private fun detailScenario(routeControlled: Boolean, action: DetailAction) = runComposeUiTest {
        val route = mutableStateOf<String?>("a")
        val changes = mutableListOf<String>()
        val holder = LiveFixtureState()
        val repository = liveRepository()
        setContent {
            QuataTheme {
                FeedScreenHost(
                    padding = PaddingValues(), repository = repository, stateHolder = holder,
                    slots = liveSlots(), isLandscape = false,
                    focusedPostId = if (routeControlled) route.value else "a",
                    onFocusedPostChanged = { changes += it; route.value = it },
                    onBackFromFocusedPost = { route.value = null },
                )
            }
        }
        onNodeWithText("active-a").assertIsDisplayed()
        onNodeWithContentDescription("LIVE").performClick()
        if (action == DetailAction.Cancel) {
            onNodeWithContentDescription("Cerrar").performClick()
            onNodeWithText("active-a").assertIsDisplayed()
            runOnIdle { assertEquals("a", route.value); assertEquals(emptyList(), changes) }
        } else {
            // Fixture ranking is A then B; use the real second row's Open control.
            onAllNodesWithText("Abrir")[1].performClick()
            onNodeWithText("active-b").assertIsDisplayed()
            if (action == DetailAction.RoundTrip) {
                onNodeWithContentDescription("LIVE").performClick()
                onAllNodesWithText("Abrir")[0].performClick()
                onNodeWithText("active-a").assertIsDisplayed()
                runOnIdle { assertEquals(listOf("b", "a"), changes); assertEquals("a", route.value) }
            } else {
                onNodeWithTag(FeedPostDetailBackTestTag).performClick()
                onNodeWithTag(FeedPostDetailChromeTestTag).assertDoesNotExist()
                runOnIdle { assertEquals(listOf("b"), changes); assertEquals(null, route.value) }
            }
        }
        onNodeWithText("En directo").assertDoesNotExist()
    }

    @Test fun ordinaryFeedSelectionKeepsPagerNavigation() = runComposeUiTest {
        val changes = mutableListOf<String>()
        val holder = LiveFixtureState()
        val repository = liveRepository()
        setContent {
            QuataTheme {
                FeedScreenHost(
                    padding = PaddingValues(), repository = repository, stateHolder = holder,
                    slots = liveSlots(), isLandscape = false,
                    onFocusedPostChanged = { changes += it },
                )
            }
        }
        onNodeWithText("active-a").assertIsDisplayed()
        onAllNodesWithContentDescription("LIVE")[0].performClick()
        onAllNodesWithText("Abrir").assertCountEquals(2)[1].performClick()
        onNodeWithText("active-b").assertIsDisplayed()
        onNodeWithTag(FeedPostDetailChromeTestTag).assertDoesNotExist()
        runOnIdle { assertEquals(emptyList(), changes) }
    }
}

private enum class DetailAction { Cancel, RoundTrip, Back }

private val livePosts = listOf(
    Post("a", User("author-a", "a@example.invalid", "Author A"), "Post A", imageUrl = "fixture://a", createdAt = "2026-09-14", likesCount = 2),
    Post("b", User("author-b", "b@example.invalid", "Author B"), "Post B", imageUrl = "fixture://b", createdAt = "2026-09-14", likesCount = 1),
)

private class LiveFixtureState : FeedStateHolder {
    override val uiState = MutableStateFlow(FeedUiState(isLoading = false, posts = livePosts))
    override fun onEvent(event: FeedUiEvent) = Unit
}

private fun liveSlots() = FeedScreenPlatformSlots(
    media = { post, active, _, _, _, _ -> if (active) Text("active-${post.id}") },
)

private fun liveRepository() = ReadOnlyFeedRepository(object : FeedReadRepository {
    override fun observeFeed() = flowOf(Result.success(livePosts))
    override suspend fun getFeed() = Result.success(livePosts)
    override suspend fun refreshFeed() = Result.success(livePosts)
    override suspend fun loadOlderFeedPage(beforeCreatedAt: String?, limit: Int) = Result.success(emptyList<Post>())
    override suspend fun refreshCurrentUser() = Result.success<User?>(null)
    override suspend fun refreshAuthor(userId: String) = Result.success<User?>(null)
    override suspend fun refreshPost(postId: String) = Result.success(livePosts.find { it.id == postId })
})
