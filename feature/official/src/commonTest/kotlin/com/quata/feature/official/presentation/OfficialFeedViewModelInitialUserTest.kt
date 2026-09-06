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

class OfficialFeedViewModelInitialUserTest {
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
