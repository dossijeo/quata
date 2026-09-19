package com.quata.feature.chat.presentation.conversations

import com.quata.core.platform.PlatformContact
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationInvitePlatformAdaptersTest {
    @Test
    fun selectedContactsBecomeStablePhoneDiscoveryCandidates() {
        val mapped = platformContactsForChatInvites(
            listOf(
                PlatformContact(
                    displayName = " Ada Invitada ",
                    phones = listOf("+34 611 111 111", "611-111-111"),
                    emails = listOf("ada@example.test"),
                ),
                PlatformContact(displayName = "Sin teléfono", emails = listOf("only@example.test")),
            )
        )

        assertEquals(1, mapped.size)
        assertEquals("Ada Invitada", mapped.single().displayName)
        assertEquals("+34 611 111 111", mapped.single().phone)
        assertEquals(setOf("34611111111", "611111111"), mapped.single().phoneKeys)
        assertEquals("+34 611 111 111", mapped.single().internationalPhone)
        assertEquals("platform-contact:34611111111:611111111", mapped.single().id)
    }
}
