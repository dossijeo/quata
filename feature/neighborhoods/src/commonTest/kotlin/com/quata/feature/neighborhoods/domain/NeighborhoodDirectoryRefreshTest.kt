package com.quata.feature.neighborhoods.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NeighborhoodDirectoryRefreshTest {
    @Test
    fun realtimeWakeupAndFallbackBothRefreshAfterTheImmediateSnapshot() = runTest {
        val realtime = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val collected = async {
            neighborhoodDirectoryRefreshSignals(realtime, fallbackIntervalMillis = 1_000L)
                .take(3)
                .toList()
        }

        runCurrent()
        realtime.emit(Unit)
        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(3, collected.await().size)
    }
}
