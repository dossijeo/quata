package com.quata.feature.notifications.presentation

import com.quata.feature.notifications.domain.NotificationsRepository
import kotlinx.coroutines.MainScope

/**
 * Swift-friendly lifecycle bridge for the authenticated chrome badge.
 *
 * Collection runs in [MainScope], so UIKit can safely forward the emitted value into Compose
 * state. The repository remains the single shared source of notification-count logic.
 */
class IosNotificationCountObserver(repository: NotificationsRepository) {
    private val subscription = NotificationCountSubscription(
        repository = repository,
        scope = MainScope(),
    )

    fun start(onCountChanged: (Int) -> Unit) {
        subscription.start(onCountChanged)
    }

    fun close() {
        subscription.close()
    }
}
