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

    fun start(onCountChanged: (Int) -> Unit) {
        observation?.cancel()
        observation = scope.launch {
            repository.observeNotificationCount().collect(onCountChanged)
        }
    }

    fun close() {
        observation?.cancel()
        observation = null
    }
}
