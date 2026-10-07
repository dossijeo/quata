package com.quata.feature.feed.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.feature.feed.domain.ReadOnlyFeedRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedDeepPaginationInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun nativePagerPreservesFirstPageRetriesAndReachesDeepTarget() {
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
            val root = compose.onNodeWithTag(FeedRootTestTag)
            repeat(43) { root.performTouchInput { swipeUp() } }

            compose.waitUntil(10_000) { model.uiState.value.olderPageError != null }
            compose.onNodeWithTag(FeedOlderPostsErrorTestTag).assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(50, model.uiState.value.posts.size)
                assertEquals(1, readRepository.olderPageCalls)
                assertTrue(model.uiState.value.posts.none { it.id == AndroidFeedRemoteTargetId })
            }

            compose.onNodeWithTag(FeedOlderPostsRetryTestTag).performClick()
            compose.waitUntil(10_000) { model.uiState.value.posts.size == 100 }
            compose.runOnIdle {
                assertEquals(2, readRepository.olderPageCalls)
                assertTrue(model.uiState.value.olderPageError == null)
            }

            repeat(20) {
                if (!compose.onNodeWithText(AndroidFeedRemoteTargetText).isDisplayed()) {
                    root.performTouchInput { swipeUp() }
                }
            }
            compose.onNodeWithText(AndroidFeedRemoteTargetText).assertIsDisplayed()
            compose.runOnIdle {
                assertTrue(model.uiState.value.posts.any { it.id == AndroidFeedRemoteTargetId })
                assertEquals(100, model.uiState.value.posts.size)
            }
        } finally {
            model.close()
        }
    }
}
