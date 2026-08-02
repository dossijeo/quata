package com.quata.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class BrowserClockInteropTest {
    @Test
    fun `Date now JavaScript Numbers cross into Kotlin Long clocks`() {
        assertTrue(notificationsBrowserNowMillis() > 0L)
        assertTrue(currentBrowserTimeMillis() > 0L)
    }

    @Test
    fun `notifications activity subtitle remains UTF-8 Spanish copy`() {
        assertEquals("Notificaciones push y actividad", webNotificationsActivitySubtitle)
    }

    @Test
    fun `notification badge preserves collect state during an error then recovers`() = runTest {
        var attempts = 0
        val source = flow {
            attempts += 1
            if (attempts == 1) {
                emit(4)
                error("transport_unavailable")
            }
            emit(7)
        }

        assertEquals(listOf(4, 7), webChromeNotificationCount(source, retryDelayMillis = 1).take(2).toList())
    }
}
