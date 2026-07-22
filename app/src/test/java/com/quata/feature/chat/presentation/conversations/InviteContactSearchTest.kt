package com.quata.feature.chat.presentation.conversations

import com.quata.feature.chat.domain.ChatInviteContact
import org.junit.Assert.assertEquals
import org.junit.Test

class InviteContactSearchTest {
    private val contacts = listOf(
        ChatInviteContact("1", "Ada Invitada", "+34 611 111 111", setOf("34611111111")),
        ChatInviteContact("2", "Bruno Prueba", "+34 622 222 222", setOf("34622222222"))
    )

    @Test
    fun filtersInvitesByName() {
        assertEquals(listOf("1"), filterInviteContacts(contacts, "ada").map { it.id })
    }

    @Test
    fun filtersInvitesByUnformattedPhoneDigits() {
        assertEquals(listOf("2"), filterInviteContacts(contacts, "622222").map { it.id })
    }
}
