package com.quata.feature.profile.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProfileDetailsValidationTest {
    @Test
    fun `valid details mirror registration normalization`() {
        val result = assertIs<ProfileDetailsValidation.Valid>(
            validateProfileDetails(update(displayName = "  Ｇabriela\t  Robles  ", neighborhood = "  Centro\n Norte ", countryCode = "+34", phone = "600 100 200")),
        )

        assertEquals("Gabriela Robles", result.update.displayName)
        assertEquals("Centro Norte", result.update.neighborhood)
        assertEquals("34", result.update.countryCode)
        assertEquals("600100200", result.update.phone)
    }

    @Test
    fun `invalid text country and phone boundaries return stable codes`() {
        assertInvalid(ProfileInvalidDisplayName, update(displayName = "A"))
        assertInvalid(ProfileInvalidDisplayName, update(displayName = "A\u0000B"))
        assertInvalid(ProfileInvalidNeighborhood, update(neighborhood = "X"))
        assertInvalid(ProfileInvalidCountryCode, update(countryCode = "000"))
        assertInvalid(ProfileInvalidPhone, update(phone = "12345"))
        assertInvalid(ProfileInvalidPhone, update(countryCode = "240", phone = "12345678901234"))
    }

    private fun assertInvalid(code: String, update: ProfileUpdate) {
        assertEquals(code, assertIs<ProfileDetailsValidation.Invalid>(validateProfileDetails(update)).code)
    }

    private fun update(
        displayName: String = "Gabriela",
        neighborhood: String = "Centro",
        countryCode: String = "240",
        phone: String = "555123456",
    ) = ProfileUpdate(
        displayName = displayName,
        neighborhood = neighborhood,
        countryCode = countryCode,
        phone = phone,
        avatarUri = null,
        newPassword = "",
        secretQuestion = "",
        secretAnswer = "",
        emergencyContactIds = emptyList(),
        emergencyMessage = "SOS",
        emergencyMessageIsDefault = true,
    )
}
