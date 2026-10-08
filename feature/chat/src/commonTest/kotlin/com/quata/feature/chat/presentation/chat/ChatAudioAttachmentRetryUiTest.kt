package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.QuataTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ChatAudioAttachmentRetryUiTest {
    @Test
    fun renderedFailureRetriesTheSameAttachmentThroughTheVisibleControl() = runComposeUiTest {
        val attachmentName = "retry-audio-fixture.m4a"
        var hasError by mutableStateOf(true)
        var loading by mutableStateOf(false)
        var retriedAttachment: String? = null

        setContent {
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
                        retriedAttachment = attachmentName
                        hasError = false
                        loading = true
                    },
                    onSeekToFraction = {},
                    modifier = Modifier.width(320.dp),
                )
            }
        }

        onNodeWithTag(ChatAudioAttachmentPlayerTestTag)
            .assertIsDisplayed()
            .assertStateStartsWith(ChatAudioAttachmentStateFailed)
        onNodeWithTag(ChatAudioAttachmentToggleTestTag)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertStateStartsWith(ChatAudioAttachmentStateFailed)
            .performClick()

        runOnIdle { assertEquals(attachmentName, retriedAttachment) }
        onNodeWithTag(ChatAudioAttachmentToggleTestTag)
            .assertIsDisplayed()
            .assertStateStartsWith(ChatAudioAttachmentStateLoading)
        onNodeWithTag(ChatAudioAttachmentPlayerTestTag)
            .assert(hasAudioAttachmentDescription(attachmentName))
            .assertIsDisplayed()
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertStateStartsWith(
    expected: String,
): androidx.compose.ui.test.SemanticsNodeInteraction = assert(
    SemanticsMatcher("state description starts with $expected") { node ->
        node.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith(expected) == true
    },
)

private fun hasAudioAttachmentDescription(name: String): SemanticsMatcher =
    SemanticsMatcher("content description contains $name") { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription)
            ?.any { description -> description.contains(name) } == true
    }
