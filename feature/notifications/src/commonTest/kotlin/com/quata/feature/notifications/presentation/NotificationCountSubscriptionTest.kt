package com.quata.feature.notifications.presentation

import com.quata.core.model.NotificationItem
import com.quata.feature.notifications.domain.NotificationsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationCountSubscriptionTest {
    @Test
    fun `forwards repository count and stops after close`() = runTest {
        val repository = CountRepository()
        val receivedCounts = mutableListOf<Int>()
        val subscription = NotificationCountSubscription(repository, backgroundScope)

        subscription.start(receivedCounts::add)
        testScheduler.advanceUntilIdle()
        repository.count.value = 4
        testScheduler.advanceUntilIdle()

        subscription.close()
        repository.count.value = 0
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(0, 4), receivedCounts)
    }

    @Test
    fun `restarting replaces the previous callback`() = runTest {
        val repository = CountRepository()
        val first = mutableListOf<Int>()
        val second = mutableListOf<Int>()
        val subscription = NotificationCountSubscription(repository, backgroundScope)

        subscription.start(first::add)
        testScheduler.advanceUntilIdle()
        subscription.start(second::add)
        testScheduler.advanceUntilIdle()
        repository.count.value = 4
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(0), first)
        assertEquals(listOf(0, 4), second)
    }

    private class CountRepository : NotificationsRepository {
        val count = MutableStateFlow(0)

        override suspend fun getNotifications(): Result<List<NotificationItem>> = Result.success(emptyList())
        override suspend fun getNotificationCount(): Result<Int> = Result.success(count.value)
        override fun observeNotifications(): Flow<List<NotificationItem>> = emptyFlow()
        override fun observeNotificationCount(): Flow<Int> = count
        override suspend fun markNotificationRead(notification: NotificationItem): Result<Unit> = Result.success(Unit)
        override suspend fun dismissNotification(notification: NotificationItem): Result<Unit> = Result.success(Unit)
    }
}
