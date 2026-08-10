package com.quata.feature.notifications.presentation

import com.quata.core.model.NotificationItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationsInteractionContractTest {
    @Test
    fun authenticatedTapMarksReadAndOpensTheExactConversation() {
        val calls = mutableListOf<String>()
        val item = notification("conversation-42")

        handleNotificationClick(
            item = item,
            canMutate = true,
            onMarkRead = { calls += "mark:${it.conversationId}" },
            onOpenConversation = { calls += "open:$it" },
            onAuthenticationRequired = { calls += "auth:${it.conversationId}" },
        )

        assertEquals(listOf("mark:conversation-42", "open:conversation-42"), calls)
    }

    @Test
    fun anonymousTapRequestsAuthenticationWithoutMutatingOrOpening() {
        val calls = mutableListOf<String>()
        val item = notification("conversation-42")

        handleNotificationClick(
            item = item,
            canMutate = false,
            onMarkRead = { calls += "mark:${it.conversationId}" },
            onOpenConversation = { calls += "open:$it" },
            onAuthenticationRequired = { calls += "auth:${it.conversationId}" },
        )

        assertEquals(listOf("auth:conversation-42"), calls)
    }

    @Test
    fun dismissSharesTheSameMutationGateAsTap() {
        var dismissed = false
        var blocked = false

        assertFalse(handleNotificationDismissAttempt(
            canDismiss = false,
            onDismiss = { dismissed = true },
            onDismissBlocked = { blocked = true },
        ))
        assertFalse(dismissed)
        assertTrue(blocked)

        assertTrue(handleNotificationDismissAttempt(
            canDismiss = true,
            onDismiss = { dismissed = true },
            onDismissBlocked = { blocked = false },
        ))
        assertTrue(dismissed)
        assertTrue(blocked)
    }

    private fun notification(conversationId: String) = NotificationItem(
        id = "notification_$conversationId",
        conversationId = conversationId,
        title = "Aviso",
        body = "Mensaje",
        createdAt = "Ahora",
        unreadCount = 1,
    )
}
