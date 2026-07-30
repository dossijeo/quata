package com.quata.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class QuataFeedEmojiTextContractTest {
    @Test
    fun commonGlyphContractsUseTheAndroidCodePoints() {
        assertEquals("🚨", QuataFeedEmoji.Sos)
        assertEquals("🔥", QuataFeedEmoji.Rank)
        assertEquals("📍", QuataFeedEmoji.Location)
        assertEquals("📝", QuataFeedEmoji.Note)
        assertEquals("📄", QuataFeedEmoji.Document)
        assertEquals("Q̈", QuataHeaderLogoGlyph)
    }
}
