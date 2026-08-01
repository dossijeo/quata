@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.quata.feature.neighborhoods.presentation

import com.quata.core.common.AppDispatchers
import com.quata.feature.neighborhoods.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class NeighborhoodsViewModelLifecycleTest {
    @Test fun `collector reports error then retries after lifecycle restart`() = runTest {
        val repo = FakeNeighborhoodRepository(failFirstRead = true)
        val vm = NeighborhoodsViewModel(repo, AppDispatchers(StandardTestDispatcher(testScheduler), StandardTestDispatcher(testScheduler), StandardTestDispatcher(testScheduler)))
        vm.startObservingCommunities(); runCurrent()
        assertNotNull(vm.uiState.value.error)
        vm.retryCommunities(); runCurrent()
        assertEquals("Centro", vm.uiState.value.communities.single().name)
        vm.close()
    }

    @Test fun `community and private chat callbacks expose repository ids`() = runTest {
        val repo = FakeNeighborhoodRepository()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = NeighborhoodsViewModel(repo, AppDispatchers(dispatcher, dispatcher, dispatcher))
        var community: String? = null; var privateChat: String? = null
        vm.openChat("Centro") { community = it }; vm.openPrivateChat("peer") { privateChat = it }; runCurrent()
        assertEquals("wall:real", community); assertEquals("private:real", privateChat)
        vm.close()
    }

    @Test fun `follow action delegates to real repository result`() = runTest {
        val repo = FakeNeighborhoodRepository()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = NeighborhoodsViewModel(repo, AppDispatchers(dispatcher, dispatcher, dispatcher))
        vm.toggleFollowUser("peer"); runCurrent()
        assertEquals("peer", repo.followedUserId)
        vm.close()
    }
}

private class FakeNeighborhoodRepository(private val failFirstRead: Boolean = false) : NeighborhoodRepository {
    private var reads = 0
    var followedUserId: String? = null
    override fun observeCommunities(): Flow<List<NeighborhoodCommunity>> = flow { reads++; if (failFirstRead && reads == 1) error("offline"); emit(listOf(NeighborhoodCommunity("Centro", emptyList(), "wall:real", null, 1L, 1))) }
    override suspend fun openNeighborhoodChat(neighborhood: String) = Result.success("wall:real")
    override suspend fun toggleFollowUser(userId: String): Result<FollowUserResult> { followedUserId = userId; return Result.success(FollowUserResult(userId, true, NeighborhoodUser("me", "Me", "", "Centro"))) }
    override suspend fun reportPost(postId: String) = Result.success(Unit)
    override suspend fun openPrivateChat(userId: String) = Result.success("private:real")
    override suspend fun isCurrentUserAdmin() = false
    override suspend fun setUserRoles(userId: String, isAdmin: Boolean, isOfficial: Boolean) = Result.failure<NeighborhoodUser>(UnsupportedOperationException())
    override suspend fun getCachedUserProfile(userId: String, maxAgeMillis: Long?) = null
    override suspend fun cacheUserProfile(profile: CommunityUserProfile) = Unit
    override fun observeUserProfile(userId: String) = flowOf(Result.failure<CommunityUserProfile>(UnsupportedOperationException()))
    override suspend fun getUserProfile(userId: String) = Result.failure<CommunityUserProfile>(UnsupportedOperationException())
}
