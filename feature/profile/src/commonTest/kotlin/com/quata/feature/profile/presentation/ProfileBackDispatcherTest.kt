package com.quata.feature.profile.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileBackDispatcherTest {
    @Test
    fun `portable dispatcher invokes the current common page handler`() {
        var calls = 0
        val dispatcher = ProfileBackDispatcher()
        dispatcher.setHandler { calls++ }
        dispatcher.dispatch()
        dispatcher.clearHandler()
        dispatcher.dispatch()
        assertEquals(1, calls)
    }
}
