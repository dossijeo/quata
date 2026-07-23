package com.quata.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuataDeepLinksTest {
    @Test
    fun postAndOfficialLinksKeepTheirPublicFormat() {
        assertEquals("https://egquata.com/#post-42", quataPostUrl("42"))
        assertEquals("https://egquata.com/#official-42", quataOfficialPostUrl("42"))
        assertEquals("42", "https://egquata.com/#post-42".quataPostIdOrNull())
        assertEquals("42", "https://www.egquata.com/#official-42".quataOfficialPostIdOrNull())
    }

    @Test
    fun legacyChatLinkStillResolvesConversation() {
        val link = "https://egquata.com/#chat-sb%3A12"
        assertEquals(QuataChatDeepLink("sb:12", null), link.quataChatDeepLinkOrNull())
    }

    @Test
    fun chatLinkRoundTripsConversationAndFocusedMessage() {
        val link = quataChatUrl(conversationId = "sb:12/á", messageId = "msg 9")
        assertEquals(
            QuataChatDeepLink(conversationId = "sb:12/á", messageId = "msg 9"),
            link.quataChatDeepLinkOrNull(),
        )
    }

    @Test
    fun foreignHostIsNotAccepted() {
        assertNull("https://example.com/#chat-sb%3A12".quataChatDeepLinkOrNull())
    }
}
