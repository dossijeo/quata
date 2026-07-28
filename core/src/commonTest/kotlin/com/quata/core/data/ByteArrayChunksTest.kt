package com.quata.core.data

import kotlin.test.Test
import kotlin.test.assertContentEquals

class ByteArrayChunksTest {
    @Test
    fun mergePreservesChunkOrderIncludingEmptyCallbacks() {
        val merged = listOf(
            byteArrayOf(1, 2),
            ByteArray(0),
            byteArrayOf(3),
            byteArrayOf(4, 5),
        ).mergeByteArrayChunks()

        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), merged)
    }

    @Test
    fun mergeOfNoBytesIsEmpty() {
        assertContentEquals(ByteArray(0), listOf(ByteArray(0), ByteArray(0)).mergeByteArrayChunks())
    }
}
