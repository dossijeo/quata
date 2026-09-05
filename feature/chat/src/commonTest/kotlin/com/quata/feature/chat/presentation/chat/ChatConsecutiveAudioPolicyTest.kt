package com.quata.feature.chat.presentation.chat

import com.quata.core.model.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatConsecutiveAudioPolicyTest {
    @Test
    fun selectsOnlyTheTemporallyFollowingAudioFromTheSameSender() {
        val first = message("1", "sender-a", "audio/ogg", sentAtMillis = 1_000)
        val second = message("2", "sender-a", "audio/mp4", sentAtMillis = 2_000)
        assertEquals(second, nextConsecutiveAudioMessage(listOf(first, second), first.composeKey()))
        assertEquals(second, nextConsecutiveAudioMessage(listOf(second, first), first.composeKey()))
        assertNull(nextConsecutiveAudioMessage(listOf(first, second), second.composeKey()))

        assertNull(nextConsecutiveAudioMessage(listOf(first, second.copy(senderId = "sender-b")), first.composeKey()))
        assertNull(nextConsecutiveAudioMessage(listOf(first, second.copy(attachmentMimeType = "image/jpeg")), first.composeKey()))
        assertNull(nextConsecutiveAudioMessage(listOf(first, second.copy(isDeleted = true)), first.composeKey()))
    }

    @Test
    fun acceptsLegacyAudioExtensionsWithoutMimeType() {
        val first = message("1", "sender-a", "audio/ogg", sentAtMillis = 1_000)
        val second = message("2", "sender-a", null, attachmentName = "voice-message.m4a", sentAtMillis = 2_000)

        assertEquals(second, nextConsecutiveAudioMessage(listOf(first, second), first.composeKey()))
    }

    @Test
    fun descendingRepositoryOrderStillStopsAtTheNewestAudio() {
        val first = message("1", "sender-a", "audio/ogg", sentAtMillis = 1_000)
        val second = message("2", "sender-a", "audio/ogg", sentAtMillis = 2_000)
        val newestFirst = listOf(second, first)

        assertEquals(second, nextConsecutiveAudioMessage(newestFirst, first.composeKey()))
        assertNull(nextConsecutiveAudioMessage(newestFirst, second.composeKey()))
    }

    private fun message(
        id: String,
        senderId: String,
        mimeType: String?,
        attachmentName: String = "$id.ogg",
        sentAtMillis: Long? = null,
    ) = Message(
        id = id,
        conversationId = "conversation",
        senderId = senderId,
        senderName = senderId,
        text = "",
        sentAt = "",
        sentAtMillis = sentAtMillis,
        attachmentUri = "https://example.test/$id",
        attachmentName = attachmentName,
        attachmentMimeType = mimeType,
    )
}
