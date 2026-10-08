package com.quata.feature.chat.presentation.chat

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quata.core.designsystem.theme.QuataTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatAudioAttachmentRetryInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun renderedFailureInvokesVisibleRetryAndRendersLoading() {
        val attachmentName = "retry-audio-fixture.m4a"
        var hasError by mutableStateOf(true)
        var loading by mutableStateOf(false)
        var retryInvocations = 0

        compose.setContent {
            QuataTheme {
                ChatAudioAttachmentPlayerContent(
                    isPlaying = false,
                    hasError = hasError,
                    isLoading = loading,
                    progress = 0f,
                    displayText = attachmentName,
                    errorText = "The audio could not be played.",
                    textColor = Color.White,
                    playPauseDescription = "Play audio",
                    retryDescription = "Retry audio",
                    onTogglePlayback = {
                        retryInvocations += 1
                        hasError = false
                        loading = true
                    },
                    onSeekToFraction = {},
                    modifier = Modifier.width(320.dp),
                )
            }
        }

        compose.onNodeWithTag(ChatAudioAttachmentPlayerTestTag)
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher("rendered audio failure") { node ->
                    node.config.getOrNull(SemanticsProperties.StateDescription)
                        ?.startsWith(ChatAudioAttachmentStateFailed) == true
                },
            )
        compose.onNodeWithTag(ChatAudioAttachmentToggleTestTag)
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        compose.runOnIdle { assertEquals(1, retryInvocations) }
        compose.onNodeWithTag(ChatAudioAttachmentToggleTestTag)
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher("audio retry enters loading") { node ->
                    node.config.getOrNull(SemanticsProperties.StateDescription)
                        ?.startsWith(ChatAudioAttachmentStateLoading) == true
                },
            )
        compose.onNodeWithTag(ChatAudioAttachmentPlayerTestTag)
            .assert(
                SemanticsMatcher("audio identity remains rendered") { node ->
                    node.config.getOrNull(SemanticsProperties.ContentDescription)
                        ?.any { description -> description.contains(attachmentName) } == true
                },
            )
            .assertIsDisplayed()
    }
}
