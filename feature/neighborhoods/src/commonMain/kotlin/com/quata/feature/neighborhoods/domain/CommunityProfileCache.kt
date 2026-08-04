package com.quata.feature.neighborhoods.domain

/** Null means any cached age is accepted; a bounded request must never receive stale data. */
fun isCommunityProfileCacheUsable(
    cachedAtMillis: Long,
    nowMillis: Long,
    maxAgeMillis: Long?,
): Boolean = maxAgeMillis == null ||
    (nowMillis - cachedAtMillis).coerceAtLeast(0L) <= maxAgeMillis.coerceAtLeast(0L)
