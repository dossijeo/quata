package com.quata.feature.official.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.core.platform.PlatformResult
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Android runtime coverage for focused and ordinary Official Live navigation. */
@RunWith(AndroidJUnit4::class)
class OfficialLiveFocusedNavigationInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun focusedLiveSelectionChangesIdentityAndRoundTrips() {
        val route = mutableStateOf<String?>("a")
        val changes = mutableListOf<String>()

        compose.setContent {
            QuataTheme {
                OfficialFeedScreenHost(
                    padding = PaddingValues(),
                    repository = androidOfficialLiveRepository(),
                    stateHolder = AndroidOfficialLiveStateHolder(),
                    slots = androidOfficialLiveSlots(),
                    currentUserId = null,
                    focusedPostId = route.value,
                    strings = OfficialFeedScreenStrings(),
                    onFocusedPostHandled = {},
                    onFocusedPostChanged = { postId ->
                        changes += postId
                        route.value = postId
                    },
                    onBackFromFocusedPost = { route.value = null },
                    onAuthRequired = {},
                    onOpenUserProfile = {},
                    onCreateOfficialPost = {},
                    modifier = Modifier,
                )
            }
        }

        compose.onNodeWithTag(OfficialPostDetailChromeTestTag).assertIsDisplayed()
        compose.onAllNodesWithText("official-live-active-a")[0].assertIsDisplayed()

        compose.onAllNodesWithContentDescription("LIVE")[0].performClick()
        compose.onNodeWithTag("live.ranking.open.b").assertIsDisplayed().performClick()
        compose.onAllNodesWithText("official-live-active-b")[0].assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf("b"), changes)
            assertEquals("b", route.value)
        }

        compose.onAllNodesWithContentDescription("LIVE")[0].performClick()
        compose.onNodeWithTag("live.ranking.open.a").assertIsDisplayed().performClick()
        compose.onAllNodesWithText("official-live-active-a")[0].assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf("b", "a"), changes)
            assertEquals("a", route.value)
        }
    }

    @Test
    fun ordinaryLiveSelectionKeepsPagerNavigation() {
        val changes = mutableListOf<String>()

        compose.setContent {
            QuataTheme {
                OfficialFeedScreenHost(
                    padding = PaddingValues(),
                    repository = androidOfficialLiveRepository(),
                    stateHolder = AndroidOfficialLiveStateHolder(),
                    slots = androidOfficialLiveSlots(),
                    currentUserId = null,
                    focusedPostId = null,
                    strings = OfficialFeedScreenStrings(),
                    onFocusedPostHandled = {},
                    onFocusedPostChanged = { changes += it },
                    onAuthRequired = {},
                    onOpenUserProfile = {},
                    onCreateOfficialPost = {},
                    modifier = Modifier,
                )
            }
        }

        compose.onAllNodesWithText("official-live-active-a")[0].assertIsDisplayed()
        compose.onAllNodesWithContentDescription("LIVE")[0].performClick()
        compose.onNodeWithTag("live.ranking.open.b").assertIsDisplayed().performClick()
        compose.onAllNodesWithText("official-live-active-b")[0].assertIsDisplayed()
        compose.onAllNodesWithTag(OfficialPostDetailChromeTestTag).assertCountEquals(0)
        compose.runOnIdle { assertEquals(emptyList<String>(), changes) }
    }
}

private val androidOfficialLivePosts = listOf(
    OfficialPostItem(
        id = "a",
        author = User("author-a", "a@example.invalid", "Author A"),
        title = "official-live-active-a",
        summary = "Post A",
        contentHtml = "<p>Post A</p>",
        contentPlain = "Post A",
        createdAt = "2026-09-14T00:00:00Z",
        likesCount = 1,
    ),
    OfficialPostItem(
        id = "b",
        author = User("author-b", "b@example.invalid", "Author B"),
        title = "official-live-active-b",
        summary = "Post B",
        contentHtml = "<p>Post B</p>",
        contentPlain = "Post B",
        createdAt = "2026-09-14T00:00:00Z",
        likesCount = 2,
    ),
)

private class AndroidOfficialLiveStateHolder : OfficialFeedStateHolder {
    override val uiState = MutableStateFlow(
        OfficialFeedUiState(isLoading = false, posts = androidOfficialLivePosts),
    )
    override fun onEvent(event: OfficialFeedUiEvent) = Unit
    override fun refreshCurrentUser() = Unit
}

private fun androidOfficialLiveSlots() = OfficialFeedScreenPlatformSlots(
    avatar = { _, _ -> },
    media = { _, _, _ -> },
    article = { _, _ -> },
    mediaViewer = { _, _, _, _ -> },
    openUrl = {},
    share = { PlatformResult.Unsupported },
    message = {},
    showComposeMessage = false,
    canCreateOfficialPost = false,
    rankingAvatar = {},
)

private fun androidOfficialLiveRepository() = object : OfficialRepository {
    override fun observeOfficialFeed() = flowOf(Result.success(androidOfficialLivePosts))
    override suspend fun getOfficialFeed() = Result.success(androidOfficialLivePosts)
    override suspend fun refreshOfficialFeed() = Result.success(androidOfficialLivePosts)
    override suspend fun loadOlderOfficialFeedPage(cursor: com.quata.feature.official.domain.OfficialFeedCursor, limit: Int) = Result.success(emptyList<OfficialPostItem>())
    override suspend fun getOfficialPost(postId: String) = Result.success(androidOfficialLivePosts.find { it.id == postId })
    override suspend fun refreshCurrentUser() = Result.success<User?>(null)
    override suspend fun createPost(draft: OfficialPostDraft) = Result.success<OfficialPostItem?>(null)
    override suspend fun deletePost(postId: String) = Result.success(Unit)
    override suspend fun toggleLike(postId: String) = Result.success<OfficialPostItem?>(null)
    override suspend fun addComment(postId: String, comment: PostComment) = Result.success<OfficialPostItem?>(null)
    override suspend fun reportComment(commentId: String) = Result.success(Unit)
}
