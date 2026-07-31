package com.quata.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class QuataEmojiVisualTransformationTest {
    @Test
    fun `catalog emoji become one layout placeholder and retain UTF-16 endpoints`() {
        val emoji = "❤️‍🔥"
        val visual = quataEmojiVisualText("a${emoji}b")

        assertEquals("a${QuataEmojiPlaceholder}b", visual.text)
        assertEquals(1, visual.offsetMapping.originalToTransformed(1))
        assertEquals(1, visual.offsetMapping.originalToTransformed(1 + emoji.length - 1))
        assertEquals(2, visual.offsetMapping.originalToTransformed(1 + emoji.length))
        assertEquals(1, visual.offsetMapping.transformedToOriginal(1))
        assertEquals(1 + emoji.length, visual.offsetMapping.transformedToOriginal(2))
    }

    @Test
    fun `flag and zwj clusters stay atomic at start middle and end`() {
        val flag = "🇪🇸"
        val zwj = "👨‍⚕️"
        val visual = quataEmojiVisualText("${flag}x${zwj}")

        assertEquals("${QuataEmojiPlaceholder}x${QuataEmojiPlaceholder}", visual.text)
        assertEquals(1, visual.offsetMapping.originalToTransformed(flag.length))
        assertEquals(2, visual.offsetMapping.originalToTransformed(flag.length + 1))
        assertEquals(flag.length + 1, visual.offsetMapping.transformedToOriginal(2))
    }

    @Test
    fun `every catalog and fixed feed glyph has one presentation placeholder`() {
        val value = "${QuataFeedEmoji.Sos} ${QuataFeedEmoji.Rank} ${QuataFeedEmoji.Location} ${QuataFeedEmoji.Note} ${QuataFeedEmoji.Document}"
        assertEquals(5, quataEmojiVisualText(value).text.count { it == QuataEmojiPlaceholder })
        val catalog = communityEmojiSections().flatMap { it.emojis }
        assertEquals(338, catalog.size)
        catalog.forEach { emoji ->
            assertEquals(QuataEmojiPlaceholder.toString(), quataEmojiVisualText(emoji).text, emoji)
        }
    }
}
