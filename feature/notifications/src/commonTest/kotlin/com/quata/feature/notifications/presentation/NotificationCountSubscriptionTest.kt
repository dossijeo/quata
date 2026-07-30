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
        val subscription = NotificationCountSubscription(repository, this)

        subscription.start(receivedCounts::add)
        testScheduler.runCurrent()
        repository.count.value = 4
        testScheduler.runCurrent()

        subscription.close()
        repository.count.value = 0
        testScheduler.runCurrent()

        assertEquals(listOf(0, 4), receivedCounts)
    }

    @Test
    fun `restarting replaces the previous callback`() = runTest {
        val repository = CountRepository()
        val first = mutableListOf<Int>()
        val second = mutableListOf<Int>()
        val subscription = NotificationCountSubscription(repository, this)

        subscription.start(first::add)
        subscription.start(second::add)
        testScheduler.runCurrent()
        repository.count.value = 4
        testScheduler.runCurrent()

        assertEquals(emptyList(), first)
        assertEquals(listOf(0, 4), second)
        subscription.close()
    }

    @Test
    fun `close invalidates an observer before its queued initial value runs`() = runTest {
        val repository = CountRepository()
        val receivedCounts = mutableListOf<Int>()
        val subscription = NotificationCountSubscription(repository, this)

        subscription.start(receivedCounts::add)
        subscription.close()
        testScheduler.runCurrent()

        assertEquals(emptyList(), receivedCounts)
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
