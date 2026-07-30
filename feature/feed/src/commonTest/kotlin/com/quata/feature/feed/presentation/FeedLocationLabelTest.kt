package com.quata.feature.feed.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class FeedLocationLabelTest {
    @Test
    fun `location label uses the shared red pin prefix`() {
        assertEquals("\uD83D\uDCCD Centro", formatFeedLocationLabel("Centro"))
    }

    @Test
    fun `location label retains Android pin emoji`() {
        assertEquals("📍 Centro", formatFeedLocationLabel("Centro"))
    }

    @Test
    fun `note and document contracts retain Android emoji`() {
        assertEquals("📝 Nota", "📝 Nota")
        assertEquals("📄 Archivo", "📄 Archivo")
    }
}
