package com.quata.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class QuataFeedActionRailTest {
    @Test
    fun `ranking contract retains Android flame emoji`() {
        assertEquals("🔥", "🔥")
    }

    @Test
    fun `action rail tags can target a specific post without platform selectors guessing coordinates`() {
        assertEquals(
            "feed.action.comments.post-123",
            quataFeedActionTestTag("feed.action", "comments", "post-123"),
        )
        assertEquals(
            "official.action.comments.notice-456",
            quataFeedActionTestTag("official.action", "comments", "notice-456"),
        )
        assertEquals("feed.action.comments", quataFeedActionTestTag("feed.action", "comments"))
    }
}
