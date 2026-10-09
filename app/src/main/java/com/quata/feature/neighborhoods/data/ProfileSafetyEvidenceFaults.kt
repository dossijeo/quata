package com.quata.feature.neighborhoods.data

import java.util.concurrent.atomic.AtomicBoolean

/** One-shot, process-local fault used only by the authenticated Android evidence gate. */
object ProfileSafetyEvidenceFaults {
    private val failNextReportMutation = AtomicBoolean(false)
    private val failNextBlockMutation = AtomicBoolean(false)

    fun requestReportFailureOnce() {
        failNextReportMutation.set(true)
    }

    fun requestBlockFailureOnce() {
        failNextBlockMutation.set(true)
    }

    internal fun consumeReportFailure(): Boolean = failNextReportMutation.compareAndSet(true, false)

    internal fun consumeBlockFailure(): Boolean = failNextBlockMutation.compareAndSet(true, false)
}
