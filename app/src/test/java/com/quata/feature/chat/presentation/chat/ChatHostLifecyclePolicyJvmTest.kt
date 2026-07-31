package com.quata.feature.chat.presentation.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHostLifecyclePolicyJvmTest {
    @Test
    fun productiveForegroundPolicyMatchesPlatformLifecycleEvents() {
        assertTrue(chatHostIsForeground(ChatHostLifecycleEvent.EnterForeground))
        assertTrue(chatHostIsForeground(ChatHostLifecycleEvent.BecomeActive))
        assertFalse(chatHostIsForeground(ChatHostLifecycleEvent.ResignActive))
        assertFalse(chatHostIsForeground(ChatHostLifecycleEvent.EnterBackground))
        assertFalse(chatHostIsForeground(ChatHostLifecycleEvent.Dispose))
    }
}
