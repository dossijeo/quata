package com.quata.feature.feed.presentation

import com.quata.core.common.AppDispatchers
import com.quata.core.model.Post
import com.quata.core.model.User
import com.quata.feature.feed.domain.FeedReadRepository
import com.quata.feature.feed.domain.ReadOnlyFeedRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FeedFocusedPostLoadTest {
    @Test
    fun immediateRetryFromTerminalObserverKeepsItsReservation() = runTest {
        val repository = FocusRepository()
        val pendingRetry = CompletableDeferred<Result<Post?>>()
        repository.result = { if (repository.reads == 1) Result.success(null) else pendingRetry.await() }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val model = FeedViewModel(ReadOnlyFeedRepository(repository), AppDispatchers(dispatcher, dispatcher, dispatcher))
        var retried = false
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.uiState.collect { state ->
                if (!retried && state.focusedPostLoads["target"] == FeedFocusedPostLoad.NotFound) {
                    retried = true
                    model.onEvent(FeedUiEvent.FocusPost("target"))
                }
            }
        }
        try {
            advanceUntilIdle()
            model.onEvent(FeedUiEvent.FocusPost("target"))
            runCurrent()
            assertTrue(retried)
            assertEquals(FeedFocusedPostLoad.Loading, model.uiState.value.focusedPostLoads["target"])
            model.onEvent(FeedUiEvent.FocusPost("target"))
            runCurrent()
            assertEquals(2, repository.reads)
            pendingRetry.complete(Result.success(null))
            advanceUntilIdle()
            assertEquals(FeedFocusedPostLoad.NotFound, model.uiState.value.focusedPostLoads["target"])
        } finally { observer.cancel(); model.close() }
    }

    @Test
    fun missingTargetIsTerminalAndRetryCanFindItWithoutLosingFeed() = runTest {
        val repository = FocusRepository()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val model = FeedViewModel(ReadOnlyFeedRepository(repository), AppDispatchers(dispatcher, dispatcher, dispatcher))
        try {
            advanceUntilIdle()
            model.onEvent(FeedUiEvent.FocusPost("target"))
            advanceUntilIdle()
            assertEquals(FeedFocusedPostLoad.NotFound, model.uiState.value.focusedPostLoads["target"])
            assertEquals(listOf("sibling"), model.uiState.value.posts.map { it.id })
            assertNull(model.uiState.value.error)
            assertEquals(1, repository.reads)
            repository.result = { Result.success(focusPost("target")) }
            model.onEvent(FeedUiEvent.FocusPost("target"))
            advanceUntilIdle()
            assertEquals(FeedFocusedPostLoad.Loaded, model.uiState.value.focusedPostLoads["target"])
            assertEquals(setOf("target", "sibling"), model.uiState.value.posts.map { it.id }.toSet())
            assertEquals(2, repository.reads)
        } finally { model.close() }
    }

    @Test
    fun duplicateEventsShareThePendingRead() = runTest {
        val repository = FocusRepository()
        val pending = CompletableDeferred<Result<Post?>>()
        repository.result = { pending.await() }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val model = FeedViewModel(ReadOnlyFeedRepository(repository), AppDispatchers(dispatcher, dispatcher, dispatcher))
        try {
            advanceUntilIdle()
            repeat(3) { model.onEvent(FeedUiEvent.FocusPost("target")) }
            runCurrent()
            assertEquals(1, repository.reads)
            assertEquals(FeedFocusedPostLoad.Loading, model.uiState.value.focusedPostLoads["target"])
            pending.complete(Result.success(null))
            advanceUntilIdle()
            assertEquals(FeedFocusedPostLoad.NotFound, model.uiState.value.focusedPostLoads["target"])
        } finally { model.close() }
    }

    @Test
    fun returnedAndThrownFailuresRemainRetryable() = runTest {
        val repository = FocusRepository()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val model = FeedViewModel(ReadOnlyFeedRepository(repository), AppDispatchers(dispatcher, dispatcher, dispatcher))
        try {
            advanceUntilIdle()
            for (throws in listOf(false, true)) {
                repository.result = { if (throws) error("transport") else Result.failure(IllegalStateException("transport")) }
                model.onEvent(FeedUiEvent.FocusPost("target"))
                advanceUntilIdle()
                assertEquals(FeedFocusedPostLoad.Failed, model.uiState.value.focusedPostLoads["target"])
            }
            repository.result = { Result.success(null) }
            model.onEvent(FeedUiEvent.FocusPost("target"))
            advanceUntilIdle()
            assertEquals(FeedFocusedPostLoad.NotFound, model.uiState.value.focusedPostLoads["target"])
            assertEquals(3, repository.reads)
        } finally { model.close() }
    }

    @Test
    fun cancellationReleasesReservationWithoutReportingFailure() = runTest {
        val repository = FocusRepository()
        repository.result = { throw CancellationException("cancelled read") }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val model = FeedViewModel(ReadOnlyFeedRepository(repository), AppDispatchers(dispatcher, dispatcher, dispatcher))
        try {
            advanceUntilIdle()
            model.onEvent(FeedUiEvent.FocusPost("target"))
            advanceUntilIdle()
            assertNull(model.uiState.value.focusedPostLoads["target"])
            repository.result = { Result.success(null) }
            model.onEvent(FeedUiEvent.FocusPost("target"))
            advanceUntilIdle()
            assertEquals(2, repository.reads)
            assertEquals(FeedFocusedPostLoad.NotFound, model.uiState.value.focusedPostLoads["target"])
        } finally { model.close() }
    }

    @Test
    fun wrongDestinationCannotBecomeAResolvedTarget() = runTest {
        val repository = FocusRepository()
        repository.result = { Result.success(focusPost("wrong")) }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val model = FeedViewModel(ReadOnlyFeedRepository(repository), AppDispatchers(dispatcher, dispatcher, dispatcher))
        try {
            advanceUntilIdle()
            model.onEvent(FeedUiEvent.FocusPost("target"))
            advanceUntilIdle()
            assertEquals(FeedFocusedPostLoad.Failed, model.uiState.value.focusedPostLoads["target"])
            assertTrue(model.uiState.value.posts.none { it.id == "wrong" })
        } finally { model.close() }
    }
}

private fun focusPost(id: String) = Post(id, User("author", "fixture@example.invalid", "Fixture"), "Local fixture", createdAt = "2026-09-10")

private class FocusRepository : FeedReadRepository {
    var reads = 0
    var result: suspend () -> Result<Post?> = { Result.success(null) }
    private val posts = listOf(focusPost("sibling"))
    override fun observeFeed() = flowOf(Result.success(posts))
    override suspend fun getFeed() = Result.success(posts)
    override suspend fun refreshFeed() = Result.success(posts)
    override suspend fun loadOlderFeedPage(beforeCreatedAt: String?, limit: Int) = Result.success(emptyList<Post>())
    override suspend fun refreshCurrentUser() = Result.success<User?>(null)
    override suspend fun refreshAuthor(userId: String) = Result.success<User?>(null)
    override suspend fun refreshPost(postId: String): Result<Post?> { reads++; return result() }
}
