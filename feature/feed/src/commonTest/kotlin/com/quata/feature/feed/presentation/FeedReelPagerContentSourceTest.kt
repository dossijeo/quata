package com.quata.feature.feed.presentation

import kotlin.test.Test
import kotlin.test.assertFalse

class FeedReelPagerContentSourceTest {
    @Test
    fun emptyFeedIsGuardedBeforeThePagerCanReadPageZero() {
        assertFalse(canRenderFeedPager(emptyList()))
    }
}
