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

    @Test
    fun `overview does not consume platform back while nested pages do`() {
        val dispatcher = ProfileBackDispatcher()
        dispatcher.setHandler(null)
        assertEquals(false, dispatcher.canConsume)
        dispatcher.setHandler { }
        assertEquals(true, dispatcher.canConsume)
        dispatcher.clearHandler()
        assertEquals(false, dispatcher.canConsume)
    }
}
