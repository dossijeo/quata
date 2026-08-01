package com.quata.feature.chat.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosChatNetworkTimeoutsTest {
    @Test
    fun `attachment upload does not inherit the fifteen second rpc timeout`() {
        assertEquals(15_000L, IosChatNetworkTimeouts.RpcRequestMillis)
        assertTrue(IosChatNetworkTimeouts.AttachmentUploadMillis > IosChatNetworkTimeouts.RpcRequestMillis)
        assertEquals(120_000L, IosChatNetworkTimeouts.AttachmentUploadMillis)
    }
}
