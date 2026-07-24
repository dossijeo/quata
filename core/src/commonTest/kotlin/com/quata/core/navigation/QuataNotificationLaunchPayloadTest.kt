package com.quata.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class QuataNotificationLaunchPayloadTest {
    @Test
    fun mapsWebPushThreadPayloadToSupabaseConversation() {
        val target = mapOf("thread_id" to "123", "message_id" to "456")
            .quataNotificationChatDeepLinkOrNull()

        assertEquals(QuataChatDeepLink("sb:123", "456"), target)
    }

    @Test
    fun preservesExplicitConversationIdOverLegacyThreadId() {
        val target = mapOf("conversation_id" to "sb:900", "thread_id" to "123")
            .quataNotificationChatDeepLinkOrNull()

        assertEquals(QuataChatDeepLink("sb:900", null), target)
    }

    @Test
    fun prefersAValidQuataUrl() {
        val target = QuataNotificationLaunchPayload(
            deepLink = quataChatUrl("sb:42", "m-1"),
            conversationId = "ignored",
        ).quataChatDeepLinkOrNull()

        assertEquals(QuataChatDeepLink("sb:42", "m-1"), target)
    }
}
