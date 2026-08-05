package com.quata.feature.chat.presentation.chat

import com.quata.core.model.Message
import com.quata.core.platform.AudioPlaybackState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatConsecutiveAudioPolicyTest {
    @Test
    fun selectsOnlyTheImmediatelyFollowingAudioFromTheSameSender() {
        val first = message("1", "sender-a", "audio/ogg")
        val second = message("2", "sender-a", "audio/mp4")
        assertEquals(second, nextConsecutiveAudioMessage(listOf(first, second), first.composeKey()))

        assertNull(nextConsecutiveAudioMessage(listOf(first, second.copy(senderId = "sender-b")), first.composeKey()))
        assertNull(nextConsecutiveAudioMessage(listOf(first, second.copy(attachmentMimeType = "image/jpeg")), first.composeKey()))
        assertNull(nextConsecutiveAudioMessage(listOf(first, second.copy(isDeleted = true)), first.composeKey()))
    }

    @Test
    fun completionRequiresAPlayingToEndedTransitionNearTheDuration() {
        val playing = AudioPlaybackState(true, true, 9_400L, 10_000L)
        assertTrue(didAudioPlaybackFinish(playing, AudioPlaybackState(true, false, 10_000L, 10_000L)))
        assertTrue(didAudioPlaybackFinish(playing, AudioPlaybackState(true, false, 9_500L, 10_000L)))
        assertFalse(didAudioPlaybackFinish(playing, AudioPlaybackState(true, false, 4_000L, 10_000L)))
        assertFalse(didAudioPlaybackFinish(playing.copy(isPlaying = false), AudioPlaybackState(true, false, 10_000L, 10_000L)))
    }

    private fun message(id: String, senderId: String, mimeType: String) = Message(
        id = id,
        conversationId = "conversation",
        senderId = senderId,
        senderName = senderId,
        text = "",
        sentAt = "",
        attachmentUri = "https://example.test/$id",
        attachmentName = "$id.ogg",
        attachmentMimeType = mimeType,
    )
}
