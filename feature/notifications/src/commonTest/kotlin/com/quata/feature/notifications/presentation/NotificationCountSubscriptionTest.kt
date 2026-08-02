package com.quata.feature.notifications.presentation

import com.quata.core.model.NotificationItem
import com.quata.feature.notifications.domain.NotificationsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
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

    @Test
    fun `failure resubscribes and close cancels recovery`() = runTest {
        val repository = FailingThenRecoveredCountRepository()
        val received = mutableListOf<Int>()
        val subscription = NotificationCountSubscription(repository, this)

        subscription.start(received::add)
        testScheduler.runCurrent()
        advanceTimeBy(1_000)
        testScheduler.runCurrent()
        assertEquals(listOf(9), received)

        subscription.close()
        advanceTimeBy(1_000)
        testScheduler.runCurrent()
        assertEquals(listOf(9), received)
    }

    @Test
    fun `close during the first retry delay prevents a permanently failing source from resubscribing`() = runTest {
        val repository = AlwaysFailingCountRepository()
        val subscription = NotificationCountSubscription(repository, this)

        subscription.start { }
        testScheduler.runCurrent()
        assertEquals(1, repository.attempts)

        subscription.close()
        advanceTimeBy(60_000)
        testScheduler.runCurrent()

        assertEquals(1, repository.attempts)
    }

    private open class CountRepository : NotificationsRepository {
        val count = MutableStateFlow(0)

        override suspend fun getNotifications(): Result<List<NotificationItem>> = Result.success(emptyList())
        override suspend fun getNotificationCount(): Result<Int> = Result.success(count.value)
        override fun observeNotifications(): Flow<List<NotificationItem>> = emptyFlow()
        override fun observeNotificationCount(): Flow<Int> = count
        override suspend fun markNotificationRead(notification: NotificationItem): Result<Unit> = Result.success(Unit)
        override suspend fun dismissNotification(notification: NotificationItem): Result<Unit> = Result.success(Unit)
    }

    private class FailingThenRecoveredCountRepository : CountRepository() {
        var attempts = 0
        override fun observeNotificationCount(): Flow<Int> = flow {
            attempts += 1
            if (attempts == 1) error("count_transport_failed")
            emit(9)
        }
    }

    private class AlwaysFailingCountRepository : CountRepository() {
        var attempts = 0
        override fun observeNotificationCount(): Flow<Int> = flow {
            attempts += 1
            error("count_transport_failed")
        }
    }
}
