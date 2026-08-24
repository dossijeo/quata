package com.quata.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        assertTrue(sections.getValue("flags").emojis.all { flag ->
            val codePoints = flag.unicodeCodePoints()
            codePoints.size == 2 && codePoints.all { it in RegionalIndicatorRange }
        })
    }

    @Test
    fun `every one of the 338 catalog entries maps to a deterministic atlas cell`() {
        val sections = communityEmojiSections()
        assertEquals(338, sections.sumOf { it.emojis.size })
        sections.forEach { section ->
            section.emojis.indices.forEach { index ->
                val (column, row) = communityEmojiAtlasCoordinates(section.key, index)
                assertTrue(column in 0..5, "${section.key}[$index] must stay in the six-column atlas")
                assertEquals(index / 6, row, "${section.key}[$index] must preserve catalog order")
            }
        }
        assertFailsWith<IllegalArgumentException> { communityEmojiAtlasCoordinates("flags", 34) }
    }

    @Test
    fun `panel selected section falls back safely and preserves empty sections for UI state`() {
        val emptyFrequent = QuataEmojiSection("frequent", "Frequent", emptyList())
        val recent = QuataEmojiSection("recent", "Recent", listOf("😀"))

        assertEquals(emptyFrequent, communityEmojiPanelSelectedSection(listOf(emptyFrequent, recent), "frequent"))
        assertEquals(emptyFrequent, communityEmojiPanelSelectedSection(listOf(emptyFrequent, recent), "missing"))
        assertEquals(null, communityEmojiPanelSelectedSection(emptyList(), "frequent"))
    }

    @Test
    fun `panel exposes stable anchors for empty error and retry states`() {
        assertEquals("community.emoji.empty", CommunityEmojiPanelEmptyTestTag)
        assertEquals("community.emoji.error", CommunityEmojiPanelErrorTestTag)
        assertEquals("community.emoji.retry", CommunityEmojiPanelRetryTestTag)
    }

    @Test
    fun `catalog provider fails closed when atlas validation fails`() {
        val available = communityEmojiCatalogState()
        assertTrue(available is CommunityEmojiCatalogState.Available)

        var retried = false
        val unavailable = communityEmojiCatalogState(
            labels = CommunityEmojiLabels(empty = "Emoji unavailable"),
            onRetry = { retried = true },
            atlasCellCountResolver = { key -> if (key == "frequent") 1 else 400 },
        )

        assertTrue(unavailable is CommunityEmojiCatalogState.Unavailable)
        assertEquals("Emoji unavailable", unavailable.message)
        unavailable.onRetry?.invoke()
        assertTrue(retried)
    }
}

private val RegionalIndicatorRange = 0x1F1E6..0x1F1FF

/** JVM-free UTF-16 decoder so this common test also exercises Wasm and native targets. */
private fun String.unicodeCodePoints(): List<Int> = buildList {
    var index = 0
    while (index < this@unicodeCodePoints.length) {
        val high = this@unicodeCodePoints[index]
        val low: Char? = if (index + 1 < this@unicodeCodePoints.length) this@unicodeCodePoints[index + 1] else null
        if (high in '\uD800'..'\uDBFF' && low != null && low in '\uDC00'..'\uDFFF') {
            add(0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00))
            index += 2
        } else {
            add(high.code)
            index += 1
        }
    }
}
