@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.quata.feature.neighborhoods.presentation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException

class IosNeighborhoodAvatarContractTest {
    @Test fun `remote avatar loader accepts only network urls`() {
        assertTrue(isIosNeighborhoodAvatarUrl("https://cdn.quata.app/avatar.jpg"))
        assertTrue(isIosNeighborhoodAvatarUrl("http://localhost/avatar.png"))
        assertFalse(isIosNeighborhoodAvatarUrl("file:///private/avatar.png"))
    }

    @Test fun `avatar request key changes cancel the previous LaunchedEffect request`() {
        assertEquals("https://cdn.quata.app/a.png", iosNeighborhoodAvatarRequestKey(" https://cdn.quata.app/a.png "))
        assertEquals(null, iosNeighborhoodAvatarRequestKey("file:///tmp/a.png"))
    }

    @Test fun `cancelling avatar load cancels the underlying task and rethrows cancellation`() = runTest {
        var taskCancelled = false
        val load = async {
            iosNeighborhoodAvatarResultOrNull<Unit> {
                suspendCancellableCoroutine { continuation ->
                    registerIosNeighborhoodAvatarTaskCancellation(continuation) { taskCancelled = true }
                }
            }
        }
        runCurrent()
        load.cancel()
        assertFailsWith<CancellationException> { load.await() }
        assertTrue(taskCancelled)
    }
}
