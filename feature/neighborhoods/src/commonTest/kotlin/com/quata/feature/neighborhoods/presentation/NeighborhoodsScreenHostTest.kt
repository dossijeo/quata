package com.quata.feature.neighborhoods.presentation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NeighborhoodsScreenHostTest {
    @Test
    fun `directory remains public while private community actions need an identity`() {
        assertFalse(canPerformNeighborhoodPrivateAction(null))
        assertFalse(canPerformNeighborhoodPrivateAction("   "))
        assertTrue(canPerformNeighborhoodPrivateAction("profile-42"))
    }
}
