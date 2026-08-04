package com.quata.feature.chat.presentation.conversations

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationAvatarPresentationTest {
    private val me = User("me", "me@quata.test", "Yo")
    private val other = User("other", "other@quata.test", "Gabrielo", avatarUrl = "https://cdn/avatar.jpg")

    @Test fun privateConversationUsesOtherParticipantProfile() {
        val result = resolve(conversation(participantIds = listOf(me.id, other.id)), mapOf(other.id to other), opening = other.id)
        assertEquals(ConversationAvatarKind.Private, result.kind)
        assertEquals(other.id, result.profileId)
        assertEquals(other.id, result.stableId)
        assertEquals(other.displayName, result.name)
        assertEquals(other.avatarUrl, result.avatarUrl)
        assertTrue(result.isLoading)
    }

    @Test fun groupUsesConversationIdentityAndIsNotClickable() {
        val result = resolve(conversation(isGroup = true, avatarUrl = "https://cdn/group.jpg"), emptyMap())
        assertEquals(ConversationAvatarKind.Group, result.kind)
        assertEquals("conversation", result.stableId)
        assertEquals("Grupo", result.name)
        assertEquals("https://cdn/group.jpg", result.avatarUrl)
        assertNull(result.profileId)
    }

    @Test fun sosHasDedicatedIdentityAndPreservesMuted() {
        val result = resolve(conversation(isEmergency = true, isMuted = true), emptyMap())
        assertEquals(ConversationAvatarKind.Sos, result.kind)
        assertEquals("SOS", result.name)
        assertNull(result.avatarUrl)
        assertNull(result.profileId)
        assertTrue(result.isMuted)
    }

    @Test fun missingPrivateUserFallsBackWithoutFalseProfileLink() {
        val result = resolve(conversation(participantIds = listOf(me.id, "missing"), avatarUrl = "https://cdn/conversation.jpg"), emptyMap())
        assertEquals(ConversationAvatarKind.Private, result.kind)
        assertEquals("conversation", result.stableId)
        assertEquals("Grupo", result.name)
        assertEquals("https://cdn/conversation.jpg", result.avatarUrl)
        assertNull(result.profileId)
        assertFalse(result.isLoading)
    }

    @Test fun messageAvatarUsesResolvedSenderImageAndProfile() {
        val message = Message(
            id = "message",
            conversationId = "conversation",
            senderId = other.id,
            senderName = "Stale name",
            text = "Mbolo",
            sentAt = "2026-08-04T08:03:00Z",
        )

        val result = resolveMessageAvatarPresentation(message, other, openingProfileUserId = other.id)

        assertEquals(ConversationAvatarKind.Private, result.kind)
        assertEquals(other.displayName, result.name)
        assertEquals(other.avatarUrl, result.avatarUrl)
        assertEquals(other.id, result.profileId)
        assertEquals(other.id, result.stableId)
        assertTrue(result.isLoading)
    }

    @Test fun messageAvatarFallsBackToMessageIdentityWithoutSenderRecord() {
        val message = Message(
            id = "message",
            conversationId = "conversation",
            senderId = "missing",
            senderName = "Invitado",
            text = "Hola",
            sentAt = "2026-08-04T08:03:00Z",
        )

        val result = resolveMessageAvatarPresentation(message, sender = null, openingProfileUserId = null)

        assertEquals("Invitado", result.name)
        assertEquals("missing", result.stableId)
        assertEquals("missing", result.profileId)
        assertNull(result.avatarUrl)
        assertFalse(result.isLoading)
    }

    private fun resolve(value: Conversation, users: Map<String, User>, opening: String? = null) =
        resolveConversationAvatarPresentation(value, me, users, "Grupo", opening)

    private fun conversation(
        participantIds: List<String> = emptyList(), isGroup: Boolean = false, isEmergency: Boolean = false,
        avatarUrl: String? = null, isMuted: Boolean = false,
    ) = Conversation("conversation", "Grupo", avatarUrl, "", participantIds = participantIds, isGroup = isGroup, isEmergency = isEmergency, isMuted = isMuted)
}
