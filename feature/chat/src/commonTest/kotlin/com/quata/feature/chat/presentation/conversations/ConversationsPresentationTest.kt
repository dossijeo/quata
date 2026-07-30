package com.quata.feature.chat.presentation.conversations

import com.quata.feature.chat.domain.ChatInviteContact
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationsPresentationTest {
    private val strings = defaultConversationsStrings("en")
    private val now = 1_000_000_000L
    private val inviteContacts = listOf(
        ChatInviteContact("1", "Ada Invitada", "+34 611 111 111", setOf("34611111111")),
        ChatInviteContact("2", "Bruno Prueba", "+34 622 222 222", setOf("34622222222")),
    )

    @Test fun relativeTime_coversBoundaries() {
        assertEquals("1 sec ago", strings.relativeTime("", now - 1_000L, now))
        assertEquals("59 sec ago", strings.relativeTime("", now - 59_000L, now))
        assertEquals("1 min ago", strings.relativeTime("", now - 60_000L, now))
        assertEquals("59 min ago", strings.relativeTime("", now - 59 * 60_000L, now))
        assertEquals("1 hr ago", strings.relativeTime("", now - 60 * 60_000L, now))
        assertEquals("6 days ago", strings.relativeTime("", now - 6 * 24 * 60 * 60_000L, now))
        assertEquals("1 week ago", strings.relativeTime("", now - 7 * 24 * 60 * 60_000L, now))
    }

    @Test fun preview_tokens_match_android_catalogue() {
        assertEquals("🖼️ Photo", strings.localizePreview("[QUATA_ATTACHMENT:photo]"))
        assertEquals("🎤 Voice note", strings.localizePreview("[QUATA_NOTIFICATION:chat_voice_note]"))
        assertEquals("SOS location update", strings.localizePreview("[SOS:kind=update;name=Ada]"))
        assertEquals("📍 Location unavailable", strings.localizePreview("[SOS:kind=alert;name=Ada]"))
    }

    @Test fun timestamp_parser_accepts_iso_utc_and_local_with_or_without_fractional_seconds() {
        assertEquals(1_785_408_137_123L, parseConversationUpdatedAtMillis("2026-07-30T10:42:17.123Z", now))
        assertEquals(1_785_408_137_000L, parseConversationUpdatedAtMillis("2026-07-30T10:42:17Z", now))
        // Local forms intentionally only assert parseability: their epoch is the platform system zone.
        kotlin.test.assertNotNull(parseConversationUpdatedAtMillis("2026-07-30T10:42:17.123", now))
        kotlin.test.assertNotNull(parseConversationUpdatedAtMillis("2026-07-30T10:42:17", now))
    }

    @Test fun invite_search_matches_name() {
        assertEquals(listOf("1"), filterPickerInviteContacts(inviteContacts, "ada").map { it.id })
    }

    @Test fun invite_search_matches_unformatted_phone_digits() {
        assertEquals(listOf("2"), filterPickerInviteContacts(inviteContacts, "622222").map { it.id })
    }
}
