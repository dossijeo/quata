package com.quata.feature.profile.data

import com.quata.feature.profile.domain.ProfileUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KmpProfileRepositoryTest {
    @Test
    fun `remote patch preserves canonical and compatibility fields`() {
        val patch = ProfileUpdate(
            displayName = "Ada",
            neighborhood = "Centro",
            countryCode = "+240",
            phone = " 555 123 ",
            avatarUri = "  https://cdn.example/avatar.jpg ",
            newPassword = "",
            secretQuestion = "pet",
            secretAnswer = "Luna",
            emergencyContactIds = emptyList(),
            emergencyMessage = "SOS",
            emergencyMessageIsDefault = false
        ).toRemotePatch()

        assertEquals("+240555123", patch["phone"])
        assertEquals("Ada", patch["display_name"])
        assertEquals("Ada", patch["nombre"])
        assertEquals("https://cdn.example/avatar.jpg", patch["avatar_url"])
        assertEquals("Luna", patch["secret_answer"])
    }

    @Test
    fun `remote profile normalizes phone and prefers custom emergency message`() {
        val profile = ProfileRemoteRecord(
            id = "profile-1",
            displayName = " Ada ",
            countryCode = "+240",
            phoneLocal = "555 123",
            avatarUrl = "  "
        ).toUserProfile(
            fallbackName = "Fallback",
            emergencyContactIds = listOf("contact"),
            storedMessage = StoredProfileEmergencyMessage("Ayuda", isDefault = false),
            defaultMessage = { "Default $it" }
        )

        assertEquals("Ada", profile.displayName)
        assertEquals("240", profile.countryCode)
        assertEquals("555123", profile.phone)
        assertEquals("Ayuda", profile.emergencyMessage)
        assertNull(profile.avatarUri)
    }

    @Test
    fun `emergency contacts are trimmed deduplicated and limited`() {
        assertEquals(
            listOf("a", "b", "c", "d", "e"),
            normalizeEmergencyContactIds(listOf(" a ", "", "a", "b", "c", "d", "e", "f"))
        )
    }
}
