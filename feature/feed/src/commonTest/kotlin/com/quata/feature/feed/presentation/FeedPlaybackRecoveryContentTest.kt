package com.quata.feature.feed.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.ui.components.QuataMediaPlaybackFailureTestTag
import com.quata.core.ui.components.QuataMediaPlaybackRetryTestTag
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class FeedPlaybackRecoveryContentTest {
    @Test
    fun playbackFailureExposesTheSharedRetryAndRoutesOneExplicitAttempt() = runComposeUiTest {
        var retries = 0
        setContent {
            QuataTheme {
                FeedReelVideoPlaybackHostContent(
                    state = VideoPlaybackState(error = "forced-playback-failure"),
                    strings = VideoPlaybackStrings(
                        play = "Play",
                        pause = "Pause",
                        mute = "Mute",
                        unmute = "Unmute",
                        playbackFailed = "The video could not be played.",
                        retry = "Retry",
                    ),
                    media = {},
                    onPlay = { _ -> },
                    onPause = { _ -> },
                    onSeek = { _ -> },
                    onEnded = {},
                    onError = { retries += 1 },
                    onToggleMute = {},
                )
            }
        }

        onNodeWithTag(QuataMediaPlaybackFailureTestTag).assertIsDisplayed()
        onNodeWithTag(QuataMediaPlaybackRetryTestTag).assertIsDisplayed().performClick()
        runOnIdle { assertEquals(1, retries) }
    }
}
