package com.quata.feature.feed.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class FeedBrowserHostStringsTest {
    @Test
    fun detailLoadingKeepsTheEllipsisGlyph() {
        val strings = FeedBrowserHostStrings(
            loading = "loading",
            retry = "retry",
            loadFailure = "failure",
            refresh = "refresh",
            refreshing = "refreshing",
            conversations = "conversations",
            loadingOlder = "older",
            loadOlder = "load older",
            noText = "no text",
            readMore = "read more",
            close = "close",
            empty = "empty",
            mediaUnavailable = "media unavailable",
        )

        assertEquals("Loading post…", strings.detailLoading)
    }
}
