package com.quata.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentFixtureBytesTest {
    @Test fun recognizesPortableMagicPrefixes() {
        assertEquals("pdf", documentFixtureKind("%PDF-1.4".encodeToByteArray()))
        assertEquals("rtf", documentFixtureKind("{\\rtf1".encodeToByteArray()))
        assertEquals("zip", documentFixtureKind(byteArrayOf(0x50, 0x4b, 0x03, 0x04)))
    }
}
