package com.quata.feature.chat.data

import kotlin.test.Test
import kotlin.test.assertEquals

class TemporaryPreviewLeaseTest {
    @Test
    fun discardsItsTemporaryFileExactlyOnce() {
        var discards = 0
        val lease = TemporaryPreviewLease { discards += 1 }

        assertEquals(0, discards)
        lease.release()
        lease.release()

        assertEquals(1, discards)
    }
}
