package com.quata.feature.notifications.presentation

import com.quata.feature.notifications.domain.NotificationsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException

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
            var retryDelayMillis = 1_000L
            while (activeGeneration == generation) {
                try {
                    repository.observeNotificationCount().collect { count ->
                        if (activeGeneration == generation) onCountChanged(count)
                    }
                    return@launch
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    delay(retryDelayMillis)
                    retryDelayMillis = (retryDelayMillis * 2L).coerceAtMost(30_000L)
                }
            }
        }
    }

    fun close() {
        generation++
        observation?.cancel()
        observation = null
    }
}
