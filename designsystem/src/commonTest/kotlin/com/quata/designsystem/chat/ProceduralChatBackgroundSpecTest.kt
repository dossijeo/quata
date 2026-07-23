package com.quata.designsystem.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class ProceduralChatBackgroundSpecTest {
    @Test
    fun producesStableSeedCacheKeyAndPaletteSelection() {
        val spec = proceduralChatBackgroundSpec("Grupo Quata", "dark-mode", paletteCount = 3)

        assertEquals(fnv1a32("Grupo Quata"), spec.seed)
        assertEquals(fnv1a32("dark-mode:Grupo Quata").toString(), spec.cacheKey)
        assertEquals((spec.seed % 3).toInt(), spec.paletteIndex)
    }

    @Test
    fun fallsBackToQuataForEmptyConversationName() {
        assertEquals(
            proceduralChatBackgroundSpec("quata", "light-mode", 1),
            proceduralChatBackgroundSpec("", "light-mode", 1),
        )
    }
}
