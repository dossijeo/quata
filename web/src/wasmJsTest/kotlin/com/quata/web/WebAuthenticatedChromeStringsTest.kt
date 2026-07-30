package com.quata.web

import kotlin.test.Test
import kotlin.test.assertEquals

class WebAuthenticatedChromeStringsTest {
    @Test
    fun `web chrome reuses Android UTF-8 fallback copy`() {
        assertEquals("Avisos", WebAuthenticatedChromeStrings.notifications)
        assertEquals("Sin conexi\u00f3n", WebAuthenticatedChromeStrings.offline)
        assertEquals("SOS \ud83d\udea8", WebAuthenticatedChromeStrings.sos)
    }
}
