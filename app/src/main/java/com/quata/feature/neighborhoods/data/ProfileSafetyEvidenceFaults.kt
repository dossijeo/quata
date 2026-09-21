package com.quata.feature.neighborhoods.data

import java.util.concurrent.atomic.AtomicBoolean

/** One-shot, process-local fault used only by the authenticated Android evidence gate. */
object ProfileSafetyEvidenceFaults {
    private val failNextBlockMutation = AtomicBoolean(false)

    fun requestBlockFailureOnce() {
        failNextBlockMutation.set(true)
    }

    internal fun consumeBlockFailure(): Boolean = failNextBlockMutation.compareAndSet(true, false)
}
