package com.quata.feature.chat.presentation

import com.quata.core.model.Conversation
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatDisplayTitleTest {
    @Test fun emergencyUsesAndroidSosTitle() {
        assertEquals("🚨 SOS", conversation(isEmergency = true, title = "ignored").chatDisplayTitle())
    }

    @Test fun communityNameTakesPriorityOverStoredTitle() {
        assertEquals("Centro", conversation(title = "Chat 42", communityName = "Centro").chatDisplayTitle())
    }

    @Test fun generatedGroupTitleUsesParticipantNames() {
        assertEquals(
            "Gabriele, Contenta",
            conversation(
                id = "thread:42",
                title = "Chat 42",
                isGroup = true,
                participantNames = listOf("Gabriele", "Contenta"),
            ).chatDisplayTitle(),
        )
    }

    @Test fun authoredGroupTitleIsPreserved() {
        assertEquals(
            "QA Estados",
            conversation(title = "QA Estados", isGroup = true, participantNames = listOf("Gabriele", "Contenta")).chatDisplayTitle(),
        )
    }

    private fun conversation(
        id: String = "conversation",
        title: String = "",
        isGroup: Boolean = false,
        isEmergency: Boolean = false,
        communityName: String? = null,
        participantNames: List<String> = emptyList(),
    ) = Conversation(
        id = id,
        title = title,
        lastMessagePreview = "",
        isGroup = isGroup,
        isEmergency = isEmergency,
        communityName = communityName,
        participantNames = participantNames,
    )
}
