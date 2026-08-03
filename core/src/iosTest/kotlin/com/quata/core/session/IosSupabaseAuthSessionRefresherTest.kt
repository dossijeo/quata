package com.quata.core.session

import kotlin.test.Test
import kotlin.test.assertEquals

class IosSupabaseAuthSessionRefresherTest {
    @Test
    fun refreshTokenJsonEscapingIteratesTheTokenInsteadOfTheGrowingBuilder() {
        assertEquals(
            "\"refresh\\\\token\\\"with\\ncontrols\"",
            "refresh\\token\"with\ncontrols".toIosJsonString(),
        )
    }
}
