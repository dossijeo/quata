package com.quata.feature.neighborhoods.domain

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Emits immediately, on each Realtime invalidation, and periodically as a bounded
 * recovery path for public sessions, socket loss, or an event that races a paged snapshot.
 */
fun neighborhoodDirectoryRefreshSignals(
    realtimeChanges: Flow<Unit>,
    fallbackIntervalMillis: Long,
): Flow<Unit> {
    require(fallbackIntervalMillis > 0L) { "neighborhood_directory_refresh_interval_invalid" }
    return channelFlow {
        send(Unit)
        launch { realtimeChanges.collect { send(Unit) } }
        launch {
            while (currentCoroutineContext().isActive) {
                delay(fallbackIntervalMillis)
                send(Unit)
            }
        }
    }.conflate()
}
