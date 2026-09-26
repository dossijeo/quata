package com.quata.feature.neighborhoods.data

import java.util.concurrent.atomic.AtomicBoolean

/** One-shot, process-local fault used only by the authenticated Android evidence gate. */
object ProfilePrivateChatEvidenceFaults {
    private val failNextOpen = AtomicBoolean(false)

    fun requestFailureOnce() {
        failNextOpen.set(true)
    }

    internal fun consumeFailure(): Boolean = failNextOpen.compareAndSet(true, false)
}
