package com.quata.feature.feed.presentation

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals

class TextOnlyReelScrollAnchorTest {
    @Test
    fun matchingPostRestoresTheExactDeepOffset() {
        assertEquals(
            431,
            textOnlyReelReaderInitialOffset(
                stableId = "feed-post-7",
                anchor = TextOnlyReelReaderScrollAnchor("feed-post-7", 431),
            ),
        )
    }

    @Test
    fun anotherPostCannotInheritThePreviousReaderOffset() {
        assertEquals(
            0,
            textOnlyReelReaderInitialOffset(
                stableId = "feed-post-8",
                anchor = TextOnlyReelReaderScrollAnchor("feed-post-7", 431),
            ),
        )
    }

    @Test
    fun restoredOffsetIsNeverNegative() {
        assertEquals(
            0,
            textOnlyReelReaderInitialOffset(
                stableId = "feed-post-7",
                anchor = TextOnlyReelReaderScrollAnchor("feed-post-7", -1),
            ),
        )
    }

    @Test
    fun saverRoundTripKeepsPostAndExactPixelOffset() {
        val anchor = TextOnlyReelReaderScrollAnchor("feed-post-7", 431)
        val saved = with(TextOnlyReelReaderScrollAnchor.Saver) {
            SaverScope { true }.save(anchor)
        }
        assertEquals(anchor, saved?.let(TextOnlyReelReaderScrollAnchor.Saver::restore))
    }
}
