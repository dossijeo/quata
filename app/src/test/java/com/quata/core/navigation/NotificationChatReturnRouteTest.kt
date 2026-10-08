package com.quata.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationChatReturnRouteTest {
    @Test
    fun returnsNotificationsOnlyForTheExactConversation() {
        assertEquals(
            AppDestinations.Notifications.route,
            notificationChatReturnRoute(
                currentConversationId = "sb:notification/42",
                storedConversationId = "sb:notification/42",
                storedRoute = AppDestinations.Notifications.route,
            ),
        )
        assertNull(
            notificationChatReturnRoute(
                currentConversationId = "sb:unrelated",
                storedConversationId = "sb:notification/42",
                storedRoute = AppDestinations.Notifications.route,
            ),
        )
    }

    @Test
    fun rejectsBlankConversationAndUnrelatedReturnRoutes() {
        assertNull(
            notificationChatReturnRoute(
                currentConversationId = "",
                storedConversationId = "",
                storedRoute = AppDestinations.Notifications.route,
            ),
        )
        assertNull(
            notificationChatReturnRoute(
                currentConversationId = "sb:notification/42",
                storedConversationId = "sb:notification/42",
                storedRoute = AppDestinations.Neighborhoods.route,
            ),
        )
    }

    @Test
    fun clearsTheReturnWhenLeavingChatOrLoggingOut() {
        assertEquals(
            false,
            shouldClearNotificationChatReturn(
                previousRoute = AppDestinations.Chat.route,
                currentRoute = AppDestinations.Chat.route,
                isAuthenticated = true,
            ),
        )
        assertEquals(
            true,
            shouldClearNotificationChatReturn(
                previousRoute = AppDestinations.Chat.route,
                currentRoute = AppDestinations.Feed.route,
                isAuthenticated = true,
            ),
        )
        assertEquals(
            true,
            shouldClearNotificationChatReturn(
                previousRoute = AppDestinations.Chat.route,
                currentRoute = AppDestinations.Chat.route,
                isAuthenticated = false,
            ),
        )
    }
}
