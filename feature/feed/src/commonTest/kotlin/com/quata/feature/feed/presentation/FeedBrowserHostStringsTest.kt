package com.quata.feature.feed.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class FeedBrowserHostStringsTest {
    @Test
    fun detailLoadingKeepsTheEllipsisGlyph() {
        assertEquals("Loading post…", FeedBrowserHostStrings().detailLoading)
    }
}
