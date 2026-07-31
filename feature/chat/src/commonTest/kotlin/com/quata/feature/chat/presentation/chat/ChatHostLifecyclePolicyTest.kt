package com.quata.feature.chat.presentation.chat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatHostLifecyclePolicyTest {
    @Test
    fun foregroundAndActiveReconnectWhileInactiveEventsDisconnect() {
        assertTrue(chatHostIsForeground(ChatHostLifecycleEvent.EnterForeground))
        assertTrue(chatHostIsForeground(ChatHostLifecycleEvent.BecomeActive))
        assertFalse(chatHostIsForeground(ChatHostLifecycleEvent.ResignActive))
        assertFalse(chatHostIsForeground(ChatHostLifecycleEvent.EnterBackground))
        assertFalse(chatHostIsForeground(ChatHostLifecycleEvent.Dispose))
    }
}
