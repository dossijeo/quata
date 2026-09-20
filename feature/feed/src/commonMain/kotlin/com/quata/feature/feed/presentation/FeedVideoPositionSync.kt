package com.quata.feature.feed.presentation

import kotlin.math.abs

internal const val FeedVideoPositionSyncToleranceMs = 250L

/**
 * Decides whether a renderer that is becoming active must adopt the position owned by Feed.
 * Active renderers keep publishing their own progress; only an activation boundary transfers
 * ownership from the renderer that was visible previously.
 */
fun shouldSynchronizeFeedVideoPosition(
    currentPositionMs: Long,
    sharedPositionMs: Long,
    isBecomingActive: Boolean,
): Boolean = isBecomingActive &&
    sharedPositionMs >= 0L &&
    abs(currentPositionMs.coerceAtLeast(0L) - sharedPositionMs) > FeedVideoPositionSyncToleranceMs
