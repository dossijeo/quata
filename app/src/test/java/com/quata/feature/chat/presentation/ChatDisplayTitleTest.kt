package com.quata.feature.chat.presentation

import com.quata.core.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatDisplayTitleTest {
    @Test
    fun generatedBackendTitleFallsBackToParticipantNames() {
        val conversation = group(title = "Chat 68")

        assertEquals("Gabriele, Contenta", conversation.chatDisplayTitle())
    }

    @Test
    fun explicitGroupTitleIsPreserved() {
        val conversation = group(title = "QA Estados")

        assertEquals("QA Estados", conversation.chatDisplayTitle())
    }

    private fun group(title: String) = Conversation(
        id = "sb:68",
        title = title,
        lastMessagePreview = "",
        participantIds = listOf("current", "gabriele", "contenta"),
        participantNames = listOf("Gabriele", "Contenta"),
        isGroup = true
    )
}
