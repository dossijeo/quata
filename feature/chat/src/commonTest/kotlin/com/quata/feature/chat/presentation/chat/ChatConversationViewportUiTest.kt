package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.Message
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ChatConversationViewportUiTest {
    @Test
    fun stableMessageAnchorOverridesTheDefaultJumpToLatest() = runComposeUiTest {
        val messages = (0 until 30).map(::viewportMessage)
        var applied = 0
        setContent {
            QuataTheme {
                ChatConversationDetailContent(
                    messages = messages,
                    selectedMessageId = null,
                    strings = ChatConversationDetailStrings("edited", "deleted", "forwarded"),
                    showSenderAvatar = { false },
                    avatar = {},
                    onOpenLink = {},
                    onMessageClick = {},
                    composer = {},
                    initialViewport = ChatConversationViewport.Anchored("message-10", 0f),
                    isInitialViewportReady = true,
                    onInitialViewportApplied = { applied += 1 },
                    modifier = Modifier.height(260.dp),
                )
            }
        }

        onNodeWithTag("chat.message.message-10").assertIsDisplayed()
        onAllNodesWithTag("chat.message.message-29").assertCountEquals(0)
        runOnIdle { assertEquals(1, applied) }
    }

    @Test
    fun latestViewportPreservesFollowLatestSemantics() = runComposeUiTest {
        val messages = (0 until 30).map(::viewportMessage)
        setContent {
            QuataTheme {
                ChatConversationDetailContent(
                    messages = messages,
                    selectedMessageId = null,
                    strings = ChatConversationDetailStrings("edited", "deleted", "forwarded"),
                    showSenderAvatar = { false },
                    avatar = {},
                    onOpenLink = {},
                    onMessageClick = {},
                    composer = {},
                    initialViewport = ChatConversationViewport.Latest,
                    isInitialViewportReady = true,
                    modifier = Modifier.height(260.dp),
                )
            }
        }

        onNodeWithTag("chat.message.message-29").assertIsDisplayed()
        onAllNodesWithTag("chat.message.message-0").assertCountEquals(0)
    }

    @Test
    fun viewportResizeKeepsLatestVisibleWhileFollowingTheBottom() = runComposeUiTest {
        val messages = (0 until 40).map(::viewportMessage)
        var viewportHeight by mutableStateOf(360.dp)
        setContent {
            QuataTheme {
                ChatConversationDetailContent(
                    messages = messages,
                    selectedMessageId = null,
                    strings = ChatConversationDetailStrings("edited", "deleted", "forwarded"),
                    showSenderAvatar = { false },
                    avatar = {},
                    onOpenLink = {},
                    onMessageClick = {},
                    composer = {},
                    initialViewport = ChatConversationViewport.Latest,
                    isInitialViewportReady = true,
                    modifier = Modifier.height(viewportHeight),
                )
            }
        }

        onNodeWithTag("chat.message.message-39").assertIsDisplayed()
        runOnIdle { viewportHeight = 220.dp }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag("chat.message.message-39").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("chat.message.message-39").assertIsDisplayed()
    }

    @Test
    fun viewportResizeDoesNotSnapADetachedReaderBackToLatest() = runComposeUiTest {
        val messages = (0 until 40).map(::viewportMessage)
        var viewportHeight by mutableStateOf(300.dp)
        var persistedViewport: ChatConversationViewport? = null
        setContent {
            QuataTheme {
                ChatConversationDetailContent(
                    messages = messages,
                    selectedMessageId = null,
                    strings = ChatConversationDetailStrings("edited", "deleted", "forwarded"),
                    showSenderAvatar = { false },
                    avatar = {},
                    onOpenLink = {},
                    onMessageClick = {},
                    composer = {},
                    initialViewport = ChatConversationViewport.Latest,
                    isInitialViewportReady = true,
                    onViewportChanged = { persistedViewport = it },
                    modifier = Modifier.height(viewportHeight),
                )
            }
        }

        onNodeWithTag(ChatConversationMessagesListTestTag).performTouchInput { swipeDown() }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag("chat.message.message-39").fetchSemanticsNodes().isEmpty() &&
                persistedViewport is ChatConversationViewport.Anchored
        }
        val detachedViewport = persistedViewport as ChatConversationViewport.Anchored
        mainClock.autoAdvance = false
        runOnIdle { viewportHeight = 180.dp }
        mainClock.advanceTimeBy(500)
        waitForIdle()
        onAllNodesWithTag("chat.message.message-39").assertCountEquals(0)
        runOnIdle {
            val resizedViewport = persistedViewport as ChatConversationViewport.Anchored
            assertEquals(detachedViewport.messageId, resizedViewport.messageId)
            assertTrue(abs(detachedViewport.scrollOffsetDp - resizedViewport.scrollOffsetDp) <= 1f)
        }
    }

    @Test
    fun recoverableFallbackDoesNotOverwriteTheStoredAnchorUntilTheUserScrolls() = runComposeUiTest {
        val messages = (0 until 30).map(::viewportMessage)
        var persisted: ChatConversationViewport? = null
        mainClock.autoAdvance = false
        setContent {
            var protectStoredViewport by remember { mutableStateOf(true) }
            QuataTheme {
                ChatConversationDetailContent(
                    messages = messages,
                    selectedMessageId = null,
                    strings = ChatConversationDetailStrings("edited", "deleted", "forwarded"),
                    showSenderAvatar = { false },
                    avatar = {},
                    onOpenLink = {},
                    onMessageClick = {},
                    composer = {},
                    isInitialViewportReady = true,
                    preserveStoredViewportUntilUserScroll = protectStoredViewport,
                    onViewportUserScroll = { protectStoredViewport = false },
                    onViewportChanged = { persisted = it },
                    modifier = Modifier.height(260.dp),
                )
            }
        }

        mainClock.advanceTimeBy(500)
        runOnIdle { assertEquals(null, persisted) }

        mainClock.autoAdvance = true
        onNodeWithTag(ChatConversationMessagesListTestTag).performTouchInput { swipeDown() }
        waitUntil(timeoutMillis = 5_000) { persisted != null }
        runOnIdle { assertTrue(persisted is ChatConversationViewport.Anchored) }
    }
}

private fun viewportMessage(index: Int) = Message(
    id = "message-$index",
    conversationId = "conversation-1",
    senderId = "peer",
    senderName = "Peer",
    text = "Message $index with enough text to occupy a stable conversation row.",
    sentAt = "2026-10-01T00:00:00Z",
)
