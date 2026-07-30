package com.quata.core.ui.window

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuataWindowLayoutInfoTest {
    @Test
    fun portraitMeasurementPreservesViewportAndOrientation() {
        val layout = measuredQuataWindowLayoutInfo("ios", widthPx = 402, heightPx = 874)

        assertEquals(402, layout.widthPx)
        assertEquals(874, layout.heightPx)
        assertFalse(layout.isLandscape)
        assertEquals("ios:402x874", layout.viewportKey)
    }

    @Test
    fun landscapeMeasurementPreservesViewportAndOrientation() {
        val layout = measuredQuataWindowLayoutInfo("ios", widthPx = 874, heightPx = 402)

        assertEquals(874, layout.widthPx)
        assertEquals(402, layout.heightPx)
        assertTrue(layout.isLandscape)
        assertEquals("ios:874x402", layout.viewportKey)
    }

    @Test
    fun unmeasuredViewportIsNotReportedAsLandscape() {
        val layout = measuredQuataWindowLayoutInfo("ios", widthPx = -1, heightPx = -1)

        assertEquals(0, layout.widthPx)
        assertEquals(0, layout.heightPx)
        assertFalse(layout.isLandscape)
        assertEquals("ios:0x0", layout.viewportKey)
    }
}
