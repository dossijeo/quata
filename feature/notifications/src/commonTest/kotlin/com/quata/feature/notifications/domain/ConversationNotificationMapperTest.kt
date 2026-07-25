package com.quata.feature.notifications.domain

import com.quata.core.model.Conversation
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationNotificationMapperTest {
    @Test
    fun mapsOnlyUnreadVisibleUnmutedConversationsOutsideTheActiveThread() {
        val items = listOf(
            conversation(id = "active", unreadCount = 2),
            conversation(id = "muted", unreadCount = 3, isMuted = true),
            conversation(id = "hidden", unreadCount = 4, isVisible = false),
            conversation(id = "read", unreadCount = 0),
            conversation(id = "community", unreadCount = 5, communityName = "Centro"),
        ).toConversationNotificationItems(activeConversationId = "active")

        assertEquals(1, items.size)
        assertEquals("notification_community", items.single().id)
        assertEquals("Centro", items.single().title)
        assertEquals(5, items.single().unreadCount)
    }

    @Test
    fun fallsBackToParticipantsForUntitledGroups() {
        val item = conversation(
            id = "group",
            title = "",
            unreadCount = 1,
            isGroup = true,
            participantNames = listOf("Ana", "Bea", "Cora", "Dani"),
        ).let(::listOf).toConversationNotificationItems(activeConversationId = null).single()

        assertEquals("Ana, Bea, Cora", item.title)
        assertEquals("", item.body)
    }

    private fun conversation(
        id: String,
        title: String = "Chat",
        unreadCount: Int,
        isMuted: Boolean = false,
        isVisible: Boolean = true,
        isGroup: Boolean = false,
        communityName: String? = null,
        participantNames: List<String> = emptyList(),
    ) = Conversation(
        id = id,
        title = title,
        lastMessagePreview = "",
        unreadCount = unreadCount,
        isMuted = isMuted,
        isVisible = isVisible,
        isGroup = isGroup,
        communityName = communityName,
        participantNames = participantNames,
    )
}
