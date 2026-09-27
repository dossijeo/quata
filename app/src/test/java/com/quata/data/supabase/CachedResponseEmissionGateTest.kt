package com.quata.data.supabase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedResponseEmissionGateTest {
    @Test
    fun unchangedFirstThousandRowsReemitAfterOffWindowInvalidation() {
        val gate = CachedResponseEmissionGate()
        val firstThousandRows = "rows-0001-through-1000"

        assertTrue(gate.accepts(firstThousandRows))
        gate.markInvalidated(emitUnchangedAfterInvalidation = true)

        assertTrue(gate.accepts(firstThousandRows))
        assertFalse(gate.accepts(firstThousandRows))
    }

    @Test
    fun ordinaryObserversStillSuppressAnUnchangedRefresh() {
        val gate = CachedResponseEmissionGate()
        val body = "same-body"

        assertTrue(gate.accepts(body))
        gate.markInvalidated(emitUnchangedAfterInvalidation = false)

        assertFalse(gate.accepts(body))
    }
}
