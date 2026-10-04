package com.quata.feature.feed.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import com.quata.core.common.AppDispatchers
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.Post
import com.quata.core.model.User
import com.quata.feature.feed.domain.FeedReadRepository
import com.quata.feature.feed.domain.ReadOnlyFeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Exercises the real common host and ranking controls; no OS, network or media certification. */
@OptIn(ExperimentalTestApi::class)
class FeedLiveFocusedNavigationTest {
    @Test
    fun remoteRankingTargetLoadsThenScrollsWhenItEntersThePager() {
        assertEquals(
            FeedRankingTargetAction.RequestLoad,
            resolveFeedRankingTarget("remote", listOf("visible"), null),
        )
        assertEquals(
            FeedRankingTargetAction.WaitForLoad,
            resolveFeedRankingTarget("remote", listOf("visible"), FeedFocusedPostLoad.Loading),
        )
        val scroll = assertIs<FeedRankingTargetAction.Scroll>(
            resolveFeedRankingTarget("remote", listOf("visible", "remote"), FeedFocusedPostLoad.Loaded),
        )
        assertEquals(1, scroll.index)
    }

    @Test
    fun failedOrMissingRankingTargetClearsForANewSelection() {
        listOf(FeedFocusedPostLoad.Loaded, FeedFocusedPostLoad.Failed, FeedFocusedPostLoad.NotFound).forEach { failure ->
            assertEquals(
                FeedRankingTargetAction.ClearFailedTarget,
                resolveFeedRankingTarget("remote", listOf("visible"), failure),
            )
        }
    }

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
            // B ranks above A; identity must not depend on the row ordinal.
            onNodeWithTag("live.ranking.open.b").assertIsDisplayed().assertHasClickAction().performClick()
            onNodeWithText("active-b").assertIsDisplayed()
            if (action == DetailAction.RoundTrip) {
                onNodeWithContentDescription("LIVE").performClick()
                onNodeWithTag("live.ranking.open.a").assertIsDisplayed().assertHasClickAction().performClick()
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
        onAllNodesWithText("Abrir").assertCountEquals(2)
        onNodeWithTag("live.ranking.open.b").assertIsDisplayed().assertHasClickAction().performClick()
        onNodeWithText("active-b").assertIsDisplayed()
        onNodeWithTag(FeedPostDetailChromeTestTag).assertDoesNotExist()
        runOnIdle { assertEquals(emptyList(), changes) }
    }

    @Test
    fun realViewModelLoadsRankingTargetAbsentFromTheVisiblePager() =
        remoteRankingScenario(Result.success(remoteRankingPost), expectedReads = 1)

    @Test
    fun realViewModelRetriesRankingTargetAfterFailedRead() =
        remoteRankingScenario(Result.failure(IllegalStateException("transport")), expectedReads = 2)

    @Test
    fun realViewModelRetriesRankingTargetAfterNotFoundRead() =
        remoteRankingScenario(Result.success(null), expectedReads = 2)

    private fun remoteRankingScenario(firstRead: Result<Post?>, expectedReads: Int) = runComposeUiTest {
        val repository = RemoteRankingRepository(firstRead)
        val immediate = Dispatchers.Unconfined
        val model = FeedViewModel(
            ReadOnlyFeedRepository(repository),
            AppDispatchers(immediate, immediate, immediate),
        )
        try {
            setContent {
                QuataTheme {
                    FeedScreenHost(
                        padding = PaddingValues(),
                        repository = ReadOnlyFeedRepository(repository),
                        stateHolder = model,
                        slots = liveSlots(),
                        isLandscape = false,
                    )
                }
            }
            waitUntil { model.uiState.value.posts.map(Post::id) == listOf("visible") }
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("active-visible").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("active-visible").assertIsDisplayed()
            onNodeWithContentDescription("LIVE").performClick()
            waitUntil { model.uiState.value.rankingPosts?.any { it.id == "remote" } == true }
            onNodeWithTag("live.ranking.open.remote").assertIsDisplayed().performClick()

            if (expectedReads > 1) {
                waitUntil {
                    model.uiState.value.focusedPostLoads["remote"] in setOf(
                        FeedFocusedPostLoad.Failed,
                        FeedFocusedPostLoad.NotFound,
                    )
                }
                onNodeWithContentDescription("LIVE").performClick()
                onNodeWithTag("live.ranking.open.remote").assertIsDisplayed().performClick()
            }

            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("active-remote").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("active-remote").assertIsDisplayed()
            runOnIdle {
                assertEquals(expectedReads, repository.reads)
                assertEquals(FeedFocusedPostLoad.Loaded, model.uiState.value.focusedPostLoads["remote"])
            }
        } finally {
            model.close()
        }
    }
}

private enum class DetailAction { Cancel, RoundTrip, Back }

private val livePosts = listOf(
    Post("a", User("author-a", "a@example.invalid", "Author A"), "Post A", imageUrl = "fixture://a", createdAt = "2026-09-14", likesCount = 1),
    Post("b", User("author-b", "b@example.invalid", "Author B"), "Post B", imageUrl = "fixture://b", createdAt = "2026-09-14", likesCount = 2),
)

private val visibleRankingPost =
    Post("visible", User("author-visible", "visible@example.invalid", "Visible"), "Visible", imageUrl = "fixture://visible", createdAt = "2026-09-14", likesCount = 1)
private val remoteRankingPost =
    Post("remote", User("author-remote", "remote@example.invalid", "Remote"), "Remote", imageUrl = "fixture://remote", createdAt = "2026-09-14", likesCount = 2)

private class RemoteRankingRepository(private val firstRead: Result<Post?>) : FeedReadRepository {
    var reads = 0
        private set

    override fun observeFeed() = flowOf(Result.success(listOf(visibleRankingPost)))
    override suspend fun getFeed() = Result.success(listOf(visibleRankingPost))
    override suspend fun refreshFeed() = Result.success(listOf(remoteRankingPost, visibleRankingPost))
    override suspend fun loadOlderFeedPage(cursor: com.quata.feature.feed.domain.FeedCursor, limit: Int) =
        Result.success(emptyList<Post>())
    override suspend fun refreshCurrentUser() = Result.success<User?>(null)
    override suspend fun refreshAuthor(userId: String) = Result.success<User?>(null)
    override suspend fun refreshPost(postId: String): Result<Post?> {
        reads += 1
        return if (reads == 1) firstRead else Result.success(remoteRankingPost)
    }
}

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
    override suspend fun loadOlderFeedPage(cursor: com.quata.feature.feed.domain.FeedCursor, limit: Int) = Result.success(emptyList<Post>())
    override suspend fun refreshCurrentUser() = Result.success<User?>(null)
    override suspend fun refreshAuthor(userId: String) = Result.success<User?>(null)
    override suspend fun refreshPost(postId: String) = Result.success(livePosts.find { it.id == postId })
})
