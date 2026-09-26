package com.quata.feature.neighborhoods.data

import java.util.concurrent.atomic.AtomicBoolean

/** One-shot, process-local fault used only by the authenticated Android evidence gate. */
object ProfileRolesEvidenceFaults {
    private val failNextMutation = AtomicBoolean(false)

    fun requestFailureOnce() {
        failNextMutation.set(true)
    }

    internal fun consumeFailure(): Boolean = failNextMutation.compareAndSet(true, false)
}
