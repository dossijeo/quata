package com.quata.feature.neighborhoods.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommunityProfileCacheTest {
    @Test
    fun boundedReadsRejectStaleProfilesAndAcceptTheBoundary() {
        assertTrue(isCommunityProfileCacheUsable(cachedAtMillis = 1_000L, nowMillis = 6_000L, maxAgeMillis = 5_000L))
        assertFalse(isCommunityProfileCacheUsable(cachedAtMillis = 1_000L, nowMillis = 6_001L, maxAgeMillis = 5_000L))
    }

    @Test
    fun unboundedReadsAndClockRollbackRemainUsable() {
        assertTrue(isCommunityProfileCacheUsable(cachedAtMillis = 1_000L, nowMillis = Long.MAX_VALUE, maxAgeMillis = null))
        assertTrue(isCommunityProfileCacheUsable(cachedAtMillis = 2_000L, nowMillis = 1_000L, maxAgeMillis = 0L))
    }
}
