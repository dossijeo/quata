package com.quata.feature.profile.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class EmergencyContactSelectionTest {
    @Test
    fun toggleAddsThenRemovesWhilePreservingSelectionOrder() {
        assertEquals(
            listOf("first", "second", "third"),
            toggleEmergencyContactSelection(listOf("first", "second"), "third"),
        )
        assertEquals(
            listOf("first", "third"),
            toggleEmergencyContactSelection(listOf("first", "second", "third"), "second"),
        )
    }

    @Test
    fun selectionIsNormalizedAndDoesNotExceedFiveContacts() {
        assertEquals(
            listOf("one", "two", "three", "four", "five"),
            toggleEmergencyContactSelection(
                listOf("one", "two", "two", "three", "four", "five", "six"),
                "seven",
            ),
        )
    }
}
