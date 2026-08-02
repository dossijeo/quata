package com.quata.feature.profile.data

import com.quata.feature.profile.domain.ProfileUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
        assertFalse(patch.containsKey("secret_question"))
        assertFalse(patch.containsKey("secret_answer"))
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

    @Test
    fun `profile password cannot report success without an atomic bridge contract`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            requireProfilePasswordUpdateSupported("NewPassword7")
        }
        assertEquals("profile_password_update_unavailable", failure.message)
        requireProfilePasswordUpdateSupported("")
    }

    @Test
    fun `password rejection happens before any remote mutation boundary`() {
        val calls = mutableListOf<String>()
        val rejected = runCatching {
            requireProfilePasswordUpdateSupported("NewPassword7")
            calls += "remote_mutation"
        }.exceptionOrNull()
        assertEquals("profile_password_update_unavailable", rejected?.message)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `sos candidate retains its remote avatar for the shared row slot`() {
        val candidate = ProfileRemoteRecord(
            id = "sos-1",
            displayName = "SOS",
            avatarUrl = "https://cdn.example/sos.jpg",
        ).toEmergencyCandidate()
        assertEquals("https://cdn.example/sos.jpg", candidate.avatarUrl)
    }
}
