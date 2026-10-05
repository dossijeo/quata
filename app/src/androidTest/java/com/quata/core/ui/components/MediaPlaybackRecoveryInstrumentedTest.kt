package com.quata.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.designsystem.theme.QuataThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MediaPlaybackRecoveryInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun failedVideoRetriesTheSameSourceIntoAPlayableState() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val recoveredVideo = File(
            instrumentation.targetContext.cacheDir,
            "media-playback-recovery-${System.nanoTime()}.mp4",
        )
        recoveredVideo.delete()

        try {
            compose.setContent {
                QuataTheme(mode = QuataThemeMode.Dark) {
                    AttachmentViewerDialog(
                        attachment = AttachmentPreview(
                            name = "Recovery fixture",
                            uri = recoveredVideo.toURI().toString(),
                            mimeType = "video/mp4",
                        ),
                        onDismiss = {},
                    )
                }
            }

            compose.waitUntil(20_000) {
                runCatching {
                    compose.onNodeWithTag(QuataMediaPlaybackFailureTestTag, useUnmergedTree = true)
                        .fetchSemanticsNode()
                }.isSuccess
            }
            compose.onNodeWithTag(QuataMediaPlaybackFailureTestTag, useUnmergedTree = true)
                .assertIsDisplayed()

            instrumentation.context.assets.open("quata-media-retry.mp4").use { input ->
                recoveredVideo.outputStream().use(input::copyTo)
            }
            compose.onNodeWithTag(QuataMediaPlaybackRetryTestTag, useUnmergedTree = true)
                .assertIsDisplayed()
                .performClick()

            compose.waitUntil(20_000) {
                runCatching {
                    val state = compose.onNodeWithTag("fullscreen-media.video", useUnmergedTree = true)
                        .fetchSemanticsNode()
                        .config
                        .getOrNull(SemanticsProperties.StateDescription)
                    state == "playing" || state == "paused"
                }.getOrDefault(false)
            }
            compose.waitUntil(5_000) {
                runCatching {
                    compose.onNodeWithTag(QuataMediaPlaybackFailureTestTag, useUnmergedTree = true)
                        .fetchSemanticsNode()
                }.isFailure
            }
        } finally {
            recoveredVideo.delete()
        }
    }
}
