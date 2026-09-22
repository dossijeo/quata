package com.quata.feature.notifications.data

import com.quata.core.model.Conversation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationNotificationsRepositoryTest {
    @Test
    fun muteChangesPropagateToTheSharedInboxOnTheNextBoundedPoll() = runTest {
        var muted = false
        val emissions = mutableListOf<List<com.quata.core.model.NotificationItem>>()
        val observation = observeConversationNotifications(
            loadConversations = {
                listOf(
                    Conversation(
                        id = "sb:owned-thread",
                        title = "Owned thread",
                        lastMessagePreview = "Unread peer message",
                        unreadCount = 1,
                        isMuted = muted,
                    ),
                )
            },
            isAppForeground = MutableStateFlow(true),
            activeConversationId = MutableStateFlow<String?>(null),
            pollIntervalMillis = 15_000,
        ).onEach(emissions::add).launchIn(backgroundScope)

        runCurrent()
        assertEquals(listOf("sb:owned-thread"), emissions.last().map { it.conversationId })

        muted = true
        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(emptyList(), emissions.last())

        muted = false
        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(listOf("sb:owned-thread"), emissions.last().map { it.conversationId })
        observation.cancel()
    }

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
