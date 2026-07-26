package com.quata.feature.chat.data

/**
 * Owns one downloaded preview file until the platform viewer is dismissed or opening fails.
 * Release is idempotent because UIKit dismissal and cancellation can race at lifecycle edges.
 */
internal class TemporaryPreviewLease(
    private val discard: () -> Unit,
) {
    private var released = false

    fun release() {
        if (released) return
        released = true
        discard()
    }
}
