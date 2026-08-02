package com.quata.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
}
