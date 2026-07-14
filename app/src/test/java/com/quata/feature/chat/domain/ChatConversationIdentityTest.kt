package com.quata.feature.chat.domain

import com.quata.core.model.Conversation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatConversationIdentityTest {
    @Test
    fun exactPrivateConversationMatchesOnlyActorAndPeer() {
        assertTrue(conversation("actor", "peer").isExactPrivateConversation("actor", "peer"))
        assertTrue(conversation("peer", "actor").isExactPrivateConversation("actor", "peer"))
    }

    @Test
    fun groupContainingPeerIsNeverTreatedAsPrivate() {
        assertFalse(
            conversation("actor", "peer", "third", isGroup = true)
                .isExactPrivateConversation("actor", "peer")
        )
    }

    @Test
    fun malformedNonGroupWithAdditionalParticipantIsRejected() {
        assertFalse(
            conversation("actor", "peer", "third")
                .isExactPrivateConversation("actor", "peer")
        )
    }

    @Test
    fun differentPrivatePeerIsRejected() {
        assertFalse(conversation("actor", "other").isExactPrivateConversation("actor", "peer"))
    }

    private fun conversation(vararg participantIds: String, isGroup: Boolean = false) = Conversation(
        id = "conversation",
        title = "Conversation",
        lastMessagePreview = "",
        participantIds = participantIds.toList(),
        isGroup = isGroup
    )
}
