package com.quata.feature.chat.presentation.chat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatSoundPolicyTest {
    @Test
    fun initialSnapshotNeverPlaysIncomingSound() {
        assertFalse(shouldPlayIncomingChatSound(false, true, 0, 3))
    }

    @Test
    fun newIncomingMessagePlaysOnlyInForeground() {
        assertTrue(shouldPlayIncomingChatSound(true, true, 2, 3))
        assertFalse(shouldPlayIncomingChatSound(true, false, 2, 3))
        assertFalse(shouldPlayIncomingChatSound(true, true, 3, 3))
    }
}
