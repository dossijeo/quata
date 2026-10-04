package com.quata.feature.official.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quata.core.designsystem.theme.QuataTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfficialPostDetailScrollRestorationInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun savedInstanceStateRestoresTheExactObservedOfficialDetailAnchor() {
        val restorationTester = StateRestorationTester(compose)
        var lastObservedAnchor: OfficialPostDetailScrollAnchor? = null

        restorationTester.setContent {
            var persistedAnchor by rememberSaveable(stateSaver = OfficialPostDetailScrollAnchor.Saver) {
                mutableStateOf(OfficialPostDetailScrollAnchor.Empty)
            }
            QuataTheme {
                OfficialPostDetailPanelContent(
                    postId = PostId,
                    title = "Scroll restoration",
                    closeLabel = "Close",
                    link = null,
                    onDismiss = {},
                    articleContent = { Spacer(it.height(900.dp)) },
                    author = { Spacer(it.height(160.dp)) },
                    media = { Spacer(it.height(224.dp)) },
                    resourceContent = { Spacer(it.height(180.dp)) },
                    navigationContent = { Spacer(it.height(160.dp)) },
                    initialScrollAnchor = persistedAnchor.takeIf { it.postId == PostId },
                    onScrollAnchorChanged = {
                        persistedAnchor = it
                        lastObservedAnchor = it
                    },
                    modifier = Modifier,
                )
            }
        }

        compose.onNodeWithTag(OfficialPostDetailScrollTestTag, useUnmergedTree = true)
            .performScrollToIndex(2)
            .performTouchInput { swipeUp() }
        compose.waitUntil(timeoutMillis = 5_000) {
            lastObservedAnchor?.let {
                it.postId == PostId && it.sectionKey == "article" && it.scrollOffsetPx > 0
            } == true
        }
        val beforeRestore = requireNotNull(lastObservedAnchor)

        compose.runOnIdle { lastObservedAnchor = null }
        restorationTester.emulateSavedInstanceStateRestore()
        compose.waitUntil(timeoutMillis = 5_000) { lastObservedAnchor == beforeRestore }

        compose.runOnIdle {
            assertEquals(beforeRestore, lastObservedAnchor)
            assertTrue(beforeRestore.scrollOffsetPx > 0)
        }
    }

    private companion object {
        const val PostId = "official-scroll-post"
    }
}
