package com.quata.feature.notifications.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationNotificationsRepositoryTest {
    @Test
    fun activeConversationChangesDoNotRestartTheRemoteInboxPoll() = runTest {
        var remoteReads = 0
        val foreground = MutableStateFlow(true)
        val activeConversation = MutableStateFlow<String?>(null)
        val observation = observeConversationNotifications(
            loadConversations = { remoteReads += 1; emptyList() },
            isAppForeground = foreground,
            activeConversationId = activeConversation,
            pollIntervalMillis = 15_000,
        ).launchIn(backgroundScope)

        runCurrent()
        assertEquals(1, remoteReads)

        repeat(100) { activeConversation.value = "sb:$it" }
        runCurrent()
        assertEquals(1, remoteReads)

        advanceTimeBy(14_999)
        runCurrent()
        assertEquals(1, remoteReads)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, remoteReads)
        observation.cancel()
    }
}
