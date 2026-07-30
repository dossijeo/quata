package com.quata.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class AuthenticatedChromeCopyContractTest {
    @Test fun `portable chrome copy retains UTF-8 semantic values`() {
        val offline = "Sin conexi\u00f3n"
        val sos = "SOS"
        assertEquals(offline, QuataAuthenticatedChromeSpanish.offline)
        assertEquals(sos, QuataAuthenticatedChromeSpanish.sos)
    }

    @Test fun `legacy SOS siren is never rendered as text`() {
        assertEquals("SOS", sosVisibleLabel("SOS 🚨"))
    }
}
