package com.quata.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarFallbackTest {
    @Test
    fun initialsUseFirstAndLastWords() {
        assertEquals("GG", avatarInitials("Gabriel García"))
    }

    @Test
    fun initialsUseFirstTwoLettersForSingleName() {
        assertEquals("GA", avatarInitials("Gabrielu"))
    }

    @Test
    fun colorIsStableForTheSameProfileId() {
        val profileId = "757edf3f-8b7f-40cf-b775-cd3ef3b07f4c"
        assertEquals(
            avatarFallbackColorArgb(profileId),
            avatarFallbackColorArgb(profileId)
        )
    }
}
