package com.quata.feature.feed.presentation

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class ReelOverlayLayoutContractTest {
    @Test
    fun `top chips retain the shared Android reference offset`() {
        assertEquals(68.dp, ReelOverlayLayoutContract.topChipsOffset)
    }
}
