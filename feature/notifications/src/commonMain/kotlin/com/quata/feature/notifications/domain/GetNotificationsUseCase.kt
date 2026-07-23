package com.quata.feature.notifications.domain

class GetNotificationsUseCase(private val repository: NotificationsRepository) {
    suspend operator fun invoke() = repository.getNotifications()
}
