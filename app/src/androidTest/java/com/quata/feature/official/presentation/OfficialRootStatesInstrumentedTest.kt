package com.quata.feature.official.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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

@RunWith(AndroidJUnit4::class)
class OfficialRootStatesInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun officialRootExposesLoadingEmptyErrorAndRetry() {
        val holder = AndroidOfficialRootStateHolder(OfficialFeedUiState(isLoading = true))
        compose.setContent {
            QuataTheme {
                OfficialFeedScreenHost(
                    padding = PaddingValues(),
                    repository = androidOfficialRootRepository(),
                    stateHolder = holder,
                    slots = androidOfficialRootSlots(),
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

        compose.onNodeWithTag(OfficialFeedRootTestTag).assertIsDisplayed()
        compose.onNodeWithTag(OfficialFeedLoadingTestTag).assertIsDisplayed()

        compose.runOnIdle { holder.state.value = OfficialFeedUiState(isLoading = false) }
        compose.onAllNodesWithTag(OfficialFeedLoadingTestTag).assertCountEquals(0)
        compose.onNodeWithTag(OfficialFeedEmptyMessageTestTag).assertIsDisplayed()

        compose.runOnIdle {
            holder.state.value = OfficialFeedUiState(isLoading = false, error = "forced-official-error")
        }
        compose.onNodeWithTag(OfficialFeedErrorMessageTestTag).assertIsDisplayed()
        compose.onNodeWithTag(OfficialFeedRetryTestTag).assertIsDisplayed().performClick()
        compose.onNodeWithTag(OfficialFeedEmptyMessageTestTag).assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, holder.refreshes) }
    }
}

private class AndroidOfficialRootStateHolder(initial: OfficialFeedUiState) : OfficialFeedStateHolder {
    val state = MutableStateFlow(initial)
    var refreshes = 0
    override val uiState = state
    override fun onEvent(event: OfficialFeedUiEvent) {
        if (event == OfficialFeedUiEvent.Refresh) {
            refreshes += 1
            state.value = OfficialFeedUiState(isLoading = false)
        }
    }
    override fun refreshCurrentUser() = Unit
}

private fun androidOfficialRootSlots() = OfficialFeedScreenPlatformSlots(
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
)

private fun androidOfficialRootRepository() = object : OfficialRepository {
    override fun observeOfficialFeed() = flowOf(Result.success(emptyList<OfficialPostItem>()))
    override suspend fun getOfficialFeed() = Result.success(emptyList<OfficialPostItem>())
    override suspend fun refreshOfficialFeed() = Result.success(emptyList<OfficialPostItem>())
    override suspend fun loadOlderOfficialFeedPage(beforePublishedAt: String?, limit: Int) = Result.success(emptyList<OfficialPostItem>())
    override suspend fun getOfficialPost(postId: String) = Result.success<OfficialPostItem?>(null)
    override suspend fun refreshCurrentUser() = Result.success<User?>(null)
    override suspend fun createPost(draft: OfficialPostDraft) = Result.success<OfficialPostItem?>(null)
    override suspend fun deletePost(postId: String) = Result.success(Unit)
    override suspend fun toggleLike(postId: String) = Result.success<OfficialPostItem?>(null)
    override suspend fun addComment(postId: String, comment: PostComment) = Result.success<OfficialPostItem?>(null)
    override suspend fun reportComment(commentId: String) = Result.success(Unit)
}
