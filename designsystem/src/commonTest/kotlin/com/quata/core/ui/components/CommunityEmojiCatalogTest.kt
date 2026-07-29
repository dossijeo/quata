package com.quata.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommunityEmojiCatalogTest {
    @Test
    fun `catalog preserves the Android section order and sizes`() {
        val sections = communityEmojiSections()
        assertEquals(listOf("recent", "frequent", "gestures", "people", "animals_nature", "food_drink", "objects_symbols", "flags"), sections.map { it.key })
        assertEquals(listOf(24, 45, 35, 34, 58, 57, 51, 34), sections.map { it.emojis.size })
    }

    @Test
    fun `catalog preserves representative emoji codepoints including zwj and regional flags`() {
        val sections = communityEmojiSections().associateBy { it.key }
        assertEquals("😀", sections.getValue("frequent").emojis.first())
        assertEquals("❤️‍🔥", sections.getValue("frequent").emojis.last { it.contains("🔥") })
        assertEquals("👨‍⚕️", sections.getValue("people").emojis[18])
        assertEquals("🐻‍❄️", sections.getValue("animals_nature").emojis[8])
        assertEquals("🇪🇸", sections.getValue("flags").emojis.first())
        assertEquals("🇨🇦", sections.getValue("flags").emojis.last())
        assertTrue(sections.getValue("flags").emojis.all { it.codePointCount(0, it.length) == 2 })
    }
}
