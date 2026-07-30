package com.quata.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import androidx.compose.ui.graphics.Color

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

    @Test fun `portable SOS siren preserves the published multicolor palette`() {
        assertEquals(Color(0xFFFFD54F), SosSirenRayColor)
        assertEquals(Color(0xFFFF5A5F), SosSirenDomeColor)
        assertEquals(Color(0xFF2563EB), SosSirenBaseColor)
    }
}
