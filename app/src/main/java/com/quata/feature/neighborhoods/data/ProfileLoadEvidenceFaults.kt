package com.quata.feature.neighborhoods.data

import java.util.concurrent.atomic.AtomicBoolean

/** One-shot, process-local fault used only by the authenticated Android profile evidence gate. */
object ProfileLoadEvidenceFaults {
    private val failNextLoad = AtomicBoolean(false)

    fun requestFailureOnce() {
        failNextLoad.set(true)
    }

    internal fun consumeFailure(): Boolean = failNextLoad.compareAndSet(true, false)
}
