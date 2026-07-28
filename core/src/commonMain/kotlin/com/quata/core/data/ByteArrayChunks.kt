package com.quata.core.data

/**
 * Returns a single byte array containing every chunk in iteration order.
 *
 * HTTP delegates receive arbitrary-sized data callbacks, including empty callbacks. Keeping the
 * merge outside the Foundation boundary makes that behaviour testable on every Kotlin target.
 */
fun Iterable<ByteArray>.mergeByteArrayChunks(): ByteArray {
    val chunks = toList()
    var totalSize = 0
    for (chunk in chunks) {
        require(chunk.size <= Int.MAX_VALUE - totalSize) { "byte_array_chunks_too_large" }
        totalSize += chunk.size
    }
    if (totalSize == 0) return ByteArray(0)

    val merged = ByteArray(totalSize)
    var offset = 0
    for (chunk in chunks) {
        chunk.copyInto(merged, destinationOffset = offset)
        offset += chunk.size
    }
    return merged
}
