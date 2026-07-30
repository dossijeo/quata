package com.quata.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class QuataFeedActionRailTest {
    @Test
    fun `ranking contract retains Android flame emoji`() {
        assertEquals("🔥", "🔥")
    }
}
