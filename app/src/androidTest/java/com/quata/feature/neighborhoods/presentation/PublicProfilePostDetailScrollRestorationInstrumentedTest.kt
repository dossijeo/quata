package com.quata.feature.neighborhoods.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.Post
import com.quata.core.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PublicProfilePostDetailScrollRestorationInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun savedInstanceStateRestoresTheExactObservedPublicProfileDetailOffset() {
        val restorationTester = StateRestorationTester(compose)
        val post = Post(PostId, User("profile-author", "", "Ada"), "Deep", createdAt = "2026-10-09")

        restorationTester.setContent {
            QuataTheme {
                ProfilePostsPagerContent(
                    posts = listOf(post),
                    pagerState = rememberPagerState(pageCount = { 1 }),
                    onAddComment = { _, _ -> },
                    postPreview = { _, _, _, openDetail ->
                        if (openDetail != null) {
                            Button(onClick = openDetail, modifier = Modifier.testTag(OpenTag)) {
                                Text("Open")
                            }
                        } else {
                            Column {
                                Text("Top")
                                Spacer(Modifier.height(1_200.dp))
                                Text("Bottom")
                            }
                        }
                    },
                    detailChrome = { _, back ->
                        Button(onClick = back) { Text("Back") }
                    },
                    commentsDialog = { _, _, _ -> },
                )
            }
        }

        compose.onNodeWithTag(OpenTag).performClick()
        compose.onNodeWithTag(PublicProfilePostDetailScrollTestTagPrefix + PostId, useUnmergedTree = true)
            .performScrollToNode(hasText("Bottom"))
        compose.waitUntil(timeoutMillis = 5_000) { detailScrollOffset() > 0f }
        val beforeRestore = detailScrollOffset()

        restorationTester.emulateSavedInstanceStateRestore()
        compose.waitUntil(timeoutMillis = 5_000) { detailScrollOffset() == beforeRestore }
        val afterRestore = detailScrollOffset()

        assertEquals(beforeRestore, afterRestore)
        assertTrue(afterRestore > 0f)
    }

    private fun detailScrollOffset(): Float =
        compose.onNodeWithTag(PublicProfilePostDetailScrollTestTagPrefix + PostId, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
            .value()

    private companion object {
        const val PostId = "public-profile-scroll-post"
        const val OpenTag = "public-profile-scroll.open"
    }
}
