package com.quata.feature.notifications.presentation

import com.quata.feature.notifications.domain.NotificationsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Lifecycle-owned subscription to the shared notification count.
 *
 * Platform hosts supply their own scope so delivery can stay on the platform UI thread while
 * the count itself continues to be derived exclusively by [NotificationsRepository].
 */
internal class NotificationCountSubscription(
    private val repository: NotificationsRepository,
    private val scope: CoroutineScope,
) {
    private var observation: Job? = null
    private var generation = 0L

    fun start(onCountChanged: (Int) -> Unit) {
        val activeGeneration = ++generation
        observation?.cancel()
        observation = scope.launch {
            repository.observeNotificationCount().collect { count ->
                // Cancellation does not retract a value that was already dispatched by some
                // Flow implementations. A generation check makes close/restart a hard delivery
                // boundary for every platform, including the Wasm event loop.
                if (activeGeneration == generation) onCountChanged(count)
            }
        }
    }

    fun close() {
        generation++
        observation?.cancel()
        observation = null
    }
}
