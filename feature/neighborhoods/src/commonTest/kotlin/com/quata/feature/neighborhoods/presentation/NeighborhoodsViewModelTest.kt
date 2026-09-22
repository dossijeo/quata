package com.quata.feature.neighborhoods.presentation

import com.quata.core.common.AppDispatchers
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.feature.neighborhoods.domain.FollowUserResult
import com.quata.feature.neighborhoods.domain.NeighborhoodCommunity
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NeighborhoodsViewModelTest {
    @Test
    fun `directory load failure leaves a stable error state`() = runTest {
        val repository = FakeNeighborhoodRepository().apply {
            communitiesFlow = flow { throw IllegalStateException("offline") }
        }
        val model = model(repository)

        model.startObservingCommunities()
        advanceUntilIdle()

        assertFalse(model.uiState.value.isLoading)
        assertTrue(model.uiState.value.communities.isEmpty())
        assertEquals("offline", model.uiState.value.error)
        model.close()
    }

    @Test
    fun `nested public profiles return through the common stack`() = runTest {
        val repository = FakeNeighborhoodRepository()
        val model = model(repository)

        model.openUserProfile("a")
        advanceUntilIdle()
        model.openUserProfile("b")
        advanceUntilIdle()

        assertFalse(model.closeUserProfile())
        advanceUntilIdle()
        assertEquals("a", model.uiState.value.selectedProfile?.user?.id)
        assertTrue(model.closeUserProfile())
        assertEquals(null, model.uiState.value.selectedProfile)
        model.close()
    }

    @Test
    fun `follow is optimistic and rolls back on backend failure`() = runTest {
        val repository = FakeNeighborhoodRepository()
        val model = model(repository)
        model.openUserProfile("a")
        advanceUntilIdle()
        repository.followResult = CompletableDeferred()

        model.toggleFollowUser("a")
        runCurrent()
        assertTrue(model.uiState.value.selectedProfile?.user?.isFollowing == true)

        repository.followResult.complete(Result.failure(IllegalStateException("denied")))
        advanceUntilIdle()
        assertFalse(model.uiState.value.selectedProfile?.user?.isFollowing == true)
        assertEquals("denied", model.uiState.value.error)

        repository.followResult = CompletableDeferred(Result.success(FollowUserResult("a", true, user("me"))))
        model.toggleFollowUser("a")
        advanceUntilIdle()

        assertTrue(model.uiState.value.selectedProfile?.user?.isFollowing == true)
        assertEquals(null, model.uiState.value.error)
        assertEquals(listOf("a", "a"), repository.followCalls)
        model.close()
    }

    @Test
    fun `follow requests for different users are serialized while one is active`() = runTest {
        val repository = FakeNeighborhoodRepository().apply {
            followResult = CompletableDeferred()
        }
        val model = model(repository)
        model.openUserProfile("a")
        advanceUntilIdle()

        model.toggleFollowUser("a")
        model.toggleFollowUser("b")
        assertEquals("a", model.uiState.value.followingUserId)
        runCurrent()

        assertEquals(listOf("a"), repository.followCalls)
        assertEquals("a", model.uiState.value.followingUserId)
        assertTrue(model.uiState.value.selectedProfile?.user?.isFollowing == true)

        repository.followResult.complete(Result.success(FollowUserResult("a", true, user("me"))))
        advanceUntilIdle()
        repository.followResult = CompletableDeferred(Result.success(FollowUserResult("b", true, user("me"))))
        model.toggleFollowUser("b")
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), repository.followCalls)
        assertEquals(null, model.uiState.value.followingUserId)
        model.close()
    }

    @Test
    fun `follow failure rolls back its target without restoring an older profile`() = runTest {
        val repository = FakeNeighborhoodRepository().apply {
            followResult = CompletableDeferred()
        }
        val model = model(repository)
        model.openUserProfile("a")
        advanceUntilIdle()

        model.toggleFollowUser("a")
        model.openUserProfile("b")
        runCurrent()
        assertEquals("b", model.uiState.value.selectedProfile?.user?.id)

        repository.followResult.complete(Result.failure(IllegalStateException("denied")))
        advanceUntilIdle()

        assertEquals("b", model.uiState.value.selectedProfile?.user?.id)
        assertEquals("denied", model.uiState.value.error)
        assertEquals(null, model.uiState.value.followingUserId)
        model.close()
    }

    @Test
    fun `profile navigation during suspended follow cache is preserved`() = runTest {
        val cacheGate = CompletableDeferred<Unit>()
        val repository = FakeNeighborhoodRepository()
        val model = model(repository)
        model.openUserProfile("a")
        advanceUntilIdle()
        repository.cacheGate = cacheGate

        model.toggleFollowUser("a")
        runCurrent()
        assertTrue(model.uiState.value.selectedProfile?.user?.isFollowing == true)
        assertEquals(null, model.uiState.value.followingUserId)

        model.openUserProfile("b")
        runCurrent()
        assertEquals("b", model.uiState.value.selectedProfile?.user?.id)

        cacheGate.complete(Unit)
        advanceUntilIdle()

        assertEquals("b", model.uiState.value.selectedProfile?.user?.id)
        model.close()
    }

    @Test
    fun `community chat opening navigates once and clears progress`() = runTest {
        val repository = FakeNeighborhoodRepository()
        repository.communityChatResult = CompletableDeferred()
        val model = model(repository)
        val opened = mutableListOf<String>()

        model.openChat("Bata") { opened += it }
        runCurrent()

        assertEquals("Bata", model.uiState.value.openingChatNeighborhood)
        assertEquals(null, model.uiState.value.chatErrorNeighborhood)

        repository.communityChatResult.complete(Result.success("sb:community-1"))
        advanceUntilIdle()

        assertEquals(listOf("sb:community-1"), opened)
        assertEquals(null, model.uiState.value.openingChatNeighborhood)
        assertEquals(null, model.uiState.value.chatErrorNeighborhood)
        assertEquals(1, repository.openCommunityChatCalls)
        model.close()
    }

    @Test
    fun `community chat opening ignores duplicate taps while request is active`() = runTest {
        val repository = FakeNeighborhoodRepository()
        repository.communityChatResult = CompletableDeferred()
        val model = model(repository)

        model.openChat("Bata") {}
        model.openChat("Bata") {}
        runCurrent()

        assertEquals(1, repository.openCommunityChatCalls)
        repository.communityChatResult.complete(Result.success("sb:community-1"))
        advanceUntilIdle()
        model.close()
    }

    @Test
    fun `community chat failure marks only the failed community`() = runTest {
        val repository = FakeNeighborhoodRepository()
        repository.communityChatResult = CompletableDeferred(Result.failure(IllegalStateException("wall_missing")))
        val model = model(repository)

        model.openChat("Bata") {}
        advanceUntilIdle()

        assertEquals(null, model.uiState.value.openingChatNeighborhood)
        assertEquals("Bata", model.uiState.value.chatErrorNeighborhood)
        assertEquals("wall_missing", model.uiState.value.error)
        model.close()
    }

    @Test
    fun `private chat opening ignores duplicate taps while request is active`() = runTest {
        val repository = FakeNeighborhoodRepository()
        repository.privateChatResult = CompletableDeferred()
        val model = model(repository)

        model.openPrivateChat("a") {}
        model.openPrivateChat("a") {}
        runCurrent()

        assertEquals(1, repository.openPrivateChatCalls)
        repository.privateChatResult.complete(Result.success("sb:private-1"))
        advanceUntilIdle()
        assertEquals(null, model.uiState.value.openingPrivateChatUserId)
        model.close()
    }

    @Test
    fun `follow success updates selected profile counters and follower list`() = runTest {
        val repository = FakeNeighborhoodRepository()
        val model = model(repository)
        model.openUserProfile("a")
        advanceUntilIdle()

        model.toggleFollowUser("a")
        advanceUntilIdle()

        val profile = model.uiState.value.selectedProfile
        assertTrue(profile?.user?.isFollowing == true)
        assertEquals(1, profile?.user?.followersCount)
        assertEquals(listOf("me"), profile?.followers?.map { it.id })
        assertEquals(null, model.uiState.value.followingUserId)
        model.close()
    }

    @Test
    fun `unfollow success restores selected profile counters and follower list`() = runTest {
        val repository = FakeNeighborhoodRepository()
        repository.profileOverride = profile(
            "a",
            user = user("a").copy(isFollowing = true, followersCount = 1),
            followers = listOf(user("me")),
        )
        repository.followResult = CompletableDeferred(Result.success(FollowUserResult("a", false, user("me"))))
        val model = model(repository)
        model.openUserProfile("a")
        advanceUntilIdle()

        model.toggleFollowUser("a")
        advanceUntilIdle()

        val profile = model.uiState.value.selectedProfile
        assertFalse(profile?.user?.isFollowing == true)
        assertEquals(0, profile?.user?.followersCount)
        assertTrue(profile?.followers.orEmpty().isEmpty())
        assertEquals(null, model.uiState.value.followingUserId)
        model.close()
    }

    @Test
    fun `comment is optimistic and rolls back on backend failure`() = runTest {
        val repository = FakeNeighborhoodRepository()
        val model = model(repository)
        model.openUserProfile("a")
        advanceUntilIdle()
        repository.commentResult = CompletableDeferred()
        val comment = PostComment("c", "You", "hello", "Now")

        model.addProfileComment("post-a", comment)
        runCurrent()
        assertEquals(listOf(comment), model.uiState.value.selectedProfile?.posts?.single()?.comments)

        repository.commentResult.complete(Result.failure(IllegalStateException("denied")))
        advanceUntilIdle()
        assertTrue(model.uiState.value.selectedProfile?.posts?.single()?.comments.orEmpty().isEmpty())
        assertEquals("denied", model.uiState.value.error)
        model.close()
    }

    @Test
    fun `consecutive profile comments keep optimistic state until all requests finish`() = runTest {
        val repository = FakeNeighborhoodRepository()
        val model = model(repository)
        model.openUserProfile("a")
        advanceUntilIdle()
        val firstResult = CompletableDeferred<Result<Post?>>()
        val secondResult = CompletableDeferred<Result<Post?>>()
        repository.commentResults += firstResult
        repository.commentResults += secondResult
        val first = PostComment("c1", "You", "first", "Now")
        val second = PostComment("c2", "You", "second", "Now")

        model.addProfileComment("post-a", first)
        model.addProfileComment("post-a", second)
        runCurrent()

        assertEquals(listOf(first, second), model.uiState.value.selectedProfile?.posts?.single()?.comments)
        assertEquals("post-a", model.uiState.value.commentingPostId)

        firstResult.complete(Result.success(Post("post-a", User("a", "", "a"), "post", comments = listOf(first), createdAt = "now")))
        advanceUntilIdle()

        assertEquals(listOf(first, second), model.uiState.value.selectedProfile?.posts?.single()?.comments)
        assertEquals("post-a", model.uiState.value.commentingPostId)

        secondResult.complete(Result.success(Post("post-a", User("a", "", "a"), "post", comments = listOf(first, second), createdAt = "now")))
        advanceUntilIdle()

        assertEquals(listOf(first, second), model.uiState.value.selectedProfile?.posts?.single()?.comments)
        assertEquals(null, model.uiState.value.commentingPostId)
        model.close()
    }

    @Test
    fun `profile post like is optimistic and rolls back on backend failure`() = runTest {
        val repository = FakeNeighborhoodRepository()
        val model = model(repository)
        model.openUserProfile("a")
        advanceUntilIdle()
        repository.likeResult = CompletableDeferred()

        model.toggleProfilePostLike("post-a")
        runCurrent()
        assertTrue(model.uiState.value.selectedProfile?.posts?.single()?.isLikedByCurrentUser == true)
        assertEquals(1, model.uiState.value.selectedProfile?.posts?.single()?.likesCount)
        assertEquals("post-a", model.uiState.value.likingPostId)

        repository.likeResult.complete(Result.failure(IllegalStateException("denied")))
        advanceUntilIdle()
        assertFalse(model.uiState.value.selectedProfile?.posts?.single()?.isLikedByCurrentUser == true)
        assertEquals(0, model.uiState.value.selectedProfile?.posts?.single()?.likesCount)
        assertEquals(null, model.uiState.value.likingPostId)
        assertEquals("denied", model.uiState.value.error)
        model.close()
    }

    @Test
    fun `profile block is optimistic and restores the exact profile on backend failure`() = runTest {
        val repository = FakeNeighborhoodRepository()
        val model = model(repository)
        model.openUserProfile("a")
        advanceUntilIdle()
        val before = model.uiState.value.selectedProfile
        repository.blockResult = CompletableDeferred()

        model.setProfileBlocked("a", true)
        runCurrent()

        assertTrue(model.uiState.value.selectedProfile?.isBlockedByCurrentUser == true)
        assertEquals("a", model.uiState.value.profileSafetyUpdatingUserId)

        repository.blockResult.complete(Result.failure(IllegalStateException("denied")))
        advanceUntilIdle()

        assertEquals(before, model.uiState.value.selectedProfile)
        assertEquals(null, model.uiState.value.profileSafetyUpdatingUserId)
        assertEquals("denied", model.uiState.value.error)
        model.close()
    }

    @Test
    fun `profile safety requests are serialized before their coroutines start`() = runTest {
        val repository = FakeNeighborhoodRepository().apply {
            reportResult = CompletableDeferred()
        }
        val model = model(repository)
        model.openUserProfile("a")
        advanceUntilIdle()

        model.reportProfile("a")
        model.setProfileBlocked("a", true)

        assertEquals("a", model.uiState.value.profileSafetyUpdatingUserId)
        assertFalse(model.uiState.value.selectedProfile?.isBlockedByCurrentUser == true)
        runCurrent()
        assertEquals(listOf("a"), repository.reportCalls)
        assertTrue(repository.blockCalls.isEmpty())

        repository.reportResult.complete(Result.success(Unit))
        advanceUntilIdle()
        model.setProfileBlocked("a", true)
        advanceUntilIdle()

        assertEquals(listOf("a" to true), repository.blockCalls)
        assertTrue(model.uiState.value.selectedProfile?.isBlockedByCurrentUser == true)
        assertEquals(null, model.uiState.value.profileSafetyUpdatingUserId)
        model.close()
    }

    @Test
    fun `profile block success stays bound to its target after navigation`() = runTest {
        val repository = FakeNeighborhoodRepository().apply {
            blockResult = CompletableDeferred()
        }
        val model = model(repository)
        model.openUserProfile("a")
        advanceUntilIdle()

        model.setProfileBlocked("a", true)
        model.openUserProfile("b")
        runCurrent()
        assertEquals("b", model.uiState.value.selectedProfile?.user?.id)

        repository.blockResult.complete(Result.success(true))
        advanceUntilIdle()

        assertEquals("b", model.uiState.value.selectedProfile?.user?.id)
        assertFalse(model.uiState.value.selectedProfile?.isBlockedByCurrentUser == true)
        assertTrue(repository.cachedProfiles.last().isBlockedByCurrentUser)
        assertEquals("a", repository.cachedProfiles.last().user.id)
        model.close()
    }

    @Test
    fun `profile block failure does not restore its target over a newer profile`() = runTest {
        val repository = FakeNeighborhoodRepository().apply {
            blockResult = CompletableDeferred()
        }
        val model = model(repository)
        model.openUserProfile("a")
        advanceUntilIdle()

        model.setProfileBlocked("a", true)
        model.openUserProfile("b")
        runCurrent()
        assertEquals("b", model.uiState.value.selectedProfile?.user?.id)

        repository.blockResult.complete(Result.failure(IllegalStateException("denied")))
        advanceUntilIdle()

        assertEquals("b", model.uiState.value.selectedProfile?.user?.id)
        assertFalse(model.uiState.value.selectedProfile?.isBlockedByCurrentUser == true)
        assertEquals("denied", model.uiState.value.error)
        model.close()
    }

    @Test
    fun `profile navigation during suspended block cache is preserved`() = runTest {
        val cacheGate = CompletableDeferred<Unit>()
        val repository = FakeNeighborhoodRepository()
        val model = model(repository)
        model.openUserProfile("a")
        advanceUntilIdle()
        repository.cacheGate = cacheGate

        model.setProfileBlocked("a", true)
        runCurrent()
        assertTrue(model.uiState.value.selectedProfile?.isBlockedByCurrentUser == true)
        assertEquals(null, model.uiState.value.profileSafetyUpdatingUserId)

        model.openUserProfile("b")
        runCurrent()
        assertEquals("b", model.uiState.value.selectedProfile?.user?.id)

        cacheGate.complete(Unit)
        advanceUntilIdle()

        assertEquals("b", model.uiState.value.selectedProfile?.user?.id)
        assertFalse(model.uiState.value.selectedProfile?.isBlockedByCurrentUser == true)
        model.close()
    }

    @Test
    fun `role updates are serialized before their coroutines start`() = runTest {
        val repository = FakeNeighborhoodRepository().apply {
            roleResult = CompletableDeferred()
        }
        val model = model(repository)
        model.openUserProfile("a")
        advanceUntilIdle()

        model.setUserRoles("a", isAdmin = true, isOfficial = false)
        model.setUserRoles("b", isAdmin = false, isOfficial = true)

        assertEquals("a", model.uiState.value.roleUpdatingUserId)
        runCurrent()
        assertEquals(listOf(Triple("a", true, false)), repository.roleCalls)

        repository.roleResult.complete(Result.success(user("a").copy(isAdmin = true)))
        advanceUntilIdle()
        repository.roleResult = CompletableDeferred(Result.success(user("b").copy(isOfficial = true)))
        model.setUserRoles("b", isAdmin = false, isOfficial = true)
        advanceUntilIdle()

        assertEquals(
            listOf(Triple("a", true, false), Triple("b", false, true)),
            repository.roleCalls,
        )
        assertEquals(null, model.uiState.value.roleUpdatingUserId)
        model.close()
    }

    private fun kotlinx.coroutines.test.TestScope.model(repository: FakeNeighborhoodRepository): NeighborhoodsViewModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return NeighborhoodsViewModel(repository, AppDispatchers(dispatcher, dispatcher, dispatcher))
    }
}

private class FakeNeighborhoodRepository : NeighborhoodRepository {
    var followResult = CompletableDeferred(Result.success(FollowUserResult("a", true, user("me"))))
    val followCalls = mutableListOf<String>()
    var communityChatResult = CompletableDeferred(Result.success("community"))
    var openCommunityChatCalls = 0
    var privateChatResult = CompletableDeferred(Result.success("private"))
    var openPrivateChatCalls = 0
    var commentResult = CompletableDeferred<Result<Post?>>(Result.success(null))
    val commentResults = mutableListOf<CompletableDeferred<Result<Post?>>>()
    var likeResult = CompletableDeferred<Result<Post?>>(Result.success(null))
    var reportResult = CompletableDeferred(Result.success(Unit))
    val reportCalls = mutableListOf<String>()
    var blockResult = CompletableDeferred(Result.success(true))
    val blockCalls = mutableListOf<Pair<String, Boolean>>()
    var roleResult = CompletableDeferred(Result.success(user("role")))
    val roleCalls = mutableListOf<Triple<String, Boolean, Boolean>>()
    val cachedProfiles = mutableListOf<CommunityUserProfile>()
    var cacheGate: CompletableDeferred<Unit>? = null
    var profileOverride: CommunityUserProfile? = null
    var communitiesFlow: Flow<List<NeighborhoodCommunity>> = flowOf(emptyList())

    override fun observeCommunities(): Flow<List<NeighborhoodCommunity>> = communitiesFlow
    override suspend fun openNeighborhoodChat(neighborhood: String): Result<String> {
        openCommunityChatCalls += 1
        return communityChatResult.await()
    }
    override suspend fun toggleFollowUser(userId: String): Result<FollowUserResult> {
        followCalls += userId
        return followResult.await()
    }
    override suspend fun toggleProfilePostLike(postId: String) = likeResult.await()
    override suspend fun addProfileComment(postId: String, comment: PostComment): Result<Post?> {
        val queued = commentResults.firstOrNull()
        if (queued != null) {
            commentResults.removeAt(0)
            return queued.await()
        }
        return commentResult.await()
    }
    override suspend fun reportPost(postId: String) = Result.success(Unit)
    override suspend fun reportProfile(userId: String): Result<Unit> {
        reportCalls += userId
        return reportResult.await()
    }
    override suspend fun setProfileBlocked(userId: String, blocked: Boolean): Result<Boolean> {
        blockCalls += userId to blocked
        return blockResult.await()
    }
    override suspend fun openPrivateChat(userId: String): Result<String> {
        openPrivateChatCalls += 1
        return privateChatResult.await()
    }
    override suspend fun isCurrentUserAdmin() = false
    override suspend fun setUserRoles(userId: String, isAdmin: Boolean, isOfficial: Boolean): Result<NeighborhoodUser> {
        roleCalls += Triple(userId, isAdmin, isOfficial)
        return roleResult.await()
    }
    override suspend fun getCachedUserProfile(userId: String, maxAgeMillis: Long?) = null
    override suspend fun cacheUserProfile(profile: CommunityUserProfile) {
        cachedProfiles += profile
        cacheGate?.await()
    }
    override fun observeUserProfile(userId: String): Flow<Result<CommunityUserProfile>> = flow { emit(getUserProfile(userId)) }
    override suspend fun getUserProfile(userId: String) = Result.success(profileOverride ?: profile(userId))

}

private fun user(id: String) = NeighborhoodUser(id, id, "", "Barrio")

private fun profile(
    id: String,
    user: NeighborhoodUser = user(id),
    followers: List<NeighborhoodUser> = emptyList(),
    following: List<NeighborhoodUser> = emptyList(),
): CommunityUserProfile = CommunityUserProfile(
    user = user,
    posts = listOf(Post("post-$id", User(id, "", id), "post", createdAt = "now")),
    followers = followers,
    following = following,
)
