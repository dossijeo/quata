package com.quata.feature.neighborhoods.presentation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
}
