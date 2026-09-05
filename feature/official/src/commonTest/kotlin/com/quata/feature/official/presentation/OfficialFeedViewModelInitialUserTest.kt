package com.quata.feature.official.presentation

import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OfficialFeedViewModelInitialUserTest {
    @Test
    fun preservesInitialCurrentUserWhenRefreshFails() = runTest {
        val official = User(
            id = "official-user",
            email = "official@example.test",
            displayName = "Official",
            isOfficial = true,
        )
        val viewModel = OfficialFeedViewModel(
            repository = FailingCurrentUserRepository,
            initialCurrentUser = official,
        )

        assertEquals(official, viewModel.uiState.value.currentUser)
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
