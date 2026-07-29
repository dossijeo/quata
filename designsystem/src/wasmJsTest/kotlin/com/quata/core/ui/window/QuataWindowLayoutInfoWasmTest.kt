package com.quata.core.ui.window

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuataWindowLayoutInfoWasmTest {
    @Test
    fun landscapeViewportIsReportedAsLandscape() {
        val layout = wasmViewportLayoutInfo(932, 430)
        assertTrue(layout.isLandscape)
        assertEquals("wasm:932x430", layout.viewportKey)
    }

    @Test
    fun portraitViewportIsNotLandscape() {
        val layout = wasmViewportLayoutInfo(430, 932)
        assertFalse(layout.isLandscape)
    }
}
