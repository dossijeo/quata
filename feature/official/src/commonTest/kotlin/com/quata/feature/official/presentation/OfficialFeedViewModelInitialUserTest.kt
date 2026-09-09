package com.quata.feature.official.presentation

import com.quata.core.common.AppDispatchers
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfficialFeedViewModelInitialUserTest {
    @Test
    fun missingFocusedPostFinishesAndCanBeRetriedWithoutAffectingAnotherTarget() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var available = false
        val repository = object : OfficialRepository by FailingCurrentUserRepository {
            override suspend fun getOfficialPost(postId: String): Result<OfficialPostItem?> = Result.success(
                if (available && postId == "target") OfficialPostItem(
                    id = postId, author = User(id = "author", email = "author@example.test", displayName = "Author"),
                    title = "Target", summary = "Summary", contentHtml = "", contentPlain = "Body",
                    createdAt = "2026-09-09T00:00:00Z",
                ) else null
            )
        }
        val viewModel = OfficialFeedViewModel(repository, AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher))
        try {
            viewModel.onEvent(OfficialFeedUiEvent.EnsurePostLoaded("target"))
            viewModel.onEvent(OfficialFeedUiEvent.EnsurePostLoaded("other"))
            advanceUntilIdle()
            assertEquals(OfficialFocusedPostLoad.NotFound, viewModel.uiState.value.focusedPostLoads["target"])
            assertEquals(OfficialFocusedPostLoad.NotFound, viewModel.uiState.value.focusedPostLoads["other"])
            available = true
            viewModel.onEvent(OfficialFeedUiEvent.EnsurePostLoaded("target"))
            advanceUntilIdle()
            assertEquals(OfficialFocusedPostLoad.Loaded, viewModel.uiState.value.focusedPostLoads["target"])
            assertEquals(OfficialFocusedPostLoad.NotFound, viewModel.uiState.value.focusedPostLoads["other"])
            assertTrue(viewModel.uiState.value.posts.any { it.id == "target" })
        } finally { viewModel.close() }
    }

    @Test
    fun failedFocusedLookupDoesNotBecomeNotFoundAndRetryCanResolveMissing() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var offline = true
        val repository = object : OfficialRepository by FailingCurrentUserRepository {
            override suspend fun getOfficialPost(postId: String): Result<OfficialPostItem?> =
                if (offline) Result.failure(IllegalStateException("offline")) else Result.success(null)
        }
        val viewModel = OfficialFeedViewModel(repository, AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher))
        try {
            viewModel.onEvent(OfficialFeedUiEvent.EnsurePostLoaded("target"))
            advanceUntilIdle()
            assertEquals(OfficialFocusedPostLoad.Failed, viewModel.uiState.value.focusedPostLoads["target"])
            offline = false
            viewModel.onEvent(OfficialFeedUiEvent.EnsurePostLoaded("target"))
            advanceUntilIdle()
            assertEquals(OfficialFocusedPostLoad.NotFound, viewModel.uiState.value.focusedPostLoads["target"])
        } finally { viewModel.close() }
    }

    @Test
    fun refreshFailureDoesNotGrantOfficialCapabilityFromInitialUser() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val official = User(
            id = "official-user",
            email = "official@example.test",
            displayName = "Official",
            isOfficial = true,
        )
        val viewModel = OfficialFeedViewModel(
            repository = FailingCurrentUserRepository,
            dispatchers = AppDispatchers(default = dispatcher, main = dispatcher, io = dispatcher),
            initialCurrentUser = official,
        )
        viewModel.refreshCurrentUser()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.currentUser?.isOfficial == true)
    }

    private object FailingCurrentUserRepository : OfficialRepository {
        override fun observeOfficialFeed(): Flow<Result<List<OfficialPostItem>>> = flowOf(Result.success(emptyList()))
        override suspend fun getOfficialFeed(): Result<List<OfficialPostItem>> = Result.success(emptyList())
        override suspend fun refreshOfficialFeed(): Result<List<OfficialPostItem>> = Result.success(emptyList())
        override suspend fun loadOlderOfficialFeedPage(beforePublishedAt: String?, limit: Int): Result<List<OfficialPostItem>> = Result.success(emptyList())
        override suspend fun getOfficialPost(postId: String): Result<OfficialPostItem?> = Result.success(null)
        override suspend fun refreshCurrentUser(): Result<User?> = Result.failure(IllegalStateException("offline"))
        override suspend fun createPost(draft: OfficialPostDraft): Result<OfficialPostItem?> = Result.success(null)
        override suspend fun deletePost(postId: String): Result<Unit> = Result.success(Unit)
        override suspend fun toggleLike(postId: String): Result<OfficialPostItem?> = Result.success(null)
        override suspend fun addComment(postId: String, comment: PostComment): Result<OfficialPostItem?> = Result.success(null)
        override suspend fun reportComment(commentId: String): Result<Unit> = Result.success(Unit)
    }
}
