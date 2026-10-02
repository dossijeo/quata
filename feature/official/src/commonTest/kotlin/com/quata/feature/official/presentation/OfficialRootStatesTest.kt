package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.core.platform.PlatformResult
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialPostType
import com.quata.feature.official.domain.OfficialRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class OfficialRootStatesTest {
    @Test
    fun rootExposesLoadingEmptyErrorAndRetryWithoutLeavingTheSharedHost() = runComposeUiTest {
        val holder = RootOfficialStateHolder(OfficialFeedUiState(isLoading = true))
        setContent { OfficialRootFixture(holder) }

        onNodeWithTag(OfficialFeedRootTestTag).assertIsDisplayed()
        onNodeWithTag(OfficialFeedLoadingTestTag).assertIsDisplayed()

        runOnIdle { holder.state.value = OfficialFeedUiState(isLoading = false) }
        onAllNodesWithTag(OfficialFeedLoadingTestTag).assertCountEquals(0)
        onNodeWithTag(OfficialFeedEmptyMessageTestTag).assertIsDisplayed()

        runOnIdle {
            holder.state.value = OfficialFeedUiState(isLoading = false, error = "forced-official-error")
        }
        onNodeWithTag(OfficialFeedErrorMessageTestTag).assertIsDisplayed()
        onNodeWithTag(OfficialFeedRetryTestTag).assertIsDisplayed().performClick()
        onNodeWithTag(OfficialFeedEmptyMessageTestTag).assertIsDisplayed()
        runOnIdle { assertEquals(1, holder.refreshes) }
    }

    @Test
    fun populatedRootKeepsTheOfficialPostInsideTheSameStableAnchor() = runComposeUiTest {
        val holder = RootOfficialStateHolder(
            OfficialFeedUiState(isLoading = false, posts = listOf(officialRootPost())),
        )
        setContent { OfficialRootFixture(holder) }

        onNodeWithTag(OfficialFeedRootTestTag).assertIsDisplayed()
        onNodeWithText("official-root-visible").assertIsDisplayed()
        onAllNodesWithTag(OfficialFeedLoadingTestTag).assertCountEquals(0)
        onAllNodesWithTag(OfficialFeedEmptyMessageTestTag).assertCountEquals(0)
        onAllNodesWithTag(OfficialFeedErrorMessageTestTag).assertCountEquals(0)
        onAllNodesWithTag(OfficialFeedRetryTestTag).assertCountEquals(0)
    }

    @Test
    fun populatedRootShowsOlderPageFailureAndWaitsForExplicitRetry() = runComposeUiTest {
        val holder = RootOfficialStateHolder(
            OfficialFeedUiState(
                isLoading = false,
                posts = listOf(officialRootPost()),
                hasMoreOlderPosts = true,
                olderPageError = "forced-older-page-error",
            ),
        )
        setContent { OfficialRootFixture(holder) }

        onNodeWithText("official-root-visible").assertIsDisplayed()
        onNodeWithTag(OfficialOlderPostsErrorTestTag).assertIsDisplayed()
        onNodeWithTag(OfficialOlderPostsRetryTestTag).assertIsDisplayed()
        runOnIdle {
            assertEquals(0, holder.automaticOlderPageLoads)
            assertEquals(0, holder.explicitOlderPageRetries)
        }

        onNodeWithTag(OfficialOlderPostsRetryTestTag).performClick()

        runOnIdle {
            assertEquals(0, holder.automaticOlderPageLoads)
            assertEquals(1, holder.explicitOlderPageRetries)
        }
        onNodeWithText("official-root-visible").assertIsDisplayed()
    }
}

@androidx.compose.runtime.Composable
private fun OfficialRootFixture(holder: OfficialFeedStateHolder) {
    QuataTheme {
        OfficialFeedScreenHost(
            padding = PaddingValues(),
            repository = rootOfficialRepository(),
            stateHolder = holder,
            slots = OfficialFeedScreenPlatformSlots(
                avatar = { _, _ -> },
                media = { _, _, _ -> },
                article = { _, _ -> },
                mediaViewer = { _, _ -> },
                openUrl = {},
                share = { PlatformResult.Unsupported },
                message = {},
                showComposeMessage = false,
                canCreateOfficialPost = false,
                rankingAvatar = {},
            ),
            currentUserId = null,
            focusedPostId = null,
            strings = OfficialFeedScreenStrings(),
            onFocusedPostHandled = {},
            onAuthRequired = {},
            onOpenUserProfile = {},
            onCreateOfficialPost = {},
            modifier = Modifier,
        )
    }
}

private class RootOfficialStateHolder(initial: OfficialFeedUiState) : OfficialFeedStateHolder {
    val state = MutableStateFlow(initial)
    var refreshes = 0
    var automaticOlderPageLoads = 0
    var explicitOlderPageRetries = 0
    override val uiState = state
    override fun onEvent(event: OfficialFeedUiEvent) {
        if (event == OfficialFeedUiEvent.Refresh) {
            refreshes += 1
            state.value = OfficialFeedUiState(isLoading = false)
        }
        if (event == OfficialFeedUiEvent.LoadOlderPage) {
            automaticOlderPageLoads += 1
            state.value = state.value.copy(isLoadingOlder = true)
        }
        if (event == OfficialFeedUiEvent.RetryOlderPage) {
            explicitOlderPageRetries += 1
            state.value = state.value.copy(isLoadingOlder = true, olderPageError = null)
        }
    }
    override fun refreshCurrentUser() = Unit
}

private fun rootOfficialRepository() = object : OfficialRepository {
    override fun observeOfficialFeed() = flowOf(Result.success(emptyList<OfficialPostItem>()))
    override suspend fun getOfficialFeed() = Result.success(emptyList<OfficialPostItem>())
    override suspend fun refreshOfficialFeed() = Result.success(emptyList<OfficialPostItem>())
    override suspend fun loadOlderOfficialFeedPage(cursor: com.quata.feature.official.domain.OfficialFeedCursor, limit: Int) = Result.success(emptyList<OfficialPostItem>())
    override suspend fun getOfficialPost(postId: String) = Result.success<OfficialPostItem?>(null)
    override suspend fun refreshCurrentUser() = Result.success<User?>(null)
    override suspend fun createPost(draft: OfficialPostDraft) = Result.success<OfficialPostItem?>(null)
    override suspend fun deletePost(postId: String) = Result.success(Unit)
    override suspend fun toggleLike(postId: String) = Result.success<OfficialPostItem?>(null)
    override suspend fun addComment(postId: String, comment: PostComment) = Result.success<OfficialPostItem?>(null)
    override suspend fun reportComment(commentId: String) = Result.success(Unit)
}

private fun officialRootPost() = OfficialPostItem(
    id = "official-root-post",
    author = User("official-root-author", "official-root@example.invalid", "Official Root"),
    title = "official-root-visible",
    summary = "Root postflight",
    contentHtml = "<p>Root postflight</p>",
    contentPlain = "Root postflight",
    type = OfficialPostType.Announcement,
    createdAt = "2026-09-19T00:00:00Z",
)
