package com.quata.core.ui.components

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class QuataFeedActionRailTest {
    @Test
    fun `ranking flame vector keeps the published orange color`() {
        assertEquals(Color(0xFFFF8A00), FeedRankFlameColor)
    }
}
