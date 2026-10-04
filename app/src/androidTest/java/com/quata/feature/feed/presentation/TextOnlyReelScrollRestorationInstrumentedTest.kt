package com.quata.feature.feed.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
class TextOnlyReelScrollRestorationInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun savedInstanceStateRestoresTheExactOpenReaderOffset() {
        val restorationTester = StateRestorationTester(compose)
        restorationTester.setContent { ReaderFixture() }

        compose.onNodeWithTag(TextOnlyReelReadMoreTestTag).performClick()
        compose.onNodeWithTag(TextOnlyReelReaderScrollTestTag, useUnmergedTree = true)
            .performTouchInput { swipeUp() }
        compose.waitUntil(timeoutMillis = 5_000) { readerScrollOffset() > 0f }
        val beforeRestore = readerScrollOffset()

        restorationTester.emulateSavedInstanceStateRestore()
        compose.waitUntil(timeoutMillis = 5_000) { readerScrollOffset() == beforeRestore }
        val afterRestore = readerScrollOffset()

        compose.runOnIdle {
            assertTrue(beforeRestore > 0f)
            assertEquals(beforeRestore, afterRestore)
        }
    }

    @Test
    fun closingAndReopeningTheSameReaderRestoresTheExactOffset() {
        compose.setContent { ReaderFixture() }

        compose.onNodeWithTag(TextOnlyReelReadMoreTestTag).performClick()
        compose.onNodeWithTag(TextOnlyReelReaderScrollTestTag, useUnmergedTree = true)
            .performTouchInput { swipeUp() }
        compose.waitUntil(timeoutMillis = 5_000) { readerScrollOffset() > 0f }
        val beforeClose = readerScrollOffset()

        compose.onNodeWithTag(CloseTestTag).performClick()
        compose.onNodeWithTag(TextOnlyReelReadMoreTestTag).performClick()
        compose.waitUntil(timeoutMillis = 5_000) { readerScrollOffset() == beforeClose }
        val afterReopen = readerScrollOffset()

        compose.runOnIdle {
            assertTrue(beforeClose > 0f)
            assertEquals(beforeClose, afterReopen)
        }
    }

    private fun readerScrollOffset(): Float =
        compose.onNodeWithTag(TextOnlyReelReaderScrollTestTag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
            .value()

    @Composable
    private fun ReaderFixture() {
        QuataTheme {
            Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                TextOnlyReelContent(
                    stableId = PostId,
                    displayText = LongText,
                    seedText = PostId,
                    patternId = null,
                    readMoreText = "Read more",
                    readerDismissButton = { modifier, onDismiss ->
                        Button(onClick = onDismiss, modifier = modifier.testTag(CloseTestTag)) {
                            Text("Close")
                        }
                    },
                )
            }
        }
    }

    private companion object {
        const val PostId = "feed-reader-restore-post"
        const val CloseTestTag = "feed.text-reader.close"
        val LongText = List(120) { "Paragraph ${it + 1} keeps the reader vertically scrollable." }.joinToString("\n\n")
    }
}
