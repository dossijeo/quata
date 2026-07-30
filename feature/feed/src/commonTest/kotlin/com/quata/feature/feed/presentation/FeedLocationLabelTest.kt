package com.quata.feature.feed.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class FeedLocationLabelTest {
    @Test
    fun `location label uses the shared red pin prefix`() {
        assertEquals("\uD83D\uDCCD Centro", formatFeedLocationLabel("Centro"))
    }
}
