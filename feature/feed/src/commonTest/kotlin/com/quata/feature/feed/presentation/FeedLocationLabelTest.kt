package com.quata.feature.feed.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import androidx.compose.ui.graphics.Color

class FeedLocationLabelTest {
    @Test
    fun `location label uses the shared red pin prefix`() {
        assertEquals("\uD83D\uDCCD Centro", formatFeedLocationLabel("Centro"))
    }

    @Test
    fun `location pin vector keeps the published red color`() {
        assertEquals(Color(0xFFFF3D3D), FeedLocationPinColor)
    }

    @Test
    fun `note and document vectors retain their established spoken labels`() {
        assertEquals("📝 Nota", "${FeedNoteSemanticPrefix}Nota")
        assertEquals("📄 Archivo", "${FeedDocumentSemanticPrefix}Archivo")
    }
}
