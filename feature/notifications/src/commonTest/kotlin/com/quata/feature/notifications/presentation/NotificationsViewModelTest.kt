package com.quata.feature.notifications.presentation

import com.quata.core.common.AppDispatchers
import com.quata.core.model.NotificationItem
import com.quata.feature.notifications.domain.NotificationsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {
    @Test
    fun `hung first notification emission leaves loading with explicit timeout error`() = runTest {
        val viewModel = NotificationsViewModel(
            repository = HangingNotificationsRepository(),
            dispatchers = AppDispatchers(default = StandardTestDispatcher(testScheduler)),
            initialLoadTimeoutMillis = 100,
        )

        advanceTimeBy(100)
        runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("notifications_initial_load_timeout", viewModel.uiState.value.error)
        viewModel.close()
    }

    @Test
    fun `retry replaces a hung collection and publishes the next honest value`() = runTest {
        val repository = RetryNotificationsRepository()
        val viewModel = NotificationsViewModel(
            repository = repository,
            dispatchers = AppDispatchers(default = StandardTestDispatcher(testScheduler)),
            initialLoadTimeoutMillis = 100,
        )
        advanceTimeBy(100)
        runCurrent()

        viewModel.retry()
        runCurrent()

        assertEquals(2, repository.subscriptions)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(emptyList(), viewModel.uiState.value.items)
        assertEquals(null, viewModel.uiState.value.error)
        viewModel.close()
    }

    @Test
    fun `close cancels a hung notification collection`() = runTest {
        val repository = HangingNotificationsRepository()
        val viewModel = NotificationsViewModel(
            repository = repository,
            dispatchers = AppDispatchers(default = StandardTestDispatcher(testScheduler)),
            initialLoadTimeoutMillis = 1_000,
        )
        runCurrent()

        viewModel.close()
        runCurrent()

        assertTrue(repository.cancelled.isCompleted)
    }

    private class HangingNotificationsRepository : BaseNotificationsRepository() {
        val cancelled = CompletableDeferred<Unit>()

        override fun observeNotifications(): Flow<List<NotificationItem>> = flow {
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }
    }

    private class RetryNotificationsRepository : BaseNotificationsRepository() {
        var subscriptions = 0

        override fun observeNotifications(): Flow<List<NotificationItem>> = flow {
            subscriptions += 1
            if (subscriptions == 1) awaitCancellation() else emit(emptyList())
        }
    }

    private abstract class BaseNotificationsRepository : NotificationsRepository {
        override suspend fun getNotifications(): Result<List<NotificationItem>> = Result.success(emptyList())
        override suspend fun getNotificationCount(): Result<Int> = Result.success(0)
        override fun observeNotificationCount(): Flow<Int> = flow { emit(0) }
        override suspend fun markNotificationRead(notification: NotificationItem): Result<Unit> = Result.success(Unit)
        override suspend fun dismissNotification(notification: NotificationItem): Result<Unit> = Result.success(Unit)
    }
}
