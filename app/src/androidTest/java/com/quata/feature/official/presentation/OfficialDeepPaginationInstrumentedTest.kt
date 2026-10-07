package com.quata.feature.official.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.platform.PlatformResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfficialDeepPaginationInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun nativePagerPreservesFirstPageRetriesAndReachesDeepTarget() {
        val repository = AndroidOfficialRemoteRankingRepository()
        val model = OfficialFeedViewModel(repository)
        try {
            compose.setContent {
                QuataTheme {
                    OfficialFeedScreenHost(
                        padding = PaddingValues(),
                        repository = repository,
                        stateHolder = model,
                        slots = officialDeepPaginationSlots(),
                        currentUserId = null,
                        focusedPostId = null,
                        strings = OfficialFeedScreenStrings(),
                        onFocusedPostHandled = {},
                        onFocusedPostChanged = {},
                        onAuthRequired = {},
                        onOpenUserProfile = {},
                        onCreateOfficialPost = {},
                        modifier = Modifier,
                    )
                }
            }

            compose.waitUntil(10_000) { model.uiState.value.posts.size == 50 }
            compose.onNodeWithText(AndroidOfficialInitialMarker).assertIsDisplayed()
            val root = compose.onNodeWithTag(OfficialFeedRootTestTag)
            repeat(43) { root.performTouchInput { swipeUp() } }

            compose.waitUntil(10_000) { model.uiState.value.olderPageError != null }
            compose.onNodeWithTag(OfficialOlderPostsErrorTestTag).assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(50, model.uiState.value.posts.size)
                assertEquals(1, repository.olderPageCalls)
                assertTrue(model.uiState.value.posts.none { it.id == AndroidOfficialRemoteTargetId })
            }

            compose.onNodeWithTag(OfficialOlderPostsRetryTestTag).performClick()
            compose.waitUntil(10_000) { model.uiState.value.posts.size == 100 }
            compose.runOnIdle {
                assertEquals(2, repository.olderPageCalls)
                assertTrue(model.uiState.value.olderPageError == null)
            }

            repeat(20) {
                if (!compose.onNodeWithText(AndroidOfficialRemoteTargetTitle).isDisplayed()) {
                    root.performTouchInput { swipeUp() }
                }
            }
            compose.onNodeWithText(AndroidOfficialRemoteTargetTitle).assertIsDisplayed()
            compose.runOnIdle {
                assertTrue(model.uiState.value.posts.any { it.id == AndroidOfficialRemoteTargetId })
                assertEquals(100, model.uiState.value.posts.size)
            }
        } finally {
            model.close()
        }
    }
}

private fun officialDeepPaginationSlots() = OfficialFeedScreenPlatformSlots(
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
