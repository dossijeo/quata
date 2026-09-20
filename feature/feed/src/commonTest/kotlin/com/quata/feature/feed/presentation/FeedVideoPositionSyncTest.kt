package com.quata.feature.feed.presentation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedVideoPositionSyncTest {
    @Test
    fun inactiveRendererAdoptsSharedPositionOnlyWhenItBecomesActive() {
        assertFalse(shouldSynchronizeFeedVideoPosition(400L, 1_600L, isBecomingActive = false))
        assertTrue(shouldSynchronizeFeedVideoPosition(400L, 1_600L, isBecomingActive = true))
    }

    @Test
    fun activationIgnoresZeroAndSmallClockDrift() {
        assertFalse(shouldSynchronizeFeedVideoPosition(900L, 0L, isBecomingActive = true))
        assertFalse(shouldSynchronizeFeedVideoPosition(900L, 1_100L, isBecomingActive = true))
        assertTrue(shouldSynchronizeFeedVideoPosition(900L, 1_200L, isBecomingActive = true))
    }
}
