package com.quata.core.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes

/**
 * Copies Kotlin-owned bytes into Foundation-owned [NSData].
 *
 * Foundation's `dataWithBytes:length:` factory owns a copy, so the result remains valid after the
 * pinned Kotlin array is released. This deliberately does not bridge a CoreFoundation pointer as
 * an Objective-C object: Kotlin/Native represents that return value as a C pointer on some Apple
 * targets.
 */
@OptIn(ExperimentalForeignApi::class)
fun ByteArray.toFoundationData(): NSData =
    if (isEmpty()) NSData()
    else usePinned { pinned -> NSData.dataWithBytes(pinned.addressOf(0), length = size.toULong()) }

/** Copies all callback chunks into Foundation-owned [NSData], including the empty case. */
fun Iterable<ByteArray>.toFoundationData(): NSData = mergeByteArrayChunks().toFoundationData()
