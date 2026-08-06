package com.quata.feature.chat.presentation.chat

import com.quata.core.model.Message
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatTimestampTest {
    @Test
    fun sameDayIsoTimestampUsesAndroidHourMinuteContract() {
        val message = message(sentAt = "2026-08-04T07:05:00Z")

        assertEquals(
            "07:05",
            chatMessageTimestampLabel(
                message = message,
                languageTag = "es",
                nowMillis = 1_785_830_400_000L,
                timeZone = TimeZone.UTC,
            ),
        )
    }

    @Test
    fun olderTimestampUsesLocalizedAndroidRelativeTime() {
        val message = message(sentAt = "ignored", sentAtMillis = 1_785_657_600_000L)

        assertEquals(
            "hace 2 d",
            chatMessageTimestampLabel(
                message = message,
                languageTag = "es",
                nowMillis = 1_785_830_400_000L,
                timeZone = TimeZone.UTC,
            ),
        )
    }

    private fun message(sentAt: String, sentAtMillis: Long? = null) = Message(
        id = "1",
        conversationId = "sb:1",
        senderId = "sender",
        senderName = "Nsue",
        text = "Mbolo",
        sentAt = sentAt,
        sentAtMillis = sentAtMillis,
    )
}
