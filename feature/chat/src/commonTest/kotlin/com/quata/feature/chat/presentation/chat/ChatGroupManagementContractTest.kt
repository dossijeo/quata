package com.quata.feature.chat.presentation.chat

import com.quata.core.model.Conversation
import com.quata.core.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatGroupManagementContractTest {
    private val currentUser = User("self", "self@example.test", "Yo")

    @Test
    fun moderatorCanManageAndInviteRegardlessOfMemberInviteFlag() {
        val conversation = conversation(moderatorIds = listOf(currentUser.id), canMembersInvite = false)

        assertTrue(canManageChatMembers(conversation, currentUser))
        assertTrue(canInviteToChat(conversation, currentUser))
    }

    @Test
    fun ordinaryMemberCanInviteOnlyWhenConversationAllowsIt() {
        assertFalse(canInviteToChat(conversation(canMembersInvite = false), currentUser))
        assertTrue(canInviteToChat(conversation(canMembersInvite = true), currentUser))
    }

    @Test
    fun memberPresentationUsesAlignedNamesAvatarsRolesAndProfileSafety() {
        val conversation = conversation(
            participantIds = listOf("self", "person-2", "wp:legacy"),
            participantNames = listOf("Gabriel", "Lucía", "Invitado"),
            participantAvatarUrls = listOf(null, "https://example.test/lucia.jpg", null),
            moderatorIds = listOf("person-2"),
        )

        val members = chatMemberPresentations(conversation, currentUser)

        assertEquals(listOf("Gabriel", "Lucía", "Invitado"), members.map { it.name })
        assertTrue(members[0].isSelf)
        assertTrue(members[1].isModerator)
        assertEquals("https://example.test/lucia.jpg", members[1].avatarUrl)
        assertFalse(members[2].canOpenProfile)
    }

    private fun conversation(
        participantIds: List<String> = listOf("self", "person-2"),
        participantNames: List<String> = listOf("Gabriel", "Lucía"),
        participantAvatarUrls: List<String?> = listOf(null, null),
        moderatorIds: List<String> = emptyList(),
        canMembersInvite: Boolean = false,
    ) = Conversation(
        id = "conversation-1",
        title = "Grupo",
        lastMessagePreview = "",
        participantIds = participantIds,
        participantNames = participantNames,
        participantAvatarUrls = participantAvatarUrls,
        isGroup = true,
        moderatorIds = moderatorIds,
        canMembersInvite = canMembersInvite,
    )
}
