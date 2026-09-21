package com.quata.feature.chat.data

import java.util.concurrent.atomic.AtomicBoolean

/** Debug-only one-shot fault used to prove mute rollback through the real Android UI. */
object ChatMuteEvidenceFaults {
    private val failNextMutation = AtomicBoolean(false)

    fun requestFailureOnce() {
        failNextMutation.set(true)
    }

    fun consumeFailure(): Boolean = failNextMutation.compareAndSet(true, false)
}
